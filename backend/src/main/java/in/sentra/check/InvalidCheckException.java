package in.sentra.check;

/** The request is well-formed but not valid for the chosen check type. Mapped to HTTP 400. */
public class InvalidCheckException extends RuntimeException {

    public InvalidCheckException(String message) {
        super(message);
    }
}
