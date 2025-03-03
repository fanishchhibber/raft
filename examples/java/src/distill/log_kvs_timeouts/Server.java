package distill.log_kvs_timeouts;
import java.util.*;
import static distill.Utils.*;
import static distill.log_kvs_timeouts.Actions.*;

public class Server {
    final Raft raft;

    /**
     * THe key can be the id of a follower or the string "ELECTION"
     * The value is a Timer object returned by Utils.setTimeout
     */
    final Map<String, Timer> timers;


    public Server(String id, List<String> members, boolean isLeader) {
        raft = new Raft(id, members, isLeader);
        timers = new HashMap<>();
    }

    void perform(Action action) {
        if (action instanceof Actions actions) {
            for (var act : actions.todos) {
                perform(act); // recursive
            }
        } else {
            if (action instanceof Send sendAction) {
                send(sendAction.msg());
            } else if (action instanceof SetAlarm setAlarm) {
                String name = setAlarm.name();
                //TODO: if there is a timer object corresponding to name
                //TODO:    cancel it and remove it from map
                //TODO: if name is ELECTION,
                //TODO:    throw an exception because we haven't implemented elections yet.
                //TODO: else if a follower name
                //TODO:    call Utils.setTimeout and update timers object.
                throw new RuntimeException("UNIMPLEMENTED");

            } else if (action instanceof CancelAlarm cancelAlarm) {
                String name = cancelAlarm.name();
                //TODO: if there is a timer object corresponding to name and remove from map.
            }
        }
    }
    void loop() {
        var actions = raft.start();
        perform(actions);

        info("Raft server " + raft.myId + " started as " +
                (raft.isLeader() ? "leader" : "follower"));

        //noinspection InfiniteLoopStatement
        while (true) {
            var msg = recv();
            actions = raft.processMsg(msg);
            perform(actions);
        }
    }

    public static void main(String[] args) {
        String id = nodeId();
        boolean isLeader = id.endsWith("1");
        var server = new Server(id, siblingNodes(), isLeader);
        server.loop();
    }
}
