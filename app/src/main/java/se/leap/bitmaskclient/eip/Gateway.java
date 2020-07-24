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

import android.content.Context;
import androidx.annotation.NonNull;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.connection.Connection;
import se.leap.bitmaskclient.utils.PreferenceHelper;

import static se.leap.bitmaskclient.Constants.HOST;
import static se.leap.bitmaskclient.Constants.IP_ADDRESS;
import static se.leap.bitmaskclient.Constants.LOCATION;
import static se.leap.bitmaskclient.Constants.LOCATIONS;
import static se.leap.bitmaskclient.Constants.NAME;
import static se.leap.bitmaskclient.Constants.OPENVPN_CONFIGURATION;
import static se.leap.bitmaskclient.Constants.TIMEZONE;
import static se.leap.bitmaskclient.Constants.VERSION;

/**
 * Gateway provides objects defining gateways and their metadata.
 * Each instance contains a VpnProfile for OpenVPN specific data and member
 * variables describing capabilities and location (name)
 *
 * @author Sean Leonard <meanderingcode@aetherislands.net>
 * @author Parménides GV <parmegv@sdf.org>
 * @author cyberta
 */
public class Gateway {

    public final static String TAG = Gateway.class.getSimpleName();
    public final static String KEY_N_CLOSEST_GATEWAY = "N_CLOSEST_GATEWAY";

    private JSONObject generalConfiguration;
    private JSONObject secrets;
    private JSONObject gateway;

    private String name;
    private int timezone;
    private int apiVersion;
    private HashMap<Connection.TransportType, VpnProfile> vpnProfiles;

    /**
     * Build a gateway object from a JSON OpenVPN gateway definition in eip-service.json
     * and create a VpnProfile belonging to it.
     */
    public Gateway(JSONObject eipDefinition, JSONObject secrets, JSONObject gateway, Context context)
            throws ConfigParser.ConfigParseError, JSONException, IOException {

        this.gateway = gateway;
        this.secrets = secrets;

        generalConfiguration = getGeneralConfiguration(eipDefinition);
        timezone = getTimezone(eipDefinition);
        name = locationAsName(eipDefinition);
        apiVersion = getApiVersion(eipDefinition);
        vpnProfiles = createVPNProfiles(context);
    }

    private void addProfileInfos(Context context, HashMap<Connection.TransportType, VpnProfile> profiles) {
        Set<String> excludedAppsVpn = PreferenceHelper.getExcludedApps(context);
        for (VpnProfile profile : profiles.values()) {
            profile.mName = name;
            profile.mGatewayIp = gateway.optString(IP_ADDRESS);
            if (excludedAppsVpn != null) {
                profile.mAllowedAppsVpn = new HashSet<>(excludedAppsVpn);
            }
        }
    }

    private JSONObject getGeneralConfiguration(JSONObject eipDefinition) {
        try {
            return eipDefinition.getJSONObject(OPENVPN_CONFIGURATION);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private int getTimezone(JSONObject eipDefinition) {
        JSONObject location = getLocationInfo(eipDefinition);
        return location.optInt(TIMEZONE);
    }

    private int getApiVersion(JSONObject eipDefinition) {
        return eipDefinition.optInt(VERSION);
    }

    public String getRemoteIP() {
        return gateway.optString(IP_ADDRESS);
    }

    public String getHost() {
        return gateway.optString(HOST);
    }

    private String locationAsName(JSONObject eipDefinition) {
        JSONObject location = getLocationInfo(eipDefinition);
        return location.optString(NAME);
    }

    private JSONObject getLocationInfo(JSONObject eipDefinition) {
        try {
            JSONObject locations = eipDefinition.getJSONObject(LOCATIONS);

            return locations.getJSONObject(gateway.getString(LOCATION));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    /**
     * Create and attach the VpnProfile to our gateway object
     */
    private @NonNull HashMap<Connection.TransportType, VpnProfile> createVPNProfiles(Context context)
            throws ConfigParser.ConfigParseError, IOException, JSONException {
        VpnConfigGenerator vpnConfigurationGenerator = new VpnConfigGenerator(generalConfiguration, secrets, gateway, apiVersion);
        HashMap<Connection.TransportType, VpnProfile> profiles = vpnConfigurationGenerator.generateVpnProfiles();
        addProfileInfos(context, profiles);
        return profiles;
    }

    public String getName() {
        return name;
    }

    public HashMap<Connection.TransportType, VpnProfile> getProfiles() {
        return vpnProfiles;
    }

    public VpnProfile getProfile(Connection.TransportType transportType) {
        return vpnProfiles.get(transportType);
    }

    public boolean suppoortsTransport(Connection.TransportType transportType) {
        return vpnProfiles.get(transportType) != null;
    }

    public int getTimezone() {
        return timezone;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this, Gateway.class);
    }

}
