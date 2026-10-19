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
        resolvePublic(host);
    }

    /** For non-URL targets (TCP, database): same address rules, applied to a bare hostname or IP. */
    public void validateHost(String host) {
        resolvePublic(host);
    }

    /**
     * Resolves the host and rejects it if ANY answer is internal. Returns the vetted addresses so a
     * caller that connects to one of them directly (TCP, database) is not exposed to a second,
     * different DNS answer between the check and the connect.
     */
    public InetAddress[] resolvePublic(String host) {
        if (host == null || host.isBlank()) {
            throw new SsrfException("URL has no host");
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new HostNotFoundException();
        }
        if (!allowPrivate) {
            for (InetAddress address : addresses) {
                if (isInternal(address)) {
                    throw new SsrfException("Target address is not allowed");
                }
            }
        }
        return addresses;
    }

    static boolean isInternal(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet6Address) {
            return isInternalV6(b);
        }
        return isInternalV4(b[0] & 0xFF, b[1] & 0xFF, b[2] & 0xFF);
    }

    private static boolean isInternalV4(int first, int second, int third) {
        return first == 0                                          // 0.0.0.0/8
                || (first == 100 && (second & 0xC0) == 64)         // carrier-grade NAT 100.64.0.0/10
                || (first == 192 && second == 0 && third == 0)    // IETF protocol assignments 192.0.0.0/24
                || (first == 192 && second == 0 && third == 2)    // TEST-NET-1
                || (first == 198 && (second & 0xFE) == 18)         // benchmarking 198.18.0.0/15
                || (first == 198 && second == 51 && third == 100) // TEST-NET-2
                || (first == 203 && second == 0 && third == 113)  // TEST-NET-3
                || first >= 240;                                   // reserved 240.0.0.0/4 + broadcast
    }

    private static boolean isNat64(byte[] b) {
        if (b[0] != 0 || b[1] != 0x64 || (b[2] & 0xFF) != 0xFF || (b[3] & 0xFF) != 0x9B) {
            return false;
        }
        for (int i = 4; i < 12; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean embeddedV4IsInternal(byte... v4) {
        try {
            return isInternal(InetAddress.getByAddress(v4));
        } catch (UnknownHostException e) {
            return true;
        }
    }

    /** Allow-list approach: only global unicast 2000::/3 can pass, minus ranges that embed or tunnel IPv4. */
    private static boolean isInternalV6(byte[] b) {
        if (isNat64(b)) {
            // NAT64 64:ff9b::/96 (IPv6-only networks): the last four bytes are the real IPv4 target.
            return embeddedV4IsInternal(b[12], b[13], b[14], b[15]);
        }
        if ((b[0] & 0xE0) != 0x20) {
            return true; // not global unicast: covers ::, ::1, fc00::/7, fe80::/10, ff00::/8, NAT64 64:ff9b::/96, ...
        }
        if (b[0] == 0x20 && b[1] == 0x01 && b[2] == 0 && b[3] == 0) {
            return true; // Teredo 2001::/32 hides an IPv4 address
        }
        if (b[0] == 0x20 && b[1] == 0x02) {
            // 6to4 2002::/16: the next four bytes are an IPv4 address, which must itself pass the full check.
            return embeddedV4IsInternal(b[2], b[3], b[4], b[5]);
        }
        return b[0] == 0x20 && b[1] == 0x01 && b[2] == 0x0d && b[3] == (byte) 0xb8; // documentation 2001:db8::/32
    }
}
