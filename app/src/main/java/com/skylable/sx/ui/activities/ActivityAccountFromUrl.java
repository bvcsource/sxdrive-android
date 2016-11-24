package com.skylable.sx.ui.activities;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.SupportAsyncTask;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.jni.SXClient;
import com.skylable.sx.jni.SXNativeException;
import com.skylable.sx.sxdrive2.SxAccount;
import com.skylable.sx.ui.fragments.MessageFragment;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ActivityAccountFromUrl extends AccountAuthenticatorFragmentActivity {

    private ProgressBar mProgressBar = null;
    private TextView mMessage = null;
    private Button mButton = null;
    static SupportAsyncTask<Void, Void, String> sTask = null;
    static ActivityAccountFromUrl sInstance = null;
    static Account sAccount = null;
    static String sErrorMessage = null;
    static Bundle sParams = null;

    @Override
    protected void onPause() {
        super.onPause();
        sInstance = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        sInstance = this;
    }

    @Override
    protected void onCreate(Bundle savedInstance) {
        setContentView(R.layout.account_from_uri_activity);
        super.onCreate(savedInstance);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessage = (TextView) findViewById(R.id.message);
        mButton = (Button) findViewById(R.id.button);

        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sAccount == null || sParams == null) {
                    sAccount = null;
                    showError(getString(R.string.error_adding_account));
                    return;
                }
                AccountManager am = AccountManager.get(getApplicationContext());
                boolean result = am.addAccountExplicitly(sAccount, null, sParams);
                if (!result)
                {
                    sAccount = null;
                    showError(getString(R.string.error_adding_account));
                    return;
                }
                else
                {
                    final String authority = getString(R.string.authority);
                    ContentResolver.setIsSyncable(sAccount, authority, 1);
                    ContentResolver.setSyncAutomatically(sAccount, authority, true);
                    ContentResolver.addPeriodicSync(sAccount, authority, new Bundle(), 600);
                    SxApp.setsLastPath(sAccount.name + ":/");

                    Intent intent = new Intent(getApplicationContext(), ActivityMain.class);
                    startActivity(intent);
                    finish();
                }
            }
        });

        sInstance = this;

        if (savedInstance == null) {
            initialize();
        }
        else {
            if (sErrorMessage != null) {
                showError(sErrorMessage);
            }
            else if (sAccount != null) {
                int index = sAccount.name.lastIndexOf("@");
                String user = sAccount.name.substring(0, index);
                String cluster = sAccount.name.substring(index+1);
                showButton(user, cluster);
            }
        }
    }

    private void initialize() {
        sAccount = null;
        sParams = null;
        sErrorMessage = null;

        Intent intent = getIntent();
        if (intent == null) {
            showError(getString(R.string.invalid_link));
            return;
        }
        final Uri uri = intent.getData();
        if (uri == null) {
            showError(getString(R.string.invalid_link));
            return;
        }
        final String cluster = "sx://"+uri.getHost();
        int port = uri.getPort();
        boolean ssl = true;
        String ip_address = null;
        String token = null;

        String args[] = uri.getQuery().split("&");

        for (String s : args) {
            String[] pair = s.split("=");
            if (pair.length != 2) {
                showError(getString(R.string.invalid_link));
                return;
            }
            String key = pair[0];
            String value = pair[1];

            switch (key) {
                case "token":
                    token = value;
                    break;
                case "ssl":
                    ssl = value.equals("y");
                    break;
                case "ip":
                    ip_address = value;
                    break;
                case "port":
                    port = Integer.parseInt(value);
                    break;
            }
        }
        if (port == -1)
            port = ssl ? 443 : 80;

        if (token == null) {
            showError(getString(R.string.invalid_link));
            return;
        }

        final String finalToken = token;
        final String finalIp_address = ip_address;
        final int finalPort = port;
        final boolean finalSsl = ssl;
        sTask = new SupportAsyncTask<Void, Void, String>() {
            private String username = null;
            private String vcluster = null;
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                if (sInstance != null) {
                    sInstance.showProgress(getString(R.string.validating_account));
                }
            }

            @Override
            protected String doInBackground(Void... params) {
                SXClient client = null;
                try {
                    client = new SXClient(SxApp.sFilesDir.getPath());
                } catch (SXNativeException e) {
                    return getString(R.string.client_init_failed);
                }
                String ip = finalIp_address;
                if (ip != null) {
                    if (ip.isEmpty())
                        ip = null;
                    else {
                        try {
                            InetAddress[] list = InetAddress.getAllByName(uri.getHost());
                            for (InetAddress address: list) {
                                if (TextUtils.equals(ip, address.getHostAddress()))
                                {
                                    ip = null;
                                    break;
                                }
                            }
                        } catch (UnknownHostException e) {
                            e.printStackTrace();
                        }
                    }
                }

                if (!client.initCluster(cluster, finalToken, ip, finalPort, finalSsl)) {
                    return client.getErrMsg();
                }
                username = client.getUsername();
                if (TextUtils.isEmpty(username)) {
                    return client.getErrMsg();
                }
                vcluster = SxAccount.readVCluster(client.getDesc());
                return null;
            }

            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
                sTask = null;

                AccountManager am = AccountManager.get(getApplicationContext());
                String account_name = username+"@"+cluster.substring(5);
                for (Account acc : am.getAccountsByType(getResources().getString(R.string.account_type))) {
                    if (TextUtils.equals(acc.name, account_name)) {
                        if (sInstance == null)
                            sErrorMessage = getString(R.string.error_account_already_exists);
                        else
                            sInstance.showError(getString(R.string.error_account_already_exists));
                        return;
                    }
                }
                sAccount = new Account(account_name, getResources().getString(R.string.account_type));

                sParams = new Bundle();
                sParams.putString(SxAccount.PARAM_URI, cluster);
                sParams.putString(SxAccount.PARAM_TOKEN, finalToken);

                if (!TextUtils.isEmpty(finalIp_address))
                    sParams.putString(SxAccount.PARAM_IPADDRESS, finalIp_address);
                if (!TextUtils.isEmpty(vcluster))
                    sParams.putString(SxAccount.PARAM_VCLUSTER, vcluster);

                sParams.putString(SxAccount.PARAM_PORT, String.valueOf(finalPort));
                sParams.putString(SxAccount.PARAM_SSL, String.valueOf(finalSsl));

                if (sInstance != null) {
                    if (s != null) {
                        sInstance.showError(s);
                    }
                    else {
                        sInstance.showButton(username, cluster);
                    }
                }
            }
        };
        sTask.execute();
    }

    private void showError(String error) {
        sErrorMessage = error;
        mProgressBar.setVisibility(View.GONE);
        mMessage.setVisibility(View.VISIBLE);
        mButton.setVisibility(View.GONE);
        mMessage.setTextColor(0xFFFF0000);
        mMessage.setText(error);
    }

    private void showProgress(String message) {
        mProgressBar.setVisibility(View.VISIBLE);
        mMessage.setVisibility(View.VISIBLE);
        mButton.setVisibility(View.GONE);
        mMessage.setTextColor(0xFF000000);
        mMessage.setText(message);
    }

    private void showButton(String username, String cluster) {
        String message = getString(R.string.prompt_username)+": "+username + "\n" +
                         getString(R.string.prompt_cluster)+": "+cluster;
        mProgressBar.setVisibility(View.GONE);
        mMessage.setVisibility(View.VISIBLE);
        mButton.setVisibility(View.VISIBLE);
        mMessage.setTextColor(0xFF000000);
        mMessage.setText(message);
    }
}
