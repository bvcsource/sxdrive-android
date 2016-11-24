/*
 *  Copyright (C) 2012-2016 Skylable Ltd. <info-copyright@skylable.com>
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  Special exception for linking this software with OpenSSL:
 *
 *  In addition, as a special exception, Skylable Ltd. gives permission to
 *  link the code of this program with the OpenSSL library and distribute
 *  linked combinations including the two. You must obey the GNU General
 *  Public License in all respects for all of the code used other than
 *  OpenSSL. You may extend this exception to your version of the program,
 *  but you are not obligated to do so. If you do not wish to do so, delete
 *  this exception statement from your version.
 */
package com.skylable.sx.enterprise;

import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

// http://nelenkov.blogspot.it/2011/12/using-custom-certificate-trust-store-on.html
public class SxTrustManager implements X509TrustManager, HostnameVerifier {
    private static final String TAG = SxTrustManager.class.getSimpleName();
    private static final String SSL_PROTOCOL = "TLS";
    private static final String NODE_SXAUTHD = "SXAUTHD";
    private static final String APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
    private static final String APPLICATION_JSON = "application/json";
    private static final String PARAM_DISPLAY = "display";
    private static final String PARAM_UNIQUE = "unique";

    public static class SxTrustException extends RuntimeException {
        private static final long serialVersionUID = -1;

        public SxTrustException(String detailMessage) {
            super(detailMessage);
        }

        public SxTrustException(Throwable throwable) {
            super(throwable);
        }

        public SxTrustException(int intRes) {
            super(SxApp.getStringResource(intRes));
        }
    }

    public static class SxSelfSignedException extends RuntimeException {
        private static final long serialVersionUID = -1;
        private final X509Certificate[] mChain;

        public SxSelfSignedException(X509Certificate[] chain) {
            mChain = chain;
        }

        public X509Certificate[] getServerCertificates() {
            return mChain;
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    private final SSLContext mSSLCtx;
    private TrustManagerFactory mTMFactory;

    private URL mEndpoint;
    private String mBasicAuth;
    private String mPostData;

    public SxTrustManager() throws SxTrustException {
        try {
            mSSLCtx = SSLContext.getInstance(SSL_PROTOCOL);
            mSSLCtx.init(null, new TrustManager[]{this}, null);
            mTMFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            initKeyStore(null);
        } catch (Throwable e) {
            throw new SxTrustException(e);
        }
    }

    private X509TrustManager getDefaultTrustManager() {
        for (TrustManager tm : mTMFactory.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        throw new SxTrustException(R.string.error_default_trustmanager);
    }

    public SxTrustManager setEndpoint(URL endpoint) {
        mEndpoint = endpoint;
        return this;
    }

    public SxTrustManager setCredentials(String username, String password) {
        final String credentials = username + ":" + password;
        mBasicAuth = "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
        return this;
    }

    public SxTrustManager setDeviceInfo(String display, String unique) {
        mPostData = new Uri.Builder()
                .appendQueryParameter(PARAM_DISPLAY, display)
                .appendQueryParameter(PARAM_UNIQUE, unique)
                .build()
                .getEncodedQuery();
        return this;
    }

    public SxTrustManager clear() {
        mBasicAuth = null;
        mPostData = null;
        initKeyStore(null);
        return this;
    }

    public SxTrustManager initKeyStore(X509Certificate[] chain) {
        try {
            final KeyStore keystore;
            if (chain == null)
                keystore = null;
            else {
                keystore = KeyStore.getInstance(KeyStore.getDefaultType());
                keystore.load(null);
                for (int i = 0; i < chain.length; i++)
                    keystore.setCertificateEntry("selfsigned" + i, chain[i]);
            }
            mTMFactory.init(keystore);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    private X509Certificate[] mLastChain;

    public String create() throws Exception {
        BufferedReader in = null;
        BufferedWriter writer = null;
        try {
            HttpsURLConnection urlConnection = (HttpsURLConnection) mEndpoint.openConnection();
            urlConnection.setSSLSocketFactory(mSSLCtx.getSocketFactory());

            urlConnection.setInstanceFollowRedirects(false);
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", APPLICATION_X_WWW_FORM_URLENCODED);
            urlConnection.setRequestProperty("Authorization", mBasicAuth);
            urlConnection.setDoInput(true);
            urlConnection.setDoOutput(true);
            // wrap the hostname verifier to return a human readable error when it fails
            urlConnection.setHostnameVerifier(this);

            // indeed very ugly but apparently no other ultra-tricky way
            // to obtain non validating certificate
            mLastChain = null;
            try {
                writer = new BufferedWriter(new OutputStreamWriter(urlConnection.getOutputStream(), "UTF-8"));
            } catch (Exception e) {
                // check if we have collected certificate chain
                if (mLastChain != null) {
                    final SxSelfSignedException sse = new SxSelfSignedException(mLastChain);
                    mLastChain = null;
                    throw (sse); // throw our exception
                } else throw (e); // re-throw non handled exception
            }

            writer.write(mPostData);
            writer.flush();
            writer.close();

            final int responseCode = urlConnection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP)
                return urlConnection.getHeaderField("Location");

            if ((responseCode < 400) || (responseCode > 599) || !APPLICATION_JSON.equals(urlConnection.getContentType()))
                throw new SxTrustException(R.string.error_no_sxcluster);

            in = new BufferedReader(new InputStreamReader(urlConnection.getErrorStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null)
                response.append(inputLine);

            Log.e(TAG, "JSON server reply: " + response.toString());

            String ErrorMessage, NodeId;

            try {
                JSONObject reader = new JSONObject(response.toString());
                ErrorMessage = reader.getString("ErrorMessage");
                NodeId = reader.getString("NodeId");
            } catch (Exception e) {
                e.printStackTrace();
                throw new SxTrustException(R.string.error_no_sxcluster);
            }

            if (!NODE_SXAUTHD.equals(NodeId))
                ErrorMessage = SxApp.getStringResource(R.string.error_no_sxauthd_running);

            throw new SxTrustException(ErrorMessage);
        } finally {
            closeQuietly(in);
            closeQuietly(writer);
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        getDefaultTrustManager().checkClientTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        try {
            getDefaultTrustManager().checkServerTrusted(chain, authType);
        } catch (CertificateException e) {
            e.printStackTrace();
            mLastChain = chain;
            throw (e);
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return getDefaultTrustManager().getAcceptedIssuers();
    }

    // maybe ugly, but somehow better than this cruft:
    // javax.net.ssl.SSLHandshakeException: javax.net.ssl.SSLProtocolException: SSL handshake aborted: ssl=0xb8ae6ef0: Failure in SSL library, usually a protocol error
    // error:14077410:SSL routines:SSL23_GET_SERVER_HELLO:sslv3 alert handshake failure (external/openssl/ssl/s23_clnt.c:741 0x97059926:0x00000000)
    @Override
    public boolean verify(String hostname, SSLSession session) {
        if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session))
            throw new SxTrustException(R.string.error_hostname_verification);
        return true;
    }
}
