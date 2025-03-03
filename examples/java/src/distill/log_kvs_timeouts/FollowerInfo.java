package distill.log_kvs_timeouts;

public class FollowerInfo {
    /**
     * Name of follower
     **/
    public String follower_id;
    /**
     * Represents the leader's knowledge (or guess) about the follower's log
     * length.
     *
     * A newly elected leader starts with not knowing the length of a follower's
     * log, so it sets logLength to the length of its own log to begin with,
     * and isLogLengthKNown to false (See below) <br>
     * <br>
     * Upon receipt of an AppendResp, the logLength is set to the msg["index"],
     * and isLogLengthKnown to msg["success"]. <br><br>
     *
     * It is only when isLogLengthKnown is true is the logLength taken seriously;
     * the leader has an accurate knowledge of the lag between the follower's log
     * and its own. Also, the length is taken into account in updateCommitted.<br>
     * When isLogLengthKnown is false, an AppendReq is sent with an empty entry
     * list, but with all other information. (@see sendAppends)
     */
    public int logLength;
    public boolean isLogLengthKnown;

    /**
     * Set when an AppendReq is sent and reset when an AppendResp is received.
     * This helps with batching.
     */
    public boolean requestPending;

    /**
     * Set when Timeout message arrives for this follower, in onTimeout
     * Reset (to false) everytime an AppendRequest is sent out, and every time
     * an AppendResponse is received.
     */
    public boolean heartbeatTimerExpired;

    public FollowerInfo(String id, int logLength, boolean requestPending, boolean isLogLengthKnown, boolean heartbeatTimerExpired) {
        this.follower_id = id;
        this.logLength = logLength;
        this.requestPending = requestPending;
        this.isLogLengthKnown = isLogLengthKnown;
        this.heartbeatTimerExpired = heartbeatTimerExpired;
    }
}
