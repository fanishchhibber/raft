package distill.log_kvs;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Raft reacts to incoming messages by returning an Action or Actions object
 * which is interpreted outside of the core Raft engine in order to do
 * environment specific actions such as timers and I/O. This example does not
 * have disk writes for Raft logs.
 */
public interface Action {
    String ELECTION = "ELECTION";
    record Send(JSONObject msg) implements Action {}

    /**
     * @param name is either "ELECTION" to start an election timer,
     * or name of a follower to start a heartbeat timer.
     * The server invoking the raft engine decides an appropriate
     * duration
     */
    record SetAlarm(String name) implements Action {}

    record CancelAlarm(String name) implements Action {}
    record Replicate(JSONObject item) implements Action {}
}

/**
 * A list of Actions, that is also an Action itself.
 */
class Actions implements Action {
    List<Action> todos = new ArrayList<>();
    static final Actions NO_ACTIONS = new Actions();

    public Actions() {}
    public Actions(Action action) {
        add(action);
    }
    Actions add(Action action) {
        if (action instanceof Actions) {
            todos.addAll(((Actions)action).todos);
        } else {
            todos.add(action);
        }
        return this;
    }

    int size() {
        return todos.size();
    }
    Action get(int i) {
        return todos.get(i);
    }

    @Override
    public String toString() {
        return todos.toString();
    }
}