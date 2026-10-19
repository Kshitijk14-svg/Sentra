package in.sentra.alert;

public interface Notifier {

    String channel();

    /** Throws on any delivery failure; the caller records it. */
    void send(String destination, AlertMessage message) throws Exception;
}
