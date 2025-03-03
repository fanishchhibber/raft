package distill.log_kvs;
import java.util.*;
import static distill.Utils.*;
import static distill.log_kvs.Actions.*;

public class Server {
    final Raft raft;

    public Server(String id, List<String> members, boolean isLeader) {
        raft = new Raft(id, members, isLeader);
    }

    void perform(Action action) {
        if (action instanceof Actions actions) {
            for (var act: actions.todos) {
                perform(act); // recursive
            }
        } else {
            if (action instanceof Send sendAction) {
                send(sendAction.msg());
            }
            // In a later exercise there will be timer related actions.
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
