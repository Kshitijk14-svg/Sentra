package in.sentra.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * Rejects URLs that would make the server call itself or its internal network (Doc 1 §7.3).
 * Called when a check or webhook is created and again immediately before each request, because
 * DNS can change after creation. A short DNS-rebinding window between this lookup and the
 * connect remains (see docs/decisions.md).
 */
@Component
public class SsrfGuard {

    private final boolean allowPrivate;

    public SsrfGuard(@Value("${sentra.ssrf.allow-private:false}") boolean allowPrivate) {
        this.allowPrivate = allowPrivate;
    }

    public void validate(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new SsrfException("Invalid URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new SsrfException("Only http and https URLs are allowed");
        }
        if (uri.getUserInfo() != null) {
            throw new SsrfException("URLs with credentials are not allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SsrfException("URL has no host");
        }
        if (allowPrivate) {
            return;
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new SsrfException("Host could not be resolved");
        }
        // Every resolved address must be public; one private answer is enough to reject.
        for (InetAddress address : addresses) {
            if (isInternal(address)) {
                throw new SsrfException("Target address is not allowed");
            }
        }
    }

    static boolean isInternal(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet6Address) {
            return (b[0] & 0xFE) == 0xFC; // unique local fc00::/7
        }
        int first = b[0] & 0xFF;
        int second = b[1] & 0xFF;
        return first == 0                                // 0.0.0.0/8
                || (first == 100 && (second & 0xC0) == 64); // carrier-grade NAT 100.64.0.0/10
    }
}
