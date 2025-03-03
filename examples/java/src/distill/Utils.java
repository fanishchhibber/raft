package distill;

import org.json.JSONObject;

import java.io.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 
 * This is a convenience library to help send and receive messages from distill.
 * It multiplexes input and timers.
 * The reason everything is static is because all I/O is done through stdin/stdout,
 * so the entire process is forced to be one node. It is of course possible to
 * multiplex these channels, but not worth it in the interest of pedagogy.
 */

public class Utils {
    // All requests go out with a unique reqid attribute.
    private static int reqid = 1; // Global request ID counter

    // Global message queue to receive messages from other nodes and timeout events
    private static final BlockingQueue<JSONObject> msgQ = new LinkedBlockingQueue<>();

    // Global node ID
    private static String GNODE_ID = null;

    // Global configuration
    private static HashMap<String, String> _g_config = null;

    // Global pattern for sibling nodes
    private static String _others_pat = null;

    // Debug file writer
    private static PrintWriter dumpf = null;

    // Exit message
    private static final JSONObject EXIT_MSG = new JSONObject();
    // Thread for reading stdin
    private static Thread readerThread = null;

    /**
     * Sets a timer that expires after the specified duration. When the timer
     * expires, the next attempt to recv() will a message like this <p>
     * <pre>
     *   { "type": "TIMEOUT", "name": "<name>, "timer": timer} 
     *</pre>
     * @param durationSeconds The duration in seconds.
     * @param name            Optional name for the timer.
     * @return The java.util.Timer object which can be used to cancel the timer.
     */

    public static Timer setTimeout(double durationSeconds, String name) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                JSONObject timeoutMsg = new JSONObject();
                timeoutMsg.put("type", "TIMEOUT");
                timeoutMsg.put("name", name);
                timeoutMsg.put("timer", timer);
                if (!msgQ.offer(timeoutMsg)) {
                    System.err.println("msgQ.put failed");
                    System.exit(1);
                }
            }
        }, (long) (durationSeconds * 1000));
        return timer;
    }


    /**
     * Sends a message by formatting it as JSON and printing it to stdout.
     *
     * @param msg The message to send.
     */
    public static void send(JSONObject msg) {
        if (!msg.has("to")) {
            throw new IllegalArgumentException("Message must contain a 'to' field");
        }
        System.out.println(msg);
        System.out.flush();
    }

    /**
     * Adds reqid to message and calls send
     * @param msg
     * @return
     */
    public static String req(JSONObject msg) {
        var msg_reqid = String.format("%d.%s", reqid, nodeId());
        msg.put("reqid", msg_reqid);
        send(msg);
        reqid++;
        return msg_reqid;
    }

    /** 
     * Converts a sequence of alternate keys and values to a JSON object
     */
    public static JSONObject mkMsg(final Object... args) {
        if (args.length % 2 != 0) {
            throw new RuntimeException("Expected even number of args");
        }
        var msg = new JSONObject();
        for (int i = 0; i < args.length; i+=2) {
            msg.put((String)args[i], args[i+1]);
        }
        return msg;
    }

    /**
     * Converts a sequence of alternate keys and values to a JSON object, and 
     * sends it as a request.
     * @param args
     * @return
     */
    public static String req(Object... args) {
        return req(mkMsg(args));
    }


    /**
     * Returns the node ID of the current process.
     *
     * @return The node ID.
     */
    public static String nodeId() {
        if (GNODE_ID != null) {
            return GNODE_ID;
        }
        String[] args = System.getProperty("sun.java.command").split(" ");
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--id")) {
                GNODE_ID = args[i + 1];
                break;
            }
        }
        if (GNODE_ID == null) {
            System.err.println("--id <id> argument not supplied");
            System.exit(1);
        }
        if (!getNodes().contains(GNODE_ID)) {
            System.err.println("id " + GNODE_ID + " is not present in config file");
            System.exit(1);
        }
        return GNODE_ID;
    }

    /**
     * Returns a list of node ids present in the given config file 
     * matching the specified pattern.
     *
     * @param pattern The regex pattern to match.
     * @return A list of node IDs.
     */
    public static List<String> getNodes(String pattern) {
        if (_g_config == null) {
            String[] args = System.getProperty("sun.java.command").split(" ");
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("--config")) {
                    String configFile = args[i + 1];
                    try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        _g_config = new HashMap<>();
                        var json = new org.json.JSONObject(sb.toString()).toMap();
                        for (Map.Entry<String, Object> entry : json.entrySet()) {
                            String key = entry.getKey();
                            Object value = entry.getValue();
                            _g_config.put(key, value.toString()); // Convert any value to a string
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("--config <configfile> absent in command-line parameters", e);
                    }
                }
            }
            if (_g_config == null) {
                throw new RuntimeException("--config <configfile> absent in command-line parameters");
            }
        }
        List<String> nodes = new ArrayList<>();
        Pattern p = Pattern.compile(pattern);
        for (String node : _g_config.keySet()) {
            if (p.matcher(node).find()) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    /**
     * @return a list of all nodes in the config file
     */
    public static List<String> getNodes() {
        return getNodes(".*");
    }

    /**
     *
     * @return  a list of sibling nodes (nodes with the same prefix but different IDs).
     */
    public static List<String> siblingNodes() {
        String pat = siblingsPattern();
        return getNodes(pat);
    }

    /**
     * Returns a regex pattern that matches all sibling node IDs except the current node.
     *
     * @return The regex pattern.
     */
    public static String siblingsPattern() {
        if (_others_pat != null) {
            return _others_pat;
        }
        String me = nodeId();
        Matcher match = Pattern.compile("^(.*)(\\d+)$").matcher(me);
        if (match.find()) {
            String prefix = match.group(1);
            String digit = match.group(2);
            _others_pat = prefix + "[^" + digit + "]";
        }
        return _others_pat;
    }

    /**
     * Writes a debug message to a file. This file is opened once and closed automatically
     * when the process dies.
     *
     * @param message The message to log.
     */
    public static void dump(String message) {
        if (dumpf == null) {
            try {
                dumpf = new PrintWriter(new FileWriter("DEBUG_" + nodeId() + ".txt"));
            } catch (IOException e) {
                e.printStackTrace(System.err);
                System.exit(1);
            }
        }
        dumpf.println(System.currentTimeMillis() + " " + message);
        dumpf.flush();
    }

    /**
     * Reads messages from stdin and adds them to the message queue.
     * This is the entry point for the thread.
     */
    private static void stdinReader() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            String line;
            try {
                line = reader.readLine();
                if (line == null) {
                    msgQ.offer(EXIT_MSG);
                    break;
                }
                line = line.trim();
                if (line.startsWith(".q")) {
                    msgQ.offer(EXIT_MSG);
                    break;
                }
                if (!line.isEmpty()) {
                    try {
                        var JSONObject = new JSONObject(line);
                        //noinspection ResultOfMethodCallIgnored
                        msgQ.offer(JSONObject);
                    } catch (org.json.JSONException e) {
                        if (line.startsWith("{"))
                            System.err.println("Malformed JSON: " + line);
                        else
                            System.err.println("*** " + line);
                        System.err.println("***IGNORED** " + line);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace(System.err);
                System.exit(1);
            }
        }

        System.exit(0);
    }

    /**
     * Receives the next message from the message queue.
     * Messages are either from other nodes or timeout events of the form
    * <pre>
     *   { "type": "TIMEOUT", "name": "&lt;name&gt;", "timer": timer} 
     *</pre>
     *
     * @return The received message.
     */
    public static JSONObject recv() {
        if (readerThread == null) {
            // Race condition, but only if recv is called by multiple threads
            readerThread = new Thread(Utils::stdinReader);
            readerThread.setDaemon(true); // Daemon thread to allow JVM to exit
            readerThread.start();
        }
        try {
            JSONObject msg = msgQ.take();
            if (msg == EXIT_MSG) {
                System.exit(0);
            }
            return msg;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Uses the stderr channel to send log messages back to distill
     * The "INFO" in front tells distill not to treat it as an error.
     * 
     * @param obj
     */
    public static void info(Object obj) {
        System.err.println("INFO " + obj);
        System.err.flush();
    }
}
