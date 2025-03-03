package distill.log_kvs;

import org.json.JSONObject;

import static distill.Utils.*;

public class Client {
    public JSONObject sendrecv(JSONObject msg) {
        req(msg);
        return recv();
    }
    public void write(String key, int value) {
        var msg = mkMsg("from", nodeId(), "to", "S1", "type", "CMD_REQ", "cmd", "W", "key", key, "value", value);
        JSONObject response = sendrecv(msg);
        assert !response.has("errmsg");
    }

    // IV == Index and Value combo. The index is the index of the log assigned to
    // that command.
    public int read(String key) {
        var msg = mkMsg("from", nodeId(), "to", "S1", "type", "CMD_REQ", "cmd", "R", "key", key);
        JSONObject response = sendrecv(msg);
        assert !response.has("errmsg");
        return (int) response.get("value");
    }

    public void testRun() {
        // Launch 3 S processes configured in config.json

        req("from", nodeId(), "to", "distill", "type", "exec", "id", "S.*");

        write("a", 10);
        write("a", 20);
        write("b", 30);

        var reply = read("a");
        // Check if latest value
        assert reply == 20;
        info("SUCCESS");

    }


    public static void main(String[] args) {
        new Client().testRun();
    }
}