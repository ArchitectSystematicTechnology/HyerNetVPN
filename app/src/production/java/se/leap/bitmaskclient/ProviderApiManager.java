/**
 * Copyright (c) 2018 LEAP Encryption Access Project and contributers
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

package se.leap.bitmaskclient;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Pair;

import androidx.multidex.BuildConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import de.blinkt.openvpn.core.VpnStatus;
import okhttp3.OkHttpClient;
import se.leap.bitmaskclient.eip.EIP;
import se.leap.bitmaskclient.utils.ConfigHelper;

import static android.text.TextUtils.isEmpty;
import static se.leap.bitmaskclient.Constants.BROADCAST_RESULT_KEY;
import static se.leap.bitmaskclient.Constants.PROVIDER_KEY;
import static se.leap.bitmaskclient.Constants.PROVIDER_VPN_CERTIFICATE;
import static se.leap.bitmaskclient.ProviderAPI.ERRORS;
import static se.leap.bitmaskclient.ProviderSetupFailedDialog.DOWNLOAD_ERRORS.ERROR_CERTIFICATE_PINNING;
import static se.leap.bitmaskclient.ProviderSetupFailedDialog.DOWNLOAD_ERRORS.ERROR_CORRUPTED_PROVIDER_JSON;
import static se.leap.bitmaskclient.R.string.downloading_vpn_certificate_failed;
import static se.leap.bitmaskclient.R.string.error_io_exception_user_message;
import static se.leap.bitmaskclient.R.string.malformed_url;
import static se.leap.bitmaskclient.R.string.setup_error_text;
import static se.leap.bitmaskclient.R.string.warning_corrupted_provider_cert;
import static se.leap.bitmaskclient.R.string.warning_corrupted_provider_details;
import static se.leap.bitmaskclient.utils.ConfigHelper.getProviderFormattedString;

/**
 * Implements the logic of the provider api http requests. The methods of this class need to be called from
 * a background thread.
 */


public class ProviderApiManager extends ProviderApiManagerBase {

    private static final String TAG = ProviderApiManager.class.getSimpleName();

    public ProviderApiManager(SharedPreferences preferences, Resources resources, OkHttpClientGenerator clientGenerator, ProviderApiServiceCallback callback) {
        super(preferences, resources, clientGenerator, callback);
    }

    /**
     * Only used in insecure flavor.
     */
    static boolean lastDangerOn() {
        return false;
    }

    /**
     * Downloads a provider.json from a given URL, adding a new provider using the given name.
     *
     * @param task containing a boolean meaning if the provider is custom or not, another boolean meaning if the user completely trusts this provider, the provider name and its provider.json url.
     * @return a bundle with a boolean value mapped to a key named BROADCAST_RESULT_KEY, and which is true if the update was successful.
     */
    @Override
    protected Bundle setUpProvider(Provider provider, Bundle task) {
        Bundle currentDownload = new Bundle();

        if (isEmpty(provider.getMainUrlString()) || provider.getMainUrl().isDefault()) {
            currentDownload.putBoolean(BROADCAST_RESULT_KEY, false);
            setErrorResult(currentDownload, malformed_url, null);
            return currentDownload;
        }

        getPersistedProviderUpdates(provider);
        currentDownload = validateProviderDetails(provider);

        //provider certificate invalid
        if (currentDownload.containsKey(ERRORS)) {
            currentDownload.putParcelable(PROVIDER_KEY, provider);
            return currentDownload;
        }

        //no provider json or certificate available
        if (currentDownload.containsKey(BROADCAST_RESULT_KEY) && !currentDownload.getBoolean(BROADCAST_RESULT_KEY)) {
            resetProviderDetails(provider);
        }

        currentDownload = getAndSetProviderJson(provider);
        if (provider.hasDefinition() || (currentDownload.containsKey(BROADCAST_RESULT_KEY) && currentDownload.getBoolean(BROADCAST_RESULT_KEY))) {
            if (!provider.hasCaCert()) {
                currentDownload = downloadCACert(provider);
            }
            if (provider.hasCaCert() || (currentDownload.containsKey(BROADCAST_RESULT_KEY) && currentDownload.getBoolean(BROADCAST_RESULT_KEY))) {
                currentDownload = getAndSetEipServiceJson(provider);
            }

            if (provider.hasEIP() && !provider.allowsRegistered() && !provider.allowsAnonymous()) {
                setErrorResult(currentDownload, setup_error_text, null);
            }
        }

        return currentDownload;
    }


    private Bundle getAndSetProviderJson(Provider provider) {
        Bundle result = new Bundle();

        String providerDotJsonString;
        if(provider.getDefinitionString().length() == 0 || provider.getCaCert().isEmpty()) {
            String providerJsonUrl = provider.getMainUrlString() + "/provider.json";
            providerDotJsonString = downloadWithCommercialCA(providerJsonUrl, provider);
        } else {
            providerDotJsonString = downloadFromApiUrlWithProviderCA("/provider.json", provider);
        }

        if (ConfigHelper.checkErroneousDownload(providerDotJsonString) || !isValidJson(providerDotJsonString)) {
            setErrorResult(result, malformed_url, null);
            return result;
        }

        if (BuildConfig.DEBUG) {
            VpnStatus.logDebug("PROVIDER JSON: " + providerDotJsonString);
        }
        try {
            JSONObject providerJson = new JSONObject(providerDotJsonString);

            if (provider.define(providerJson)) {
                result.putBoolean(BROADCAST_RESULT_KEY, true);
            } else {
                return setErrorResult(result, warning_corrupted_provider_details, ERROR_CORRUPTED_PROVIDER_JSON.toString());
            }

        } catch (JSONException e) {
            setErrorResult(result, providerDotJsonString);
        }
        return result;
    }

    /**
     * Downloads the eip-service.json from a given URL, and saves eip service capabilities including the offered gateways
     * @return a bundle with a boolean value mapped to a key named BROADCAST_RESULT_KEY, and which is true if the download was successful.
     */
    @Override
    protected Bundle getAndSetEipServiceJson(Provider provider) {
        Bundle result = new Bundle();
        String eipServiceJsonString = "";
        try {
            String eipServiceUrl = provider.getApiUrlWithVersion() + "/" + EIP.SERVICE_API_PATH;
            eipServiceJsonString = downloadWithProviderCA(provider.getCaCert(), eipServiceUrl);
            JSONObject eipServiceJson = new JSONObject(eipServiceJsonString);
            if (BuildConfig.DEBUG) {
                VpnStatus.logDebug("EIP SERVICE JSON: " + eipServiceJsonString);
            }
            if (eipServiceJson.has(ERRORS)) {
                setErrorResult(result, eipServiceJsonString);
            } else {
                provider.setEipServiceJson(eipServiceJson);
                provider.setLastEipServiceUpdate(System.currentTimeMillis());
                result.putBoolean(BROADCAST_RESULT_KEY, true);
            }
        } catch (NullPointerException | JSONException e) {
            setErrorResult(result, eipServiceJsonString);
        }
        return result;
    }

    /**
     * Downloads a new OpenVPN certificate, attaching authenticated cookie for authenticated certificate.
     *
     * @return true if certificate was downloaded correctly, false if provider.json is not present in SharedPreferences, or if the certificate url could not be parsed as a URI, or if there was an SSL error.
     */
    @Override
    protected Bundle updateVpnCertificate(Provider provider) {
        Bundle result = new Bundle();
        try {
            URL newCertStringUrl = new URL(provider.getApiUrlWithVersion() + "/" + PROVIDER_VPN_CERTIFICATE);

            String certString = downloadWithProviderCA(provider.getCaCert(), newCertStringUrl.toString());
            if (BuildConfig.DEBUG) {
                VpnStatus.logDebug("VPN CERT: " + certString);
            }
            if (ConfigHelper.checkErroneousDownload(certString)) {
                if (certString == null || certString.isEmpty()) {
                    // probably 204
                    setErrorResult(result, error_io_exception_user_message, null);
                } else {
                    setErrorResult(result, certString);
                    return result;
                }
            }
            return loadCertificate(provider, certString);
        } catch (IOException e) {
            // TODO try to get Provider Json
            setErrorResult(result, downloading_vpn_certificate_failed, null);
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Fetches the geo ip Json, containing a list of gateways sorted by distance from the users current location.
     * Fetching is only allowed if the cache timeout of 1 h was reached, a valid geoip service URL exists and the
     * vpn is not yet active. The latter condition is needed in order to guarantee that the geoip service sees
     * the real ip of the client
     *
     * @param provider
     * @return
     */
    @Override
    protected Bundle getGeoIPJson(Provider provider) {
        Bundle result = new Bundle();

        if (!provider.shouldUpdateGeoIpJson() || provider.getGeoipUrl().isDefault() || VpnStatus.isVPNActive()) {
            result.putBoolean(BROADCAST_RESULT_KEY, false);
            return result;
        }

        try {
            URL geoIpUrl = provider.getGeoipUrl().getUrl();

            String geoipJsonString = downloadFromUrlWithProviderCA(geoIpUrl.toString(), provider);
            JSONObject geoipJson = new JSONObject(geoipJsonString);

            if (geoipJson.has(ERRORS)) {
                result.putBoolean(BROADCAST_RESULT_KEY, false);
            } else{
                provider.setGeoIpJson(geoipJson);
                provider.setLastGeoIpUpdate(System.currentTimeMillis());
                result.putBoolean(BROADCAST_RESULT_KEY, true);
            }

        } catch (JSONException | NullPointerException e) {
            result.putBoolean(BROADCAST_RESULT_KEY, false);
            e.printStackTrace();
        }
        return result;
    }


    private Bundle downloadCACert(Provider provider) {
        Bundle result = new Bundle();
        try {
            String caCertUrl = provider.getDefinition().getString(Provider.CA_CERT_URI);
            String providerDomain = getDomainFromMainURL(provider.getMainUrlString());
            String certString = downloadWithCommercialCA(caCertUrl, provider);

            if (validCertificate(provider, certString)) {
                provider.setCaCert(certString);
                preferences.edit().putString(Provider.CA_CERT + "." + providerDomain, certString).apply();
                if (BuildConfig.DEBUG) {
                    VpnStatus.logDebug("CA CERT: " + certString);
                }
                result.putBoolean(BROADCAST_RESULT_KEY, true);
            } else {
                setErrorResult(result, warning_corrupted_provider_cert, ERROR_CERTIFICATE_PINNING.toString());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Tries to download the contents of the provided url using commercially validated CA certificate from chosen provider.
     *
     */
    private String downloadWithCommercialCA(String stringUrl, Provider provider) {
        String responseString;
        JSONObject errorJson = new JSONObject();

        OkHttpClient okHttpClient = clientGenerator.initCommercialCAHttpClient(errorJson);
        if (okHttpClient == null) {
            return errorJson.toString();
        }

        List<Pair<String, String>> headerArgs = getAuthorizationHeader();

        responseString = sendGetStringToServer(stringUrl, headerArgs, okHttpClient);

        if (responseString != null && responseString.contains(ERRORS)) {
            try {
                // try to download with provider CA on certificate error
                JSONObject responseErrorJson = new JSONObject(responseString);
                if (responseErrorJson.getString(ERRORS).equals(getProviderFormattedString(resources, R.string.certificate_error))) {
                    responseString = downloadWithProviderCA(provider.getCaCert(), stringUrl);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return responseString;
    }


    /**
     * Tries to download the contents of the provided url using not commercially validated CA certificate from chosen provider.
     *
     * @return an empty string if it fails, the response body if not.
     */
    private String downloadFromApiUrlWithProviderCA(String path, Provider provider) {
        String baseUrl = provider.getApiUrlString();
        String urlString = baseUrl + path;
        return downloadFromUrlWithProviderCA(urlString, provider);
    }

    private String downloadFromUrlWithProviderCA(String urlString, Provider provider) {
        String responseString;
        JSONObject errorJson = new JSONObject();
        OkHttpClient okHttpClient = clientGenerator.initSelfSignedCAHttpClient(provider.getCaCert(), errorJson);
        if (okHttpClient == null) {
            return errorJson.toString();
        }

        List<Pair<String, String>> headerArgs = getAuthorizationHeader();
        responseString = sendGetStringToServer(urlString, headerArgs, okHttpClient);

        return responseString;
    }


        /**
         * Tries to download the contents of the provided url using not commercially validated CA certificate from chosen provider.
         *
         * @param urlString as a string
         * @return an empty string if it fails, the url content if not.
         */
    private String downloadWithProviderCA(String caCert, String urlString) {
        JSONObject initError = new JSONObject();
        String responseString;

        OkHttpClient okHttpClient = clientGenerator.initSelfSignedCAHttpClient(caCert, initError);
        if (okHttpClient == null) {
            return initError.toString();
        }

        List<Pair<String, String>> headerArgs = getAuthorizationHeader();

        responseString = sendGetStringToServer(urlString, headerArgs, okHttpClient);

        return responseString;
    }
}
