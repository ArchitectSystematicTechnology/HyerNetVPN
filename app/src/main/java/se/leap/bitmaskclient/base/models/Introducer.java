package se.leap.bitmaskclient.base.models;

import java.net.URI;
import java.net.URISyntaxException;

public class Introducer {
    private String type;
    private String address;
    private String certificate;
    private String fullyQualifiedDomainName;
    private boolean kcpEnabled;

    public Introducer(String type, String address, String certificate, String fullyQualifiedDomainName, boolean kcpEnabled) {
        this.type = type;
        this.address = address;
        this.certificate = certificate;
        this.fullyQualifiedDomainName = fullyQualifiedDomainName;
        this.kcpEnabled = kcpEnabled;
    }

    public boolean validate() {
        if (!"obfsvpnintro".equals(type)) {
            throw new IllegalArgumentException("Unknown type: " + type);
        }
        if (!address.contains(":") || address.split(":").length != 2) {
            throw new IllegalArgumentException("Expected address in format ipaddr:port");
        }
        if (certificate.length() != 70) {
            throw new IllegalArgumentException("Wrong certificate length: " + certificate.length());
        }
        if (!"localhost".equals(fullyQualifiedDomainName) && fullyQualifiedDomainName.split("\\.").length < 2) {
            throw new IllegalArgumentException("Expected a FQDN, got: " + fullyQualifiedDomainName);
        }
        return true;
    }

    public static Introducer fromUrl(String introducerUrl) throws URISyntaxException {
        URI uri = new URI(introducerUrl);
        String fqdn = getQueryParam(uri, "fqdn");
        if (fqdn == null || fqdn.isEmpty()) {
            throw new IllegalArgumentException("FQDN not found in the introducer URL");
        }

        boolean kcp = "1".equals(getQueryParam(uri, "kcp"));

        String cert = getQueryParam(uri, "cert");
        if (cert == null || cert.isEmpty()) {
            throw new IllegalArgumentException("Cert not found in the introducer URL");
        }

        return new Introducer(uri.getScheme(), uri.getAuthority(), cert, fqdn, kcp);
    }

    private static String getQueryParam(URI uri, String param) {
        String[] queryParams = uri.getQuery().split("&");
        for (String queryParam : queryParams) {
            String[] keyValue = queryParam.split("=");
            if (keyValue.length == 2 && keyValue[0].equals(param)) {
                return keyValue[1];
            }
        }
        return null;
    }
}