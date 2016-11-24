package com.skylable.sx.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.SupportAsyncTask;
import android.support.v4.util.Pair;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.enterprise.SxTrustManager;
import com.skylable.sx.sxdrive2.ExportAdapter;
import com.skylable.sx.sxdrive2.FilesAdapter;
import com.skylable.sx.sxdrive2.SxAccount;
import com.skylable.sx.sxdrive2.SxFileInfo;
import com.skylable.sx.ui.activities.ActivityMain;
import com.skylable.sx.util.FileOps;

import org.json.JSONException;
import  org.json.JSONObject;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;

/**
 * Created by tangarr on 08.10.15.
 */
public class PublicLinkFragment extends Fragment implements View.OnClickListener {

    private CheckBox mCheckBoxPassword;
    private CheckBox mCheckBoxNotifyMe;
    private EditText mPassword1;
    private EditText mPassword2;
    private EditText mEmail;
    private long mfileId;
    private static Context sContext = null;
    private String sPublicLink = null;
    private View mFormView = null;
    private View mProgressView = null;
    private View mButtonBar = null;
    private static PublicLinkFragment sInstance = null;

    private long mExpirationTime = 0;
    private static SupportAsyncTask<Void, Void, Pair<Boolean, String>> task = null;

    private String expire_string[] = {
            SxApp.getStringResource(R.string.expiration_one_day),
            SxApp.getStringResource(R.string.expiration_one_week),
            SxApp.getStringResource(R.string.expiration_one_month),
            SxApp.getStringResource(R.string.expiration_one_year),
            SxApp.getStringResource(R.string.expiration_infinite),
    };
    private long expire_time[] = {
            24*60*60,
            7*24*60*60,
            30*24*60*60,
            365*24*60*60,
            9999999999l
    };

    public void setup(long fileId) {
        mfileId = fileId;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View mView = inflater.inflate(R.layout.public_link_fragment, container, false);
        mView.setOnClickListener(this);
        mCheckBoxPassword = (CheckBox) mView.findViewById(R.id.checkBoxPassword);
        mCheckBoxNotifyMe = (CheckBox) mView.findViewById(R.id.checkBoxNotify);
        mPassword1 = (EditText) mView.findViewById(R.id.password1);
        mPassword2 = (EditText) mView.findViewById(R.id.password2);
        mEmail = (EditText) mView.findViewById(R.id.email);
        Spinner mSpinner = (Spinner) mView.findViewById(R.id.spinner);

        mCheckBoxPassword.setOnClickListener(this);
        mCheckBoxNotifyMe.setOnClickListener(this);
        mView.findViewById(R.id.button_ok).setOnClickListener(this);
        mView.findViewById(R.id.button_cancel).setOnClickListener(this);

        mFormView = mView.findViewById(R.id.form);
        mProgressView = mView.findViewById(R.id.progressBar);
        mButtonBar = mView.findViewById(R.id.buttons_bar);

        mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mExpirationTime = expire_time[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), android.R.layout. simple_spinner_dropdown_item , expire_string );
        mSpinner.setAdapter(adapter);

        if (FilesAdapter.sSxShare == null)
            mCheckBoxNotifyMe.setVisibility(View.GONE);

        if (savedInstanceState == null) {
            sPublicLink = null;
            mPassword1.setVisibility(View.GONE);
            mPassword2.setVisibility(View.GONE);
            mEmail.setVisibility(View.GONE);
        }
        else {
            mfileId = savedInstanceState.getLong("fileId");
            boolean passwordChecked = savedInstanceState.getBoolean("password_checked");
            boolean notifyMeChecked = savedInstanceState.getBoolean("notify_checked");
            mCheckBoxPassword.setChecked(passwordChecked);
            mCheckBoxNotifyMe.setChecked(notifyMeChecked);
            if (!mCheckBoxPassword.isChecked()) {
                mPassword1.setVisibility(View.GONE);
                mPassword2.setVisibility(View.GONE);
            }
            if (mCheckBoxNotifyMe.getVisibility() != View.VISIBLE || !mCheckBoxNotifyMe.isChecked())
                mEmail.setVisibility(View.GONE);
            if (task != null) {
                mButtonBar.setVisibility(View.INVISIBLE);
                mFormView.setVisibility(View.GONE);
                mProgressView.setVisibility(View.VISIBLE);
            }
        }
        sContext = getActivity();
        return mView;
    }

    @Override
    public void onPause() {
        super.onPause();
        sContext = null;
        sInstance = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        sContext = getActivity();
        sInstance = this;
        if (sPublicLink != null) {
            FileOps.sharePublicLink(sPublicLink, sContext);
            sPublicLink = null;
        }
        if (task == null && mProgressView.getVisibility() == View.VISIBLE) {
            sInstance.mButtonBar.setVisibility(View.VISIBLE);
            sInstance.mFormView.setVisibility(View.VISIBLE);
            sInstance.mProgressView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("password_checked", mCheckBoxPassword.isChecked());
        outState.putBoolean("notify_checked", mCheckBoxNotifyMe.isChecked());
        outState.putLong("fileId", mfileId);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_ok: {
                if (validate())
                    createLink();
            } break;
            case R.id.button_cancel: {
                close();
            } break;
            case R.id.checkBoxPassword: {
                int visibility = mCheckBoxPassword.isChecked()?View.VISIBLE:View.GONE;
                mPassword1.setVisibility(visibility);
                mPassword2.setVisibility(visibility);
                if (mCheckBoxPassword.isChecked()) {
                    mPassword1.setText("");
                    mPassword2.setText("");
                }

            } break;
            case R.id.checkBoxNotify: {
                mEmail.setVisibility(mCheckBoxNotifyMe.isChecked()?View.VISIBLE:View.GONE);
                if (!mCheckBoxNotifyMe.isChecked()) {
                    mEmail.setText("");
                }
            } break;
        }
    }

    private boolean validate() {
        if (mCheckBoxPassword.isChecked()) {
            if (mPassword1.getText().toString().length() < 8) {
                mPassword1.setError(SxApp.getStringResource(R.string.error_password_to_short));
                mPassword1.requestFocus();
                return false;
            }
            if (!TextUtils.equals(mPassword1.getText().toString(), mPassword2.getText().toString())) {
                mPassword2.setError(SxApp.getStringResource(R.string.password_do_not_match));
                mPassword2.requestFocus();
                return false;
            }
        }
        if (mCheckBoxNotifyMe.isChecked()) {
            String email = mEmail.getText().toString();
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches())
            {
                mEmail.setError(SxApp.getStringResource(R.string.enter_valid_email));
                mEmail.requestFocus();
                return false;
            }
        }
        return true;
    }

    private void createLink() {
        final JSONObject json = new JSONObject();
        SxAccount account = SxAccount.loadAccount(SxApp.currentAccountName());
        String error = null;
        if (!SxApp.isConnected()) {
            error = SxApp.getStringResource(R.string.no_data_connection);
        }
        else if (account == null) {
            error = "Unable to load account";
        }
        else {
            try {
                SxFileInfo fileInfo = new SxFileInfo(mfileId);
                json.put("access_key", account.token());
                json.put("path", "/"+fileInfo.volume()+fileInfo.path());
                json.put("expire_time", mExpirationTime);
                json.put("password", mCheckBoxPassword.isChecked() ? mPassword1.getText().toString() : "");
                if (mCheckBoxNotifyMe.isChecked())
                    json.put("notify", mEmail.getText().toString());

                task = new SupportAsyncTask<Void, Void, Pair<Boolean, String>>() {
                    String jsonDoc;
                    @Override
                    protected void onPreExecute() {
                        super.onPreExecute();
                        jsonDoc = json.toString();
                        sPublicLink = null;
                        sInstance.mButtonBar.setVisibility(View.INVISIBLE);
                        sInstance.mFormView.setVisibility(View.GONE);
                        sInstance.mProgressView.setVisibility(View.VISIBLE);
                    }

                    @Override
                    protected Pair<Boolean, String> doInBackground(Void... params) {
                        boolean returnValue = false;
                        String data = "Internal error";
                        try {
                            String url_str = FilesAdapter.sSxShare==null ? FilesAdapter.sSxWeb : FilesAdapter.sSxShare;
                            if (url_str.endsWith("/"))
                                url_str = url_str + "api/share";
                            else
                                url_str = url_str + "/api/share";
                            URL url = new URL(url_str);

                            SSLContext context = SSLContext.getInstance("TLS");
                            context.init(null, new X509TrustManager[]{new X509TrustManager(){
                                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
                                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
                                public X509Certificate[] getAcceptedIssuers() {
                                    return new X509Certificate[0];
                                }
                            }}, new SecureRandom());
                            HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());

                            HttpURLConnection connection = (HttpURLConnection)url.openConnection();

                            connection.setDoOutput(true);
                            connection.setDoInput(true);
                            connection.setRequestProperty("Content-Type", "application/json");
                            connection.setRequestProperty("Accept", "application/json");
                            connection.setRequestMethod("POST");

                            try {
                                OutputStream out = new BufferedOutputStream(connection.getOutputStream());
                                out.write(jsonDoc.getBytes("UTF-8"));
                                out.flush();

                                int responseCode = connection.getResponseCode();
                                if (responseCode != 200) {
                                    throw new Exception(connection.getResponseMessage());
                                }

                                InputStream in = new BufferedInputStream(connection.getInputStream());
                                ByteArrayOutputStream byteStream = new ByteArrayOutputStream(in.available());
                                byte[] buffer = new byte[1024];
                                while (true) {
                                    int count = in.read(buffer);
                                    if (count <= 0)
                                        break;
                                    byteStream.write(buffer, 0, count);
                                }
                                String reply_string = byteStream.toString("UTF-8");
                                JSONObject jReply = new JSONObject(reply_string);
                                if (jReply.has("status")) {
                                    returnValue = jReply.getBoolean("status");
                                    if (returnValue) {
                                        if (jReply.has("publink"))
                                            data = jReply.getString("publink");
                                    }
                                    else if (jReply.has("error"))
                                        data = jReply.getString("error");
                                }
                                returnValue = true;
                            }
                            catch (Exception ex) {
                                ex.printStackTrace();
                                data = ex.getMessage();
                            }
                            finally {
                                connection.disconnect();
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                            data = e.getMessage();
                        }
                        return Pair.create(returnValue, data);
                    }

                    @Override
                    protected void onPostExecute(Pair<Boolean, String> booleanStringPair) {
                        super.onPostExecute(booleanStringPair);
                        task = null;
                        Boolean succeed = booleanStringPair.first;
                        String data = booleanStringPair.second;
                        if (succeed) {
                            close();
                            if (sContext != null) {
                                FileOps.sharePublicLink(data, sContext);
                            }
                            else
                                sPublicLink = data;
                        }
                        else {
                            if (sInstance != null) {
                                sInstance.mButtonBar.setVisibility(View.VISIBLE);
                                sInstance.mFormView.setVisibility(View.VISIBLE);
                                sInstance.mProgressView.setVisibility(View.GONE);
                            }
                            MessageFragment.showMessage(data);
                        }
                    }
                };
                task.execute();


            } catch (Exception e) {
                error = e.getMessage();
                e.printStackTrace();
            }
        }
        if (error != null) {
            MessageFragment.showMessage(error);
        }
    }

    public void close() {
        ActivityMain.closeFragment(getClass().getName());
    }

    public void onBackPressed() {
        close();
    }
}
