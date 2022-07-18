/**
 * Copyright (c) 2013 LEAP Encryption Access Project and contributers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package se.leap.bitmaskclient.eip;

import static de.blinkt.openvpn.core.connection.Connection.TransportType.OBFS4;
import static de.blinkt.openvpn.core.connection.Connection.TransportType.OPENVPN;
import static se.leap.bitmaskclient.base.models.Constants.CAPABILITIES;
import static se.leap.bitmaskclient.base.models.Constants.IP_ADDRESS;
import static se.leap.bitmaskclient.base.models.Constants.IP_ADDRESS6;
import static se.leap.bitmaskclient.base.models.Constants.OPTIONS;
import static se.leap.bitmaskclient.base.models.Constants.PORTS;
import static se.leap.bitmaskclient.base.models.Constants.PROTOCOLS;
import static se.leap.bitmaskclient.base.models.Constants.PROVIDER_PRIVATE_KEY;
import static se.leap.bitmaskclient.base.models.Constants.PROVIDER_VPN_CERTIFICATE;
import static se.leap.bitmaskclient.base.models.Constants.REMOTE;
import static se.leap.bitmaskclient.base.models.Constants.TRANSPORT;
import static se.leap.bitmaskclient.base.models.Constants.TYPE;
import static se.leap.bitmaskclient.base.models.Constants.UDP;
import static se.leap.bitmaskclient.base.utils.ConfigHelper.ObfsVpnHelper.useObfsVpn;
import static se.leap.bitmaskclient.pluggableTransports.Shapeshifter.DISPATCHER_IP;
import static se.leap.bitmaskclient.pluggableTransports.Shapeshifter.DISPATCHER_PORT;

import androidx.annotation.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Iterator;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.VpnStatus;
import de.blinkt.openvpn.core.connection.Connection;
import se.leap.bitmaskclient.BuildConfig;
import se.leap.bitmaskclient.base.models.Provider;
import se.leap.bitmaskclient.base.utils.ConfigHelper;
import se.leap.bitmaskclient.pluggableTransports.Obfs4Options;

public class VpnConfigGenerator {
    private JSONObject generalConfiguration;
    private JSONObject gateway;
    private JSONObject secrets;
    private JSONObject obfs4Transport;
    private int apiVersion;
    private boolean preferUDP;


    public final static String TAG = VpnConfigGenerator.class.getSimpleName();
    private final String newLine = System.getProperty("line.separator"); // Platform new line

    public VpnConfigGenerator(JSONObject generalConfiguration, JSONObject secrets, JSONObject gateway, int apiVersion, boolean preferUDP) throws ConfigParser.ConfigParseError {
        this.generalConfiguration = generalConfiguration;
        this.gateway = gateway;
        this.secrets = secrets;
        this.apiVersion = apiVersion;
        this.preferUDP = preferUDP;
        checkCapabilities();
    }

    public void checkCapabilities() throws ConfigParser.ConfigParseError {

        try {
            if (apiVersion >= 3) {
                JSONArray supportedTransports = gateway.getJSONObject(CAPABILITIES).getJSONArray(TRANSPORT);
                for (int i = 0; i < supportedTransports.length(); i++) {
                    JSONObject transport = supportedTransports.getJSONObject(i);
                    if (transport.getString(TYPE).equals(OBFS4.toString())) {
                        obfs4Transport = transport;
                        break;
                    }
                }
            }

        } catch (JSONException e) {
            throw new ConfigParser.ConfigParseError("Api version ("+ apiVersion +") did not match required JSON fields");
        }
    }

    public HashMap<Connection.TransportType, VpnProfile> generateVpnProfiles() throws
            ConfigParser.ConfigParseError,
            NumberFormatException,
            JSONException,
            IOException {
        HashMap<Connection.TransportType, VpnProfile> profiles = new HashMap<>();
        profiles.put(OPENVPN, createProfile(OPENVPN));
        if (supportsObfs4()) {
            try {
                profiles.put(OBFS4, createProfile(OBFS4));
            } catch (ConfigParser.ConfigParseError | NumberFormatException | JSONException | IOException e) {
                e.printStackTrace();
            }
        }
        return profiles;
    }

    private boolean supportsObfs4(){
        return obfs4Transport != null;
    }

    private String getConfigurationString(Connection.TransportType transportType) {
        return generalConfiguration()
                + newLine
                + gatewayConfiguration(transportType)
                + newLine
                + androidCustomizations()
                + newLine
                + secretsConfiguration();
    }

    @VisibleForTesting
    protected VpnProfile createProfile(Connection.TransportType transportType) throws IOException, ConfigParser.ConfigParseError, JSONException {
        String configuration = getConfigurationString(transportType);
        ConfigParser icsOpenvpnConfigParser = new ConfigParser();
        icsOpenvpnConfigParser.parseConfig(new StringReader(configuration));
        if (transportType == OBFS4) {
            icsOpenvpnConfigParser.setObfs4Options(getObfs4Options());
        }
        return icsOpenvpnConfigParser.convertProfile(transportType);
    }

    private Obfs4Options getObfs4Options() throws JSONException {
        JSONObject transportOptions = obfs4Transport.getJSONObject(OPTIONS);
        String iatMode = transportOptions.getString("iatMode");
        String cert = transportOptions.getString("cert");
        String port = obfs4Transport.getJSONArray(PORTS).getString(0);
        String ip = gateway.getString(IP_ADDRESS);
        boolean udp = false;

        if (BuildConfig.obfsvpn_pinning) {
            cert = BuildConfig.obfsvpn_cert;
            port = BuildConfig.obfsvpn_port;
            ip = BuildConfig.obfsvpn_port;
            udp = BuildConfig.obfsvpn_use_kcp;
        }
        return new Obfs4Options(ip, port, cert, iatMode, udp);
    }

    private String generalConfiguration() {
        String commonOptions = "";
        try {
            Iterator keys = generalConfiguration.keys();
            while (keys.hasNext()) {
                String key = keys.next().toString();

                commonOptions += key + " ";
                for (String word : String.valueOf(generalConfiguration.get(key)).split(" "))
                    commonOptions += word + " ";
                commonOptions += newLine;

            }
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        commonOptions += "client";

        return commonOptions;
    }

    private String gatewayConfiguration(Connection.TransportType transportType) {
        String remotes = "";

        StringBuilder stringBuilder = new StringBuilder();
        try {
            String ipAddress = null;
            JSONObject capabilities = gateway.getJSONObject(CAPABILITIES);
            switch (apiVersion) {
                default:
                case 1:
                case 2:
                    ipAddress = gateway.getString(IP_ADDRESS);
                    gatewayConfigApiv1(stringBuilder, ipAddress, capabilities);
                    break;
                case 3:
                case 4:
                    ipAddress = gateway.optString(IP_ADDRESS);
                    String ipAddress6 = gateway.optString(IP_ADDRESS6);
                    String[] ipAddresses = ipAddress6.isEmpty()  ?
                            new String[]{ipAddress} :
                            new String[]{ipAddress6, ipAddress};
                    JSONArray transports = capabilities.getJSONArray(TRANSPORT);
                    gatewayConfigMinApiv3(transportType, stringBuilder, ipAddresses, transports);
                    break;
            }
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        remotes = stringBuilder.toString();
        if (remotes.endsWith(newLine)) {
            remotes = remotes.substring(0, remotes.lastIndexOf(newLine));
        }

        return remotes;
    }

    private void gatewayConfigMinApiv3(Connection.TransportType transportType, StringBuilder stringBuilder, String[] ipAddresses, JSONArray transports) throws JSONException {
        if (transportType == OBFS4) {
            obfs4GatewayConfigMinApiv3(stringBuilder, ipAddresses, transports);
        } else {
            ovpnGatewayConfigMinApi3(stringBuilder, ipAddresses, transports);
        }
    }

    private void gatewayConfigApiv1(StringBuilder stringBuilder, String ipAddress, JSONObject capabilities) throws JSONException {
        int port;
        String protocol;
        JSONArray ports = capabilities.getJSONArray(PORTS);
        JSONArray protocols = capabilities.getJSONArray(PROTOCOLS);
        for (int i = 0; i < ports.length(); i++) {
            port = ports.getInt(i);
            for (int j = 0; j < protocols.length(); j++) {
                protocol = protocols.optString(j);
                String newRemote = REMOTE + " " + ipAddress + " " + port + " " + protocol + newLine;
                stringBuilder.append(newRemote);
            }
        }
    }

    private void ovpnGatewayConfigMinApi3(StringBuilder stringBuilder, String[] ipAddresses, JSONArray transports) throws JSONException {
        String port;
        String protocol;
        JSONObject openvpnTransport = getTransport(transports, OPENVPN);
        JSONArray ports = openvpnTransport.getJSONArray(PORTS);
        JSONArray protocols = openvpnTransport.getJSONArray(PROTOCOLS);
        if (preferUDP) {
            StringBuilder udpRemotes = new StringBuilder();
            StringBuilder tcpRemotes = new StringBuilder();
            for (int i = 0; i < protocols.length(); i++) {
                protocol = protocols.optString(i);
                for (int j = 0; j < ports.length(); j++) {
                    port = ports.optString(j);
                    for (String ipAddress : ipAddresses) {
                        String newRemote = REMOTE + " " + ipAddress + " " + port + " " + protocol + newLine;
                        if (UDP.equals(protocol)) {
                            udpRemotes.append(newRemote);
                        } else {
                            tcpRemotes.append(newRemote);
                        }
                    }
                }
            }
            stringBuilder.append(udpRemotes.toString());
            stringBuilder.append(tcpRemotes.toString());
        } else {
            for (int j = 0; j < ports.length(); j++) {
                port = ports.getString(j);
                for (int k = 0; k < protocols.length(); k++) {
                    protocol = protocols.optString(k);
                    for (String ipAddress : ipAddresses) {
                        String newRemote = REMOTE + " " + ipAddress + " " + port + " " + protocol + newLine;
                        stringBuilder.append(newRemote);
                    }
                }
            }
        }
    }

    private JSONObject getTransport(JSONArray transports, Connection.TransportType transportType) throws JSONException {
        JSONObject selectedTransport = new JSONObject();
        for (int i = 0; i < transports.length(); i++) {
            JSONObject transport = transports.getJSONObject(i);
            if (transport.getString(TYPE).equals(transportType.toString())) {
                selectedTransport = transport;
                break;
            }
        }
        return selectedTransport;
    }

    private void obfs4GatewayConfigMinApiv3(StringBuilder stringBuilder, String[] ipAddresses, JSONArray transports) throws JSONException {
        JSONObject obfs4Transport = getTransport(transports, OBFS4);
        JSONArray protocols = obfs4Transport.getJSONArray(PROTOCOLS);
        //for now only use ipv4 gateway the syntax route remote_host 255.255.255.255 net_gateway is not yet working
        // https://community.openvpn.net/openvpn/ticket/1161
        /*for (String ipAddress : ipAddresses) {
            String route = "route " + ipAddress + " 255.255.255.255 net_gateway" + newLine;
            stringBuilder.append(route);
        }*/

        if (ipAddresses.length == 0) {
            return;
        }

        // check if at least one address is IPv4, IPv6 is currently not supported for obfs4
        String ipAddress = null;
        for (String address : ipAddresses) {
            if (ConfigHelper.isIPv4(address)) {
                ipAddress = address;
                break;
            }
            VpnStatus.logWarning("Skipping IP address " + address + " while configuring obfs4.");
        }

        if (ipAddress == null) {
            VpnStatus.logError("No matching IPv4 address found to configure obfs4.");
            return;
        }

        // check if at least one protocol is TCP, UDP is currently not supported for obfs4
        boolean hasTcp = false;
        for (int i = 0; i < protocols.length(); i++) {
            String protocol = protocols.getString(i);
            if (protocol.contains("tcp")) {
                hasTcp = true;
            }
        }

        if (!hasTcp) {
            VpnStatus.logError("obfs4 currently only allows TCP! Skipping obfs4 config for ip " + ipAddress);
            return;
        }

        JSONArray ports = obfs4Transport.getJSONArray(PORTS);
        if (ports.isNull(0)){
            VpnStatus.logError("Misconfigured provider: no ports defined in obfs4 transport JSON.");
            return;
        }

        String route = "route " + ipAddress + " 255.255.255.255 net_gateway" + newLine;
        stringBuilder.append(route);
        if (useObfsVpn()) {
            String remote;
            if (BuildConfig.obfsvpn_pinning) {
                remote = REMOTE + " " + BuildConfig.obfsvpn_ip + " " + BuildConfig.obfsvpn_port + newLine;
            } else {
                remote = REMOTE + " " + ipAddress + " " + ports.getString(0) + newLine;
            }

            stringBuilder.append(remote);
        } else {
            String remote = REMOTE + " " + DISPATCHER_IP + " " + DISPATCHER_PORT + " tcp" + newLine;
            stringBuilder.append(remote);
        }
    }

    private String secretsConfiguration() {
        try {
            String ca =
                    "<ca>"
                            + newLine
                            + secrets.getString(Provider.CA_CERT)
                            + newLine
                            + "</ca>";

            String key =
                    "<key>"
                            + newLine
                            + secrets.getString(PROVIDER_PRIVATE_KEY)
                            + newLine
                            + "</key>";

            String openvpnCert =
                    "<cert>"
                            + newLine
                            + secrets.getString(PROVIDER_VPN_CERTIFICATE)
                            + newLine
                            + "</cert>";

            return ca + newLine + key + newLine + openvpnCert;
        } catch (JSONException e) {
            e.printStackTrace();
            return "";
        }
    }

    private String androidCustomizations() {
        return
                "remote-cert-tls server"
                        + newLine
                        + "persist-tun"
                        + newLine
                        + "auth-retry nointeract";
    }
}
