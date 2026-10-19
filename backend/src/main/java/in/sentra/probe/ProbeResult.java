package in.sentra.probe;

public record ProbeResult(boolean success, Integer statusCode, long latencyMs, String error) {
}
