package distill.replicated_kvs;

import static distill.Utils.*;

import org.json.JSONObject;

import java.util.*;


public class Server {
    final HashMap<String, Object> kv = new HashMap<>();
    final List<String> siblings;
    boolean isLeader;


    static final String APPEND_REQ = "APPEND_REQ";
    static final String APPEND_RESP = "APPEND_RESP";

    static final String CMD_REQ = "CMD_REQ";
    static final String CMD_RESP = "CMD_RESP";


    // reqid -> number of pending replication acks
    HashMap<String, Integer> pendingAcks = new HashMap<>();

    String nodeId;

    public Server(String nodeId, List<String> siblings, boolean isLeader) {
        this.nodeId = nodeId;
        this.isLeader = isLeader;
        this.siblings = siblings;
        if (isLeader) {
            this.pendingAcks = new HashMap<>();
        }
    }

    public void apply(JSONObject msg) {
        var key = msg.getString("key");
        var cmd = msg.getString("cmd");
        var value = msg.get("value");

        throw new RuntimeException("UNIMPLEMENTED");
    }

    int quorumSize() {
        throw new RuntimeException("UNIMPLEMENTED");
    }
    /**
     * Message
     * @param msg from leader to follower
     */
    void onAppendReq(JSONObject msg) {
        throw new RuntimeException("UNIMPLEMENTED");
    }

    void onClientCommand(JSONObject msg) {
        throw new RuntimeException("UNIMPLEMENTED");

    }

    void replicate(JSONObject msg) {
        throw new RuntimeException("UNIMPLEMENTED");

    }


    void onAppendResp(JSONObject msg) {
        throw new RuntimeException("UNIMPLEMENTED");
    }


    // Convenience method for creating a reply to a message
    // The "from", "to" fields are flipped,  the type is flipped to the appropriate
    // response type, and the reqid is copied.
    public static JSONObject mkReply(JSONObject msg, Object... extraKeyValues) {
        JSONObject reply = new JSONObject();
        reply.put("from", nodeId());

        reply.put("to", msg.get("from"));
        if (msg.has("reqid"))
            reply.put("reqid", msg.get("reqid"));
        switch (msg.getString("type")) {
            case CMD_REQ: reply.put("type", CMD_RESP);break;
            case APPEND_REQ: reply.put("type", APPEND_RESP); break;
            default: throw new RuntimeException("msg with unknown type" + msg);
        }
        for (int i = 0; i < extraKeyValues.length; i+=2) {
            reply.put((String)extraKeyValues[i], extraKeyValues[i+1]);
        }
        return reply;
    }

    public void listen() {
        while (true) {
            JSONObject msg = recv();
            String msgType = msg.getString("type");
            switch (msgType) {
                case APPEND_REQ: onAppendReq(msg); break; // APPEND_RESP
                case APPEND_RESP: {
                    onAppendResp(msg);
                    break;
                }
                case CMD_REQ: {
                    onClientCommand(msg);
                    break;
                }
                default: throw new RuntimeException("Unknown message type " + msgType);
            }
        }
    }


    public static void main(String[] args) {
        // Hardcode server 1 as the leader
        var isLeader = nodeId().endsWith("1");
        var server = new Server(nodeId(), siblingNodes(), isLeader);
        server.listen();
    }
}