package distill.echo2;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import static distill.Utils.*;

public class Server {
    public static void main(String[] args) throws Exception {
        while (true) {
            var msg = recv();
            info("Received " + msg);

            var from = msg.getString("from");
            var to = msg.getString("to");
            var num = msg.getInt("num");
            if (num > 8000) break;
            msg.put("from", to);
            msg.put("to", from);
            msg.put("num", num * 2);

            send(msg);
        }

    }
}
