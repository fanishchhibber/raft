package distill.log_kvs_timeouts;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.*;

import static distill.log_kvs_timeouts.Actions.*;
import static distill.log_kvs_timeouts.Events.*;
import static distill.log_kvs_timeouts.Raft.mkMsg;
import static org.junit.jupiter.api.Assertions.*;

public class RaftTest {

    /**
     * At start servers should set alarms
     */
    @Test
    void testStartup() {
        int clusterSize = 5;
        ArrayList<String> members = new ArrayList<>(clusterSize);
        for (int i = 1; i <= clusterSize; i++) {
            members.add("S" + i);
        }
        var leader = new Raft(members.get(0), members, true);
        Actions actions = leader.start();
        var alarms = extractSetAlarm(actions);
        // Expect a heartbeat alarm to be set per follower
        assertEquals(alarms.size(), clusterSize - 1);
    }

    /**
     * A higher term in an incoming message should downgrade leader to follower,
     * and upgrade raft's term to the message's term
     */
    @Test
    void testDowngrade() {
        var l = createLeader(3);
        var msg = mkMsg("from", "S3",
                "to", "S1",
                "type", APPEND_REQ,
                "index", 3,
                "term", 5,
                "log_length", 3,
                "prev_log_term", 5,
                "num_committed", 0,
                "entries", new JSONArray()
        );
        l.processMsg(msg);
        assertSame(l.status, Status.FOLLOWER);
        assertEquals(l.term, 5);
    }

    @Test
    void testIgnoreLowerTerm() {
        var l = createLeader(3);

        var msg = mkMsg("from", "S3",
                "to", "S1",
                "type", APPEND_REQ,
                "index", 3,
                "term", 1,
                "log_length", 3,
                "prev_log_term", 1,
                "num_committed", 0,
                "entries", new JSONArray()
        );
        // Check leader remains leader and term is unchanged.
        int oldTerm = l.term;
        assertSame(l.status, Status.LEADER);
        assertEquals(l.term, oldTerm);
        // Ensure no actions are produced. The message should be dropped
        Actions actions = l.processMsg(msg);
        assertEquals(actions.size(), 0);
    }


    @Test
    void testBecomeFollower() {
        var l = createLeader(5);
        Actions actions = l.becomeFollower();
        // at the very least  there should be 4 heartbeat cancellations and
        // one SetAlarm("ELECTION"). Optional: CancelAlarm("ELECTION")
        // If we collect them as a set, there should be exactly 5 alarm actions.
        Set<String> names = new HashSet<>();
        actions.todos.forEach(act -> {
            if (act instanceof SetAlarm sa) {
                names.add(sa.name());
            } else if (act instanceof CancelAlarm ca) {
                names.add(ca.name());
            }
        });
        assertEquals(names.size(),  5, actions.toString());

    }
    /**
     * Expect to see a single send of an empty AppendReq message.
     */
    @Test
    void testHeartbeatExpired() {
        var msg = mkMsg("type", TIMEOUT, "name", "S2");
        var l = createLeader();
        var actions = l.processMsg(msg);
        var sends = extractSends(actions);
        assertEquals(sends.size(), 1);
        msg = sends.get(0);
        assertEquals (msg.getJSONArray("entries").length(), 0);
        assertEquals (msg.getString("to"), "S2");
    }

    /**
     * This is a comprehensive test of log replication. It starts with a follower
     * with a different log than the leader.
     * We take the message from the Send action of one server and give it to the
     * other's processMsg, in a loop, which is a cycle of AppendRequests and responses.
     * Once the logs have equalized, there should be no more messages to send from
     * leader to follower (in this test, that is; otherwise there will be regular heartbeats)
     * </b>
     * The code below tests both naks and acks; The follower forces the leader to keep backing
     * up until the beginning of the log and then signals success.
     */
    void replicate(Raft leader, Raft follower) {
        // Prime the loop with a heartbeat timer expired message, which
        // will force the leader to send an empty Append
        var msg = mkMsg("type", TIMEOUT, "name", "S2");
        for (int i = 0; i < 6; i++) {
            // msg is either an output from the previous iteration or
            // an initial heartbeat timer expiry for follower S2.
            var actions = leader.processMsg(msg);
            msg = extractSend(actions, "S2");
            if (msg == null) {
                break; // Nothing more to send to S2
            }
            // send msg to follower
            actions = follower.processMsg(msg);
            msg = extractSend(actions, "S1");
            //System.out.println("S2 -> S1: " + msg);
            //System.out.println("FOLLOWER LOG " + f.log);
        }
        assertNull(msg, "Expected no more messages once logs have equalized");
        // Check if the logs are identical. JSONArray doesn't implement equals() alas
        assertJsonEquals(leader.log, follower.log);
    }

    @Test
    void testReplication() {
        var l = createLeader(3);
        int[] terms = {1,2,2};
        l.log = mkSampleLog(terms);
        // Keep the term higher than the last term in the log and verify that although
        // the logs have been equalized, l.numCommitted and l.numApplied are still 0
        l.term = 3;

        var f = createFollower(3); // f.log has the default log [1,1,1]
        f.log = new JSONArray();
        f.myId = "S2";

        replicate(l,f);
        assertEquals(0, l.numCommitted);
        assertEquals(0, l.numApplied);
    }

    @Test
    void testCommit() {
        var l = createLeader(3);
        int[] terms = {1,2,3};
        l.log = mkSampleLog(terms);
        l.term = 3;

        var f = createFollower(3); // f.log has the default log [1,1,1]
        f.log = new JSONArray();
        f.myId = "S2";

        replicate(l,f);
        assertEquals(3, l.numCommitted);
        assertEquals(3, l.numApplied);
        // Check KVStore
        assertEquals(30, l.kv.get("a"));
    }


    // ----------------------------------------------------------
    // Setup and convenience methods

    Raft createLeader(int clusterSize) {
        ArrayList<String> members = new ArrayList<>(clusterSize);
        for (int i = 1; i <= clusterSize; i++) {
            members.add("S" + i);
        }
        Raft raft = new Raft(members.get(0), members, true);
        int[] terms = {1, 1, 2};
        raft.term = 2;
        raft.log = mkSampleLog(terms);
        raft.pendingResponses = mkPendingResponses(terms.length);
        raft.start();
        return raft;
    }

    private Map<String, JSONObject> mkPendingResponses(int length) {
        var pending = new HashMap<String, JSONObject>();
        for (int i = 0; i < length; i++) {
            var reqid = "" + i;
            var jo = mkMsg(
                    "from", "cl1",
                    "to", "S1",
                    "type", CMD_REQ,
                    "reqid", reqid,
                    "cmd", "W",
                    "key", "a",
                    "value", i
            );
            pending.put(reqid, jo);
        }
        return pending;
    }

    Raft createFollower(int clusterSize) {
        var raft = createLeader(clusterSize);
        raft.becomeFollower();
        return raft;
    }

    Raft createLeader() {
        return createLeader(3);
    }

    JSONArray mkSampleLog(int[] terms) {
        var log = new JSONArray(terms.length);
        for (int i = 0; i < terms.length; i++) {
            var term = terms[i];
            var cl_reqid = "" + i;
            var entry = mkMsg(
                    "term", term,
                    "cl_reqid", cl_reqid,
                    "cmd", "W", "key", "a", "value", (i+1) * 10
            );
            log.put(entry);
        }
        return log;
    }

    List<JSONObject> extractSends(Actions actions) {
        var msgs = new ArrayList<JSONObject>();
        for (var action : actions.todos) {
            if (action instanceof Send sendAction) {
                msgs.add(sendAction.msg());
            }
        }
        return msgs;
    }

    JSONObject extractSend(Actions actions, String to) {
        var msgs = extractSends(actions);
        for (var msg: msgs) {
            if (msg.getString("to").equals(to)) {
                return msg;
            }
        }
        return null;
    }

    List<String> extractSetAlarm(Actions actions) {
        var timerNames = new ArrayList<String>();
        for (var action : actions.todos) {
            if (action instanceof SetAlarm alarmAction) {
                timerNames.add(alarmAction.name());
            }
        }
        return timerNames;
    }

    void assertJsonEquals(Object a, Object b) {
        assertTrue( (a == null & b == null) || (a != null & b != null),
                "one of them is null and the other isn't");
        if ((a instanceof JSONObject ajo) && (b instanceof JSONObject bjo)) {
            assertJsonEquals(ajo, bjo);
        } else if ((a instanceof JSONArray aja) && (b instanceof JSONArray bja)) {
            assertEquals(aja.length(), bja.length());
            for (int i = 0; i < aja.length(); i++) {
                assertJsonEquals(aja.get(i), bja.get(i));
            }
        } else {
            assertEquals(a, b);
        }
    }
    void assertJsonEquals(JSONObject a, JSONObject b) {
        assertEquals(a.length(), b.length());
        for (String key: a.keySet()) {
            assertTrue(b.has(key), "");
            assertJsonEquals(a.get(key), b.get(key));
        }
    }
}
