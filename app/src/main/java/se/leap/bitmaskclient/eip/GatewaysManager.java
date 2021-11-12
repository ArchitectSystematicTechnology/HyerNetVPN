/**
 * Copyright (c) 2013 - 2019 LEAP Encryption Access Project and contributors
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
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.VpnStatus;
import de.blinkt.openvpn.core.connection.Connection;
import se.leap.bitmaskclient.base.models.Location;
import se.leap.bitmaskclient.base.models.Provider;
import se.leap.bitmaskclient.base.models.ProviderObservable;
import se.leap.bitmaskclient.base.utils.PreferenceHelper;

import static de.blinkt.openvpn.core.connection.Connection.TransportType.OBFS4;
import static de.blinkt.openvpn.core.connection.Connection.TransportType.OPENVPN;
import static se.leap.bitmaskclient.base.models.Constants.GATEWAYS;
import static se.leap.bitmaskclient.base.models.Constants.HOST;
import static se.leap.bitmaskclient.base.models.Constants.PROVIDER_PRIVATE_KEY;
import static se.leap.bitmaskclient.base.models.Constants.PROVIDER_VPN_CERTIFICATE;
import static se.leap.bitmaskclient.base.models.Constants.SORTED_GATEWAYS;
import static se.leap.bitmaskclient.base.utils.PreferenceHelper.getPreferredCity;
import static se.leap.bitmaskclient.base.utils.PreferenceHelper.getUseBridges;

/**
 * @author parmegv
 */
public class GatewaysManager {

    private static final String TAG = GatewaysManager.class.getSimpleName();

    private Context context;
    private LinkedHashMap<String, Gateway> gateways = new LinkedHashMap<>();
    private Type listType = new TypeToken<ArrayList<Gateway>>() {}.getType();
    private ArrayList<Gateway> presortedList = new ArrayList<>();

    public GatewaysManager(Context context) {
        this.context = context;
        configureFromCurrentProvider();
    }

    /**
     * select closest Gateway
      * @return the n closest Gateway
     */
    public Gateway select(int nClosest) {
        String selectedCity = getPreferredCity(context);
        return select(nClosest, selectedCity);
    }

    public Gateway select(int nClosest, String city) {
        Connection.TransportType transportType = getUseBridges(context) ? OBFS4 : OPENVPN;
        if (presortedList.size() > 0) {
            return getGatewayFromPresortedList(nClosest, transportType, city);
        }

        return getGatewayFromTimezoneCalculation(nClosest, transportType, city);
    }

    public List<Location> getGatewayLocations() {
        String selectedCity = PreferenceHelper.getPreferredCity(context);
        HashMap<String, Integer> locationNames = new HashMap<>();
        ArrayList<Location> locations = new ArrayList<>();
        int n = 0;
        Gateway gateway;
        while ((gateway = select(n, null)) != null) {
            if (!locationNames.containsKey(gateway.getName())) {
                locationNames.put(gateway.getName(), locations.size());
                Location location = new Location(
                        gateway.getName(),
                        gateway.getFullness(),
                        1,
                        gateway.getName().equals(selectedCity));
                locations.add(location);
            } else {
                int index = locationNames.get(gateway.getName());
                Location location = locations.get(index);
                location.averageLoad = (location.numberOfGateways * location.averageLoad + gateway.getFullness()) / (location.numberOfGateways + 1);
                location.numberOfGateways += 1;
                locations.set(index, location);
            }
            n++;
        }

        return locations;
    }

    private Gateway getGatewayFromTimezoneCalculation(int nClosest, Connection.TransportType transportType, @Nullable String city) {
        List<Gateway> list = new ArrayList<>(gateways.values());
        GatewaySelector gatewaySelector = new GatewaySelector(list);
        Gateway gateway;
        int found  = 0;
        int i = 0;
        while ((gateway = gatewaySelector.select(i)) != null) {
            if ((city == null && gateway.supportsTransport(transportType)) ||
                    (gateway.getName().equals(city) && gateway.supportsTransport(transportType))) {
                if (found == nClosest) {
                    return gateway;
                }
                found++;
            }
            i++;
        }
        return null;
    }

    private Gateway getGatewayFromPresortedList(int nClosest, Connection.TransportType transportType, @Nullable String city) {
        int found = 0;
        for (Gateway gateway : presortedList) {
            if ((city == null && gateway.supportsTransport(transportType)) ||
                    (gateway.getName().equals(city) && gateway.supportsTransport(transportType))) {
                if (found == nClosest) {
                    return gateway;
                }
                found++;
            }
        }
        return null;
    }

    /**
     * Get position of the gateway from a sorted set (along the distance of the gw to your time zone)
     * @param profile profile belonging to a gateway
     * @return position of the gateway owning to the profile
     */
    public int getPosition(VpnProfile profile) {
        if (presortedList.size() > 0) { 
            return getPositionFromPresortedList(profile);
        } 
        
        return getPositionFromTimezoneCalculatedList(profile);
    }
    
    private int getPositionFromPresortedList(VpnProfile profile) {
        Connection.TransportType transportType = profile.mUsePluggableTransports ? OBFS4 : OPENVPN;
        int nClosest = 0;
        for (Gateway gateway : presortedList) {
            if (gateway.supportsTransport(transportType)) {
                if (profile.equals(gateway.getProfile(transportType))) {
                    return nClosest;
                }
                nClosest++;
            }
        }
        return -1;
    }
    
    private int getPositionFromTimezoneCalculatedList(VpnProfile profile) {
        Connection.TransportType transportType = profile.mUsePluggableTransports ? OBFS4 : OPENVPN;
        GatewaySelector gatewaySelector = new GatewaySelector(new ArrayList<>(gateways.values()));
        Gateway gateway;
        int nClosest = 0;
        int i = 0;
        while ((gateway = gatewaySelector.select(i)) != null) {
            if (gateway.supportsTransport(transportType)) {
                if (profile.equals(gateway.getProfile(transportType))) {
                    return nClosest;
                }
                nClosest++;
            }
            i++;
        }
        return -1;
    }

    /**
     * check if there are no gateways defined
     * @return true if no gateways defined else false
     */
    public boolean isEmpty() {
        return gateways.isEmpty();
    }

    /**
     * @return number of gateways defined in the GatewaysManager
     */
    public int size() {
        return gateways.size();
    }

    @Override
    public String toString() {
        return new Gson().toJson(gateways, listType);
    }

    /**
     * parse gateways from Provider's eip service
     * @param provider
     */
     private void parseDefaultGateways(Provider provider) {
         try {
             JSONObject eipDefinition = provider.getEipServiceJson();
             JSONObject secrets = secretsConfigurationFromCurrentProvider();

             JSONArray gatewaysDefined = new JSONArray();
             try {
                 gatewaysDefined = eipDefinition.getJSONArray(GATEWAYS);
             } catch (Exception e) {
                 e.printStackTrace();
             }

             for (int i = 0; i < gatewaysDefined.length(); i++) {
                 try {
                     JSONObject gw = gatewaysDefined.getJSONObject(i);
                     Gateway aux = new Gateway(eipDefinition, secrets, gw, this.context);
                     if (gateways.get(aux.getHost()) == null) {
                         addGateway(aux);
                     }
                 } catch (JSONException | ConfigParser.ConfigParseError | IOException e) {
                     e.printStackTrace();
                     VpnStatus.logError("Unable to parse gateway config!");
                 }
             }
         } catch (NullPointerException npe) {
             npe.printStackTrace();
         }
    }

    private void parseSimpleGatewayList(Provider provider) {
         try {
             JSONObject geoIpJson = provider.getGeoIpJson();
             JSONArray gatewaylist = geoIpJson.getJSONArray(GATEWAYS);

             for (int i = 0; i < gatewaylist.length(); i++) {
                 try {
                     String key = gatewaylist.getString(i);
                     if (gateways.containsKey(key)) {
                         presortedList.add(gateways.get(key));
                     }
                 } catch (JSONException e) {
                     e.printStackTrace();
                 }
             }
         } catch (NullPointerException | JSONException npe) {
             Log.d(TAG, "No valid geoip json found: " + npe.getLocalizedMessage());
         }
    }

    private boolean hasSortedGatewaysWithLoad(@Nullable Provider provider) {
         if (provider == null) {
             return false;
         }
         JSONObject geoIpJson = provider.getGeoIpJson();
         return geoIpJson.has(SORTED_GATEWAYS);
    }

    private void parseGatewaysWithLoad(Provider provider) {
        try {
            JSONObject geoIpJson = provider.getGeoIpJson();
            JSONArray gatewaylist = geoIpJson.getJSONArray(SORTED_GATEWAYS);
            for (int i = 0; i < gatewaylist.length(); i++) {
                try {
                    JSONObject load = gatewaylist.getJSONObject(i);
                    String hostName = load.getString(HOST);
                    if (gateways.containsKey(hostName)) {
                        Gateway gateway = gateways.get(hostName);
                        gateway.updateLoad(load);
                        presortedList.add(gateway);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        } catch (NullPointerException | JSONException npe) {
            npe.printStackTrace();
        }
    }

    private JSONObject secretsConfigurationFromCurrentProvider() {
        JSONObject result = new JSONObject();
        Provider provider = ProviderObservable.getInstance().getCurrentProvider();

        try {
            result.put(Provider.CA_CERT, provider.getCaCert());
            result.put(PROVIDER_PRIVATE_KEY, provider.getPrivateKey());
            result.put(PROVIDER_VPN_CERTIFICATE, provider.getVpnCertificate());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }

    private void addGateway(Gateway gateway) {
        gateways.put(gateway.getHost(), gateway);
    }

    private void configureFromCurrentProvider() {
         Provider provider = ProviderObservable.getInstance().getCurrentProvider();
         parseDefaultGateways(provider);
         if (hasSortedGatewaysWithLoad(provider)) {
             parseGatewaysWithLoad(provider);
         } else {
             parseSimpleGatewayList(provider);
         }

    }
}
