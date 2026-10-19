package in.sentra.security;

/** The hostname did not resolve. Kept distinct from "address not allowed" so probes can say so. */
public class HostNotFoundException extends SsrfException {

    public HostNotFoundException() {
        super("Host could not be resolved");
    }
}
