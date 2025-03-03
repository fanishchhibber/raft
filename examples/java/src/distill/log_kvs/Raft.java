package distill.log_kvs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

import static distill.log_kvs.Actions.*;
import static distill.log_kvs.Events.*;

public class Raft {
    static Action IGNORE_MSG = new Action() {
    };
    String myId;
    final List<String> siblings;
    final int quorumSize;
    final HashMap<String, Object> kv = new HashMap<>();
    JSONArray log = new JSONArray();
    int numCommitted = 0;
    int numApplied = 0;
    // kv:  key -> (log index,  value)
    Status status;
    Map<String, FollowerInfo> followers = new HashMap<>();
    int term;
    
    Map<String, JSONObject> pendingResponses = null;

    public Raft(String myId, List<String> siblings, boolean isLeader) {
        this.myId = myId;
        this.siblings = siblings;
        int clusterSize = siblings.size() + 1;
        quorumSize = clusterSize / 2 + 1;
        term = isLeader ? 1 : 0;
        status = isLeader ? Status.LEADER : Status.FOLLOWER;
    }

    Actions becomeLeader() {
        this.status = Status.LEADER;
        this.followers = new HashMap<>();
        for (String fol : siblings) {
            FollowerInfo fi = new FollowerInfo(fol, log.length(),
                    false);
            followers.put(fol, fi);
        }
        this.pendingResponses = new HashMap<>();
        return NO_ACTIONS; // There will be actions in a later exercise.
    }

    Actions becomeFollower() {
        status = Status.FOLLOWER;
        followers = null;
        return NO_ACTIONS; // There will be actions in a later exercise.
    }
    
    public Action mkReply(JSONObject msg, Object... extraKeyValues) {
        JSONObject reply = mkMsg(
                "from", myId,
                "to", msg.get("from"),
                "term", term);
        if (msg.has("reqid")) {
            reply.put("reqid", msg.get("reqid"));
        }
        String reqType = msg.getString("type");
        String responseType = Events.responseType.get(reqType);
        if (responseType == null) {
            throw new RuntimeException("msg type error: " + msg);
        }
        reply.put("type", responseType);

        for (int i = 0; i < extraKeyValues.length; i += 2) {
            var value = extraKeyValues[i + 1];
            reply.put((String) extraKeyValues[i], value);
        }

        return new Send(reply);
    }

    public boolean isLeader() {
        return status == Status.LEADER;
    }
    
    Actions onAppendReq(JSONObject msg) {
        assert !isLeader();
        int msgIndex = (int) msg.get("index");
        var msgEntries = (JSONArray) msg.get("entries");
        Action toSend = null;
        if (msgIndex > log.length()) {
            // Ask leader to back up
            toSend = mkReply(msg, "success", false, "index", log.length());
        } else {
            if (msgIndex == log.length()) {
                // Append msgEntries to log
                for (int i = 0; i < msgEntries.length(); i++) {
                    log.put(msgEntries.get(i));
                }
                toSend = mkReply(msg, "success", true, "index", log.length());
            } else { // msgIndex < log.length()
                // chop tail until msgIndex, then add msgEntries
                while (log.length() > msgIndex) {
                    log.remove(log.length() - 1);
                }
                for (int i = 0; i < msgEntries.length(); i++) {
                    log.put(msgEntries.get(i));
                }
                toSend = mkReply(msg, "success", true, "index", log.length());
            }
        }
        var actions = new Actions(toSend);

        if (!isLeader()) {
            numCommitted = (int) msg.get("num_committed");
            actions.add(onCommit());
        }
        return actions;
    }

    Actions onAppendResp(JSONObject msg) {
        assert isLeader();
        int msgIndex = msg.getInt("index");
        assert msgIndex <= log.length() : msgIndex;
        var fi = followers.get(msg.getString("from"));
        fi.logLength = msgIndex;
        fi.requestPending = false;
        var actions = new Actions(new SetAlarm(fi.follower_id)); // heartbeat timer reset
        if (updateNumCommitted()) {
            actions.add(onCommit());
        }
        return actions;
    }
    
    boolean updateNumCommitted() {
        // This method is called every time an append response comes, and
        // we check to see how much of the log has been committed at all.
        // if the number committed has changed, it returns true.

        // This is how to
        // Suppose the leader and followers' log lengths are as follows :
        // [10, 5, 4, 8, 10].
        //
        // The last follower has caught up, but tht others are lagging. To find out the number
        // of entries present in a majority of servers (that can be considered
        // committed), we sort the list (in descending order), pick a quorum-sized
        // slice from the top lengths, and use the last length (and smallest) element
        // in this list.
        // In this example, the sorted list is [10, 10, 8, 5, 4].
        // The top quorum-sized slice is [10, 10, 8]
        // 8 is the last and the smallest of this slice.
        // Regardless of which triple combination is chosen, we are
        // guaranteed that at least one server has 8 entries in its log.

        // Note that we can take a follower's log length seriously only if
        // fi.matched.

        assert isLeader();
        // Not implemented yet, just return false to fix compilation
        return false;
    }


    Action apply(int index, JSONObject entry) {
        var key = entry.getString("key");
        var cmd = entry.getString("cmd");
        Action action = NO_ACTIONS;

        JSONObject clientMsg = null;

        if (isLeader()) {
            String reqid = entry.getString("cl_reqid");
            clientMsg = pendingResponses.get(reqid);
            pendingResponses.remove(reqid);
        }

        if (cmd.equals("W")) {
            var value = entry.get("value");
            kv.put(key, value);
            if (clientMsg != null) {
                action = mkReply(clientMsg, "client_msg", clientMsg,
                        "index", index);
            }
        }
        return action;
    }
    Actions onCommit() {
        var actions = new Actions();
        // Not implemented yet, just return empty actions to fix compilation
        numApplied = numCommitted;
        return actions;
    }

    static JSONObject mkMsg(Object... kvpairs) {
        JSONObject jo = new JSONObject();
        if (kvpairs.length % 2 != 0) {
            throw new RuntimeException("kvpairs must be even numbered");
        }
        for (int i = 0; i < kvpairs.length; i += 2) {
            var value = kvpairs[i + 1];
            jo.put((String) kvpairs[i], value);
        }
        return jo;
    }

    Action mkAppendMsg(String to, int index) {
        assert isLeader();

        // Create a simple implementation to fix compilation errors
        JSONArray entries = new JSONArray();
        for (int i = index; i < log.length(); i++) {
            entries.put(log.get(i));
        }
        
        JSONObject message = mkMsg(
            "from", myId,
            "to", to,
            "type", APPEND_REQ,
            "term", term,
            "index", index,
            "num_committed", numCommitted,
            "entries", entries
        );
        
        return new Send(message);
    }

    Actions sendAppends() {
        if (!isLeader()) {
            return NO_ACTIONS;
        }
        var actions = new Actions();

        // Simple implementation to fix compilation errors
        for (FollowerInfo fi : followers.values()) {
            if (fi.logLength < log.length() && !fi.requestPending) {
                Action sendAction = mkAppendMsg(fi.follower_id, fi.logLength);
                actions.add(sendAction);
                fi.requestPending = true;
            }
        }
        
        return actions;
    }

    Action checkTerm(JSONObject msg) {
        var actions = NO_ACTIONS;
        var msgTerm = msg.getInt("term");

        if (msgTerm > term) {
            term = msgTerm;
            if (isLeader()) {
                return becomeFollower();
            }
        }

        return actions;
    }

    public Actions start() {
        if (isLeader()) {
            return becomeLeader();
        }
        return becomeFollower();
    }

    Action onClientCommand(JSONObject msg) {
        Action action = NO_ACTIONS;
        if (!isLeader()) {
            action = mkReply(msg, "errmsg", "Not a leader");
        } else {
            switch (msg.getString("cmd")) {
                case "R" -> {
                    var key = msg.getString("key");
                    var value = kv.get(key);
                    action = mkReply(msg, "value", value);
                }
                case "W" -> {
                    pendingResponses.put(msg.getString("reqid"), msg);
                    replicate(msg);
                }
                default -> throw new RuntimeException("Unknown cmd " + msg);
            }
        }
        return action;
    }

    void replicate(JSONObject msg) {
        var entry = mkMsg(
    "term", term,
            "cl_reqid", msg.getString("reqid"),
            "key", msg.get("key"),
            "cmd", msg.get("cmd"),
            "value", msg.get("value")
        );
        log.put(entry);
    }

    Actions processMsg(JSONObject msg) {
        var msgType = msg.getString("type");
        var actions = new Actions();
        if (!(msgType.equals(CMD_REQ))) {
            var action = checkTerm(msg);
            if (action == IGNORE_MSG) {
                return NO_ACTIONS;
            }
        }

        switch (msgType) {
            case APPEND_REQ -> actions.add(onAppendReq(msg));
            case APPEND_RESP -> actions.add(onAppendResp(msg));
            case CMD_REQ -> actions.add(onClientCommand(msg));
            default -> throw new RuntimeException("Unknown msg type " + msgType);
        }
        if (isLeader()) {
            actions.add(sendAppends());
        }
        return actions;
    }

}
