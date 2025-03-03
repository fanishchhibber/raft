package distill.echo;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Server {
    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            var line = reader.readLine();
            var msg = new JSONObject(line);
            System.err.println("Received " + msg);
            var from = msg.getString("from");
            var to = msg.getString("to");
            var num = msg.getInt("num");
            msg.put("from", to);
            msg.put("to", from);
            msg.put("num", num * 2);
            System.out.println(msg);
            System.out.flush();
        }

    }
}
