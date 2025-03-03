package distill.log_kvs_timeouts;

import java.util.Map;

public class Events {
    public final static String APPEND_REQ="APPEND_REQ";
    public final static String APPEND_RESP="APPEND_RESP";
    public final static String VOTE_REQ="VOTE_REQ";
    public final static String VOTE_RESP="VOTE_RESP";

    public final static String CMD_REQ="CMD_REQ";
    public final static String CMD_RESP="CMD_RESP";

    public final static String TIMEOUT = "TIMEOUT";

    /**
     * map request type to its response type
     */
    public static final Map<String, String> responseType =
            Map.of(APPEND_REQ, APPEND_RESP,
                    VOTE_REQ, VOTE_RESP,
                    CMD_REQ, CMD_RESP);

}
