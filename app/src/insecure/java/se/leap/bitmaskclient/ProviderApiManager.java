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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Scanner;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import se.leap.bitmaskclient.eip.EIP;

import static android.text.TextUtils.isEmpty;
import static se.leap.bitmaskclient.DownloadFailedDialog.DOWNLOAD_ERRORS.ERROR_CERTIFICATE_PINNING;
import static se.leap.bitmaskclient.ProviderAPI.ERRORS;
import static se.leap.bitmaskclient.ProviderAPI.RESULT_KEY;
import static se.leap.bitmaskclient.R.string.certificate_error;
import static se.leap.bitmaskclient.R.string.malformed_url;
import static se.leap.bitmaskclient.R.string.warning_corrupted_provider_cert;

/**
 * Created by cyberta on 04.01.18.
 */

public class ProviderApiManager extends ProviderApiManagerBase {
    protected static boolean lastDangerOn = true;


    public ProviderApiManager(SharedPreferences preferences, Resources resources, OkHttpClientGenerator clientGenerator, ProviderApiServiceCallback callback) {
        super(preferences, resources, clientGenerator, callback);
    }

    public static boolean lastDangerOn() {
        return lastDangerOn;
    }

    /**
     * Downloads a provider.json from a given URL, adding a new provider using the given name.
     *
     * @param task containing a boolean meaning if the provider is custom or not, another boolean meaning if the user completely trusts this provider, the provider name and its provider.json url.
     * @return a bundle with a boolean value mapped to a key named RESULT_KEY, and which is true if the update was successful.
     */
    @Override
    protected Bundle setUpProvider(Bundle task) {
        int progress = 0;
        Bundle currentDownload = new Bundle();

        if (task != null) {
            lastDangerOn = task.containsKey(ProviderListContent.ProviderItem.DANGER_ON) && task.getBoolean(ProviderListContent.ProviderItem.DANGER_ON);
            lastProviderMainUrl = task.containsKey(Provider.MAIN_URL) ?
                    task.getString(Provider.MAIN_URL) :
                    "";

            if (isEmpty(lastProviderMainUrl)) {
                setErrorResult(currentDownload, malformed_url, null);
                return currentDownload;
            }

            providerCaCertFingerprint = task.containsKey(Provider.CA_CERT_FINGERPRINT) ?
                    task.getString(Provider.CA_CERT_FINGERPRINT) :
                    "";
            providerCaCert = task.containsKey(Provider.CA_CERT) ?
                    task.getString(Provider.CA_CERT) :
                    "";

            try {
                providerDefinition = task.containsKey(Provider.KEY) ?
                        new JSONObject(task.getString(Provider.KEY)) :
                        new JSONObject();
            } catch (JSONException e) {
                e.printStackTrace();
                providerDefinition = new JSONObject();
            }
            providerApiUrl = getApiUrlWithVersion(providerDefinition);

            checkPersistedProviderUpdates();
            currentDownload = validateProviderDetails();

            //provider details invalid
            if (currentDownload.containsKey(ERRORS)) {
                return currentDownload;
            }

            //no provider certificate available
            if (currentDownload.containsKey(RESULT_KEY) && !currentDownload.getBoolean(RESULT_KEY)) {
                resetProviderDetails();
            }

            EIP_SERVICE_JSON_DOWNLOADED = false;
            go_ahead = true;
        }

        if (!PROVIDER_JSON_DOWNLOADED)
            currentDownload = getAndSetProviderJson(lastProviderMainUrl, lastDangerOn, providerCaCert, providerDefinition);
        if (PROVIDER_JSON_DOWNLOADED || (currentDownload.containsKey(RESULT_KEY) && currentDownload.getBoolean(RESULT_KEY))) {
            broadcastProgress(progress++);
            PROVIDER_JSON_DOWNLOADED = true;

            if (!CA_CERT_DOWNLOADED)
                currentDownload = downloadCACert(lastDangerOn);
            if (CA_CERT_DOWNLOADED || (currentDownload.containsKey(RESULT_KEY) && currentDownload.getBoolean(RESULT_KEY))) {
                broadcastProgress(progress++);
                CA_CERT_DOWNLOADED = true;
                currentDownload = getAndSetEipServiceJson();
                if (currentDownload.containsKey(RESULT_KEY) && currentDownload.getBoolean(RESULT_KEY)) {
                    broadcastProgress(progress++);
                    EIP_SERVICE_JSON_DOWNLOADED = true;
                }
            }
        }

        return currentDownload;
    }

    private Bundle getAndSetProviderJson(String providerMainUrl, boolean dangerOn, String caCert, JSONObject providerDefinition) {
        Bundle result = new Bundle();

        if (go_ahead) {
            String providerDotJsonString;
            if(providerDefinition.length() == 0 || caCert.isEmpty())
                providerDotJsonString = downloadWithCommercialCA(providerMainUrl + "/provider.json", dangerOn);
            else
                providerDotJsonString = downloadFromApiUrlWithProviderCA("/provider.json", caCert, providerDefinition, dangerOn);

            if (!isValidJson(providerDotJsonString)) {
                result.putString(ERRORS, resources.getString(malformed_url));
                result.putBoolean(RESULT_KEY, false);
                return result;
            }

            try {
                JSONObject providerJson = new JSONObject(providerDotJsonString);
                String providerDomain = getDomainFromMainURL(lastProviderMainUrl);
                providerApiUrl = getApiUrlWithVersion(providerJson);
                //String name = providerJson.getString(Provider.NAME);
                //TODO setProviderName(name);

                preferences.edit().putString(Provider.KEY, providerJson.toString()).
                        putBoolean(Constants.PROVIDER_ALLOW_ANONYMOUS, providerJson.getJSONObject(Provider.SERVICE).getBoolean(Constants.PROVIDER_ALLOW_ANONYMOUS)).
                        putBoolean(Constants.PROVIDER_ALLOWED_REGISTERED, providerJson.getJSONObject(Provider.SERVICE).getBoolean(Constants.PROVIDER_ALLOWED_REGISTERED)).
                        putString(Provider.KEY + "." + providerDomain, providerJson.toString()).commit();
                result.putBoolean(RESULT_KEY, true);
            } catch (JSONException e) {
                String reason_to_fail = pickErrorMessage(providerDotJsonString);
                result.putString(ERRORS, reason_to_fail);
                result.putBoolean(RESULT_KEY, false);
            }
        }
        return result;
    }

    /**
     * Downloads the eip-service.json from a given URL, and saves eip service capabilities including the offered gateways
     * @return a bundle with a boolean value mapped to a key named RESULT_KEY, and which is true if the download was successful.
     */
    @Override
    protected Bundle getAndSetEipServiceJson() {
        Bundle result = new Bundle();
        String eip_service_json_string = "";
        if (go_ahead) {
            try {
                JSONObject provider_json = new JSONObject(preferences.getString(Provider.KEY, ""));
                String eip_service_url = provider_json.getString(Provider.API_URL) + "/" + provider_json.getString(Provider.API_VERSION) + "/" + EIP.SERVICE_API_PATH;
                eip_service_json_string = downloadWithProviderCA(eip_service_url, lastDangerOn);
                JSONObject eip_service_json = new JSONObject(eip_service_json_string);
                eip_service_json.getInt(Provider.API_RETURN_SERIAL);

                preferences.edit().putString(Constants.PROVIDER_KEY, eip_service_json.toString()).commit();

                result.putBoolean(RESULT_KEY, true);
            } catch (NullPointerException | JSONException e) {
                String reason_to_fail = pickErrorMessage(eip_service_json_string);
                result.putString(ERRORS, reason_to_fail);
                result.putBoolean(RESULT_KEY, false);
            }
        }
        return result;
    }

    /**
     * Downloads a new OpenVPN certificate, attaching authenticated cookie for authenticated certificate.
     *
     * @return true if certificate was downloaded correctly, false if provider.json is not present in SharedPreferences, or if the certificate url could not be parsed as a URI, or if there was an SSL error.
     */
    @Override
    protected boolean updateVpnCertificate() {
        try {
            JSONObject provider_json = new JSONObject(preferences.getString(Provider.KEY, ""));

            String provider_main_url = provider_json.getString(Provider.API_URL);
            URL new_cert_string_url = new URL(provider_main_url + "/" + provider_json.getString(Provider.API_VERSION) + "/" + Constants.PROVIDER_VPN_CERTIFICATE);

            String cert_string = downloadWithProviderCA(new_cert_string_url.toString(), lastDangerOn);

            if (cert_string == null || cert_string.isEmpty() || ConfigHelper.checkErroneousDownload(cert_string))
                return false;
            else
                return loadCertificate(cert_string);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }
    }


    private Bundle downloadCACert(boolean dangerOn) {
        Bundle result = new Bundle();
        try {
            JSONObject providerJson = new JSONObject(preferences.getString(Provider.KEY, ""));
            String caCertUrl = providerJson.getString(Provider.CA_CERT_URI);
            String providerDomain = providerJson.getString(Provider.DOMAIN);

            String certString = downloadWithCommercialCA(caCertUrl, dangerOn);

            if (validCertificate(certString) && go_ahead) {
                preferences.edit().putString(Provider.CA_CERT, certString).commit();
                preferences.edit().putString(Provider.CA_CERT + "." + providerDomain, certString).commit();
                result.putBoolean(RESULT_KEY, true);
            } else {
                setErrorResult(result, warning_corrupted_provider_cert, ERROR_CERTIFICATE_PINNING.toString());
            }
        } catch (JSONException e) {
            setErrorResult(result, malformed_url, null);
        }

        return result;
    }

    /**
     * Tries to download the contents of the provided url using commercially validated CA certificate from chosen provider.
     * <p/>
     * If danger_on flag is true, SSL exceptions will be managed by futher methods that will try to use some bypass methods.
     *
     * @param string_url
     * @param danger_on  if the user completely trusts this provider
     * @return
     */
    private String downloadWithCommercialCA(String string_url, boolean danger_on) {
        String responseString;
        JSONObject errorJson = new JSONObject();

        OkHttpClient okHttpClient = clientGenerator.initCommercialCAHttpClient(errorJson);
        if (okHttpClient == null) {
            return errorJson.toString();
        }

        List<Pair<String, String>> headerArgs = getAuthorizationHeader();

        responseString = sendGetStringToServer(string_url, headerArgs, okHttpClient);

        if (responseString != null && responseString.contains(ERRORS)) {
            try {
                // try to download with provider CA on certificate error
                JSONObject responseErrorJson = new JSONObject(responseString);
                if (danger_on && responseErrorJson.getString(ERRORS).equals(resources.getString(R.string.certificate_error))) {
                    responseString = downloadWithoutCA(string_url);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return responseString;
    }

    private String downloadFromApiUrlWithProviderCA(String path, String caCert, JSONObject providerDefinition, boolean dangerOn) {
        String responseString;
        JSONObject errorJson = new JSONObject();
        String baseUrl = getApiUrl(providerDefinition);
        OkHttpClient okHttpClient = clientGenerator.initSelfSignedCAHttpClient(errorJson, caCert);
        if (okHttpClient == null) {
            return errorJson.toString();
        }

        String urlString = baseUrl + path;
        List<Pair<String, String>> headerArgs = getAuthorizationHeader();
        responseString = sendGetStringToServer(urlString, headerArgs, okHttpClient);

        if (responseString != null && responseString.contains(ERRORS)) {
            try {
                // try to download with provider CA on certificate error
                JSONObject responseErrorJson = new JSONObject(responseString);
                if (dangerOn && responseErrorJson.getString(ERRORS).equals(resources.getString(R.string.certificate_error))) {
                    responseString = downloadWithCommercialCA(urlString, dangerOn);
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
     * @param urlString as a string
     * @param dangerOn  true to download CA certificate in case it has not been downloaded.
     * @return an empty string if it fails, the url content if not.
     */
    private String downloadWithProviderCA(String urlString, boolean dangerOn) {
        JSONObject initError = new JSONObject();
        String responseString;

        OkHttpClient okHttpClient = clientGenerator.initSelfSignedCAHttpClient(initError);
        if (okHttpClient == null) {
            return initError.toString();
        }

        List<Pair<String, String>> headerArgs = getAuthorizationHeader();

        responseString = sendGetStringToServer(urlString, headerArgs, okHttpClient);

        if (responseString.contains(ERRORS)) {
            try {
                // danger danger: try to download without CA on certificate error
                JSONObject responseErrorJson = new JSONObject(responseString);
                if (dangerOn && responseErrorJson.getString(ERRORS).equals(resources.getString(R.string.certificate_error))) {
                    responseString = downloadWithoutCA(urlString);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return responseString;
    }

    /**
     * Downloads the string that's in the url with any certificate.
     */
    // This method is totally insecure anyways. So no need to refactor that in order to use okHttpClient, force modern TLS etc.. DO NOT USE IN PRODUCTION!
    private String downloadWithoutCA(String url_string) {
        String string = "";
        try {

            HostnameVerifier hostnameVerifier = new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };

            class DefaultTrustManager implements X509TrustManager {

                @Override
                public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            }

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(new KeyManager[0], new TrustManager[]{new DefaultTrustManager()}, new SecureRandom());

            URL url = new URL(url_string);
            HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setSSLSocketFactory(context.getSocketFactory());
            urlConnection.setHostnameVerifier(hostnameVerifier);
            string = new Scanner(urlConnection.getInputStream()).useDelimiter("\\A").next();
            System.out.println("String ignoring certificate = " + string);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            string = formatErrorMessage(malformed_url);
        } catch (IOException e) {
            // The downloaded certificate doesn't validate our https connection.
            e.printStackTrace();
            string = formatErrorMessage(certificate_error);
        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (KeyManagementException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return string;
    }
}
