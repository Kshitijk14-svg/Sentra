package in.sentra.security;

public class SsrfException extends RuntimeException {

    public SsrfException(String message) {
        super(message);
    }
}
