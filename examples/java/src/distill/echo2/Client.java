package distill.echo2;
import org.json.JSONObject;

import static distill.Utils.*;

public class Client {
    public static void main(String[] args) throws Exception{
        var myid = nodeId();

        var msg = new JSONObject();
        msg.put("to", "server_1");
        msg.put("from", myid);
        int num = 1;
        for (int i = 0; i < 10; i++) {
            // Send to echo server
            msg.put("num", num * 2);
            send(msg);
            // Wait for response
            var rcvdMsg = recv();
            num = rcvdMsg.getInt("num");
        }
        System.err.println("INFO Success: " + num);
    }
}
