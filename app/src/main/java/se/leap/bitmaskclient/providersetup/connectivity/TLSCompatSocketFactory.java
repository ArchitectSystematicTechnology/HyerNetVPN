package se.leap.bitmaskclient.providersetup.connectivity;

import android.text.TextUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import se.leap.bitmaskclient.base.utils.ConfigHelper;

/**
 * Created by cyberta on 24.10.17.
 * This class ensures that modern TLS algorithms will also be used on old devices
 */

public class TLSCompatSocketFactory extends SSLSocketFactory {

    private static final String TAG = TLSCompatSocketFactory.class.getName();
    private SSLSocketFactory internalSSLSocketFactory;
    private TrustManager trustManager;

    public TLSCompatSocketFactory(String trustedCaCert) throws KeyManagementException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, NoSuchProviderException {
        initForSelfSignedCAs(trustedCaCert);
    }

    public TLSCompatSocketFactory() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, NoSuchProviderException, IOException {
        initForCommercialCAs();
    }

    public void initSSLSocketFactory(OkHttpClient.Builder builder) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, KeyManagementException, IllegalStateException {
        builder.sslSocketFactory(this, (X509TrustManager) trustManager);
    }


    private void initForSelfSignedCAs(String trustedSelfSignedCaCert) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, IllegalStateException, KeyManagementException, NoSuchProviderException {
        // Create a KeyStore containing our trusted CAs
        String defaultType = KeyStore.getDefaultType();
        KeyStore keyStore = KeyStore.getInstance(defaultType);
        keyStore.load(null, null);
        if (!TextUtils.isEmpty(trustedSelfSignedCaCert)) {
            ArrayList<X509Certificate> x509Certificates = ConfigHelper.parseX509CertificatesFromString(trustedSelfSignedCaCert);
            if (x509Certificates != null) {
                for (int i = 0; i < x509Certificates.size(); i++) {
                    keyStore.setCertificateEntry("provider_ca_certificate"+i, x509Certificates.get(i));
                }
            }
        }

        // Create a TrustManager that trusts the CAs in our KeyStore
        String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
        tmf.init(keyStore);

        // Check if there's only 1 X509Trustmanager -> from okttp3 source code example
        TrustManager[] trustManagers = tmf.getTrustManagers();
        if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
            throw new IllegalStateException("Unexpected default trust managers:"
                    + Arrays.toString(trustManagers));
        }

        trustManager = trustManagers[0];

        // Create a SSLContext that uses our TrustManager
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);
        internalSSLSocketFactory = sslContext.getSocketFactory();

    }


    private void initForCommercialCAs() throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {

        // Create a TrustManager that trusts the CAs in our KeyStore
        String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
        tmf.init((KeyStore) null);

        // Check if there's only 1 X509Trustmanager -> from okttp3 source code example
        TrustManager[] trustManagers = tmf.getTrustManagers();
        if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
            throw new IllegalStateException("Unexpected default trust managers:"
                    + Arrays.toString(trustManagers));
        }

        trustManager = trustManagers[0];

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, null);
        internalSSLSocketFactory = context.getSocketFactory();
    }


    @Override
    public String[] getDefaultCipherSuites() {
        return internalSSLSocketFactory.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return internalSSLSocketFactory.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException, IllegalArgumentException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket());
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException, IllegalArgumentException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException, IllegalArgumentException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException, IllegalArgumentException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException, IllegalArgumentException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException, IllegalArgumentException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(address, port, localAddress, localPort));
    }

    private Socket enableTLSOnSocket(Socket socket) throws IllegalArgumentException {
        if((socket instanceof SSLSocket)) {
            ((SSLSocket)socket).setEnabledProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
        }
        return socket;


    }



}
