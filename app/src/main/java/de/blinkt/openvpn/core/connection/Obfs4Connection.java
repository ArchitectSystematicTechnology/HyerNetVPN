package de.blinkt.openvpn.core.connection;

import se.leap.bitmaskclient.pluggableTransports.HoppingObfsVpnClient;
import se.leap.bitmaskclient.pluggableTransports.Obfs4Options;
import se.leap.bitmaskclient.pluggableTransports.ObfsVpnClient;


/**
 * Created by cyberta on 08.03.19.
 */

public class Obfs4Connection extends Connection {

    private static final String TAG = Obfs4Connection.class.getName();
    private Obfs4Options options;

    public Obfs4Connection(Obfs4Options options) {
        setServerName(options.gatewayIP);
        setServerPort(options.transport.getPorts()[0]);
        setProxyName(ObfsVpnClient.SOCKS_IP);
        setProxyType(ProxyType.SOCKS5);
        switch (options.transport.getTransportType()) {
            case OBFS4:
                setUseUdp(false);
                setProxyPort(String.valueOf(ObfsVpnClient.SOCKS_PORT.get()));
                break;
            case OBFS4_HOP:
                setUseUdp(true);
                setProxyPort(String.valueOf(HoppingObfsVpnClient.PORT));
                break;
            default:break;
        }

        setProxyAuthUser(null);
        setProxyAuthPassword(null);
        setUseProxyAuth(false);
        this.options = options;
    }

    @Override
    public Connection clone() throws CloneNotSupportedException {
        Obfs4Connection connection = (Obfs4Connection) super.clone();
        connection.options = this.options;
        return connection;
    }

    @Override
    public TransportType getTransportType() {
        return TransportType.OBFS4;
    }


    public Obfs4Options getObfs4Options() {
        return options;
    }

}
