package distill.echo;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Client {
    public static void main(String[] args) throws Exception{
        String myid = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--id")) {
                myid = args[i + 1];
                break;
            }
        }
        if (myid == null) {
            throw new RuntimeException("--id not specified");
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        var msg = new JSONObject();
        msg.put("to", "server_1");
        msg.put("from", myid);
        int num = 1;
        for (int i = 0; i < 10; i++) {
            // Send to echo server
            msg.put("num", num * 2);
            System.out.println(msg);

            // Wait for response
            var line = reader.readLine();
            var rcvdMsg = new JSONObject(line);
            num = rcvdMsg.getInt("num");
        }
        System.err.println("INFO Success: " + num);
    }
}
