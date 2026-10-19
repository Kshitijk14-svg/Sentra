package in.sentra.probe;

/**
 * Outcome of one probe run. {@code inconclusive} means the probe could not tell (for example an
 * unparseable answer): it is stored, but it neither counts as a failure nor as a success.
 */
public record ProbeResult(boolean success, Integer statusCode, long latencyMs, String error,
                          String detail, boolean inconclusive) {

    public ProbeResult(boolean success, Integer statusCode, long latencyMs, String error) {
        this(success, statusCode, latencyMs, error, null, false);
    }

    public static ProbeResult ok(long latencyMs, String detail) {
        return new ProbeResult(true, null, latencyMs, null, detail, false);
    }

    public static ProbeResult fail(long latencyMs, String error) {
        return new ProbeResult(false, null, latencyMs, error, null, false);
    }

    public static ProbeResult inconclusive(long latencyMs, String error) {
        return new ProbeResult(false, null, latencyMs, error, null, true);
    }
}
