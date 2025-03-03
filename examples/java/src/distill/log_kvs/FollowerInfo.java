package distill.log_kvs;

public class FollowerInfo {
    /**
     * Name of follower
     **/
    public String follower_id;
    /**
     * Represents the leader's knowledge (or guess) about the follower's log
     * length. A newly elected leader starts with not knowing the length of a follower's
     * log, so it assumes that it is equal to its own log length and attempts to
     * append subsequent entries.
     */
    public int logLength;

    /**
     * Set when an AppendReq is sent and reset when an AppendResp is received.
     * This helps with batching.
     */
    public boolean requestPending;

    public FollowerInfo(String id, int logLength, boolean requestPending) {
        this.follower_id = id;
        this.logLength = logLength;
        this.requestPending = requestPending;
    }
}
