package com.skylable.sx.ui.activities;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.content.SupportAsyncTask;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.enterprise.SxTrustManager;
import com.skylable.sx.jni.SXClient;
import com.skylable.sx.jni.SXError;
import com.skylable.sx.ui.fragments.AccountEnterpriseFragment;
import com.skylable.sx.ui.fragments.AccountNormalFragment;
import com.skylable.sx.ui.fragments.InputDialogFragment;
import com.skylable.sx.ui.fragments.MessageFragment;
import com.skylable.sx.sxdrive2.SxAccount;
import com.skylable.sx.ui.fragments.YesNoDialog;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;

public class ActivityAccount extends AccountAuthenticatorFragmentActivity {

    public static final int REQUEST_CODE = 1001;
    static private SupportAsyncTask<Void, Void, String> task = null;
    private static boolean sPaused = true;
    private static String sErrorMessage = null;

    private String hardcoded_cluster = SxApp.getStringResource(R.string.hardcoded_cluster);
    private boolean whitelabel = !TextUtils.isEmpty(hardcoded_cluster);

    private static YesNoDialog.AcceptListener sOnClickListener = null;

    public static ActivityAccount sInstance=null;

    static public class SxConnectionData
    {
        public SxConnectionData(String uri, String username, String password, String ip_address, long port, boolean use_ssl)
        {
            this.uri = uri;
            this.username = username;
            this.password = password;
            this.ip_address = ip_address;
            this.port = port;
            this.use_ssl = use_ssl;
        }
        String uri;
        String username;
        String password;
        String ip_address;
        long port;
        boolean use_ssl;
    }

    static public class LdapConnectionData
    {
        public LdapConnectionData(URL url, String username, String password, String device)
        {
            this.url = url;
            this.username = username;
            this.password = password;
            this.device = device;
        }
        URL url;
        String username;
        String password;
        String device;
    }

    void setupAccountButtons()
    {
        final ActivityAccount _this = this;
        View normalLogin, enterpriseLogin;
        normalLogin = findViewById(R.id.buttonNormalLogin);
        enterpriseLogin = findViewById(R.id.buttonEnterpriseLogin);

        normalLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AccountNormalFragment fragment = new AccountNormalFragment();
                fragment.setActivityAccount(_this);
                getSupportFragmentManager().beginTransaction().add(R.id.accountView, fragment, fragment.getClass().getName()).commit();

            }
        });
        enterpriseLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AccountEnterpriseFragment fragment = new AccountEnterpriseFragment();
                fragment.setActivityAccount(_this);
                getSupportFragmentManager().beginTransaction().add(R.id.accountView, fragment, fragment.getClass().getName()).commit();
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_activity);
        setupAccountButtons();
        if (whitelabel) {
            findViewById(R.id.buttonNormalLogin).setVisibility(View.INVISIBLE);
            findViewById(R.id.buttonEnterpriseLogin).setVisibility(View.INVISIBLE);
            findViewById(R.id.textView).setVisibility(View.INVISIBLE);
        }

        if (savedInstanceState != null) {
            AccountNormalFragment normalFragment = (AccountNormalFragment) getSupportFragmentManager().findFragmentByTag(AccountNormalFragment.class.getName());
            if (normalFragment != null)
            {
                normalFragment.setActivityAccount(this);
            }
            AccountEnterpriseFragment enterpriseFragment = (AccountEnterpriseFragment) getSupportFragmentManager().findFragmentByTag(AccountEnterpriseFragment.class.getName());
            if (enterpriseFragment != null)
            {
                enterpriseFragment.setActivityAccount(this);
            }
            InputDialogFragment inputFragment = (InputDialogFragment) getSupportFragmentManager().findFragmentByTag(InputDialogFragment.FRAGMENT_TAG);
            if (inputFragment instanceof YesNoDialog) {
                ((YesNoDialog) inputFragment).setupAcceptListener(sOnClickListener);
            }
        }
        else {
            if (whitelabel) {
                AccountNormalFragment fragment = new AccountNormalFragment();
                fragment.setActivityAccount(this);
                getSupportFragmentManager().beginTransaction().add(R.id.accountView, fragment, fragment.getClass().getName()).commit();
            }
        }
    }

    @Override
    protected void onResume() {
        sInstance = this;
        super.onResume();
        MessageFragment.setupFragmentManager(getSupportFragmentManager(), R.id.accountView);
        sPaused = false;
        if (sErrorMessage != null) {
            MessageFragment.showMessage(sErrorMessage);
            sErrorMessage = null;
        }
        //hideSoftKeyboard();
    }

    @Override
    protected void onPause() {
        sInstance = null;
        super.onPause();
        sPaused = true;
    }

    private void hideSoftKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    @Override
    public void onBackPressed() {

        InputDialogFragment inputFragment = (InputDialogFragment) getSupportFragmentManager().findFragmentByTag(InputDialogFragment.FRAGMENT_TAG);
        if (inputFragment != null) {
            inputFragment.onBackPressed();
            return;
        }

        if (task != null)
            return;

        if (whitelabel)
            super.onBackPressed();

        AccountNormalFragment normalFragment = (AccountNormalFragment) getSupportFragmentManager().findFragmentByTag(AccountNormalFragment.class.getName());
        if (normalFragment != null)
        {
            normalFragment.onBackPressed();
            return;
        }
        AccountEnterpriseFragment enterpriseFragment = (AccountEnterpriseFragment) getSupportFragmentManager().findFragmentByTag(AccountEnterpriseFragment.class.getName());
        if (enterpriseFragment != null)
        {
            enterpriseFragment.onBackPressed();
            return;
        }
        super.onBackPressed();
    }

    public void addNormallAccount(final SxConnectionData connInfo, final AsyncTaskListener listener)
    {
        boolean result = false;

        task = new SupportAsyncTask<Void, Void, String>() {

            SXClient client;
            String token;
            String mError;
            private String vcluster = null;

            @Override
            protected void onPreExecute() {
                mError = null;
                listener.preExecute();
                try {
                    client = new SXClient(SxApp.sFilesDir.getPath());
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                    mError = getString(R.string.client_init_failed);
                }
            }

            @Override
            protected String doInBackground(Void... params) {
                if (mError != null)
                {
                    return mError;
                }
                token = client.pass2token(connInfo.uri, connInfo.username, connInfo.password, connInfo.ip_address, connInfo.port, connInfo.use_ssl);
                if (token.isEmpty())
                {
                    return client.getErrMsg();
                }
                if (!client.initCluster(connInfo.uri, token, connInfo.ip_address, connInfo.port, connInfo.use_ssl))
                {
                    return client.getErrMsg();
                }
                vcluster = SxAccount.readVCluster(client.getDesc());
                return null;
            }

            @Override
            protected void onPostExecute(String s) {
                listener.finish();
                task = null;

                if (s != null)
                {
                    if (sPaused)
                        sErrorMessage = s;
                    else
                        MessageFragment.showMessage(s);
                    return;
                }
                AccountManager am = AccountManager.get(getApplicationContext());
                String account_name = connInfo.username+"@"+connInfo.uri.substring(5);
                for (Account acc : am.getAccountsByType(getResources().getString(R.string.account_type))) {
                    if (TextUtils.equals(acc.name, account_name)) {
                        if (sPaused)
                            sErrorMessage = s;
                        else
                            MessageFragment.showMessage(R.string.error_account_already_exists);
                        return;
                    }
                }
                Account a = new Account(account_name, getResources().getString(R.string.account_type));

                Bundle params = new Bundle();
                params.putString(SxAccount.PARAM_URI, connInfo.uri);
                params.putString(SxAccount.PARAM_TOKEN, token);

                if (!TextUtils.isEmpty(connInfo.ip_address))
                    params.putString(SxAccount.PARAM_IPADDRESS, connInfo.ip_address);
                if (!TextUtils.isEmpty(vcluster))
                    params.putString(SxAccount.PARAM_VCLUSTER, vcluster);

                params.putString(SxAccount.PARAM_PORT, String.valueOf(connInfo.port));
                params.putString(SxAccount.PARAM_SSL, String.valueOf(connInfo.use_ssl));

                boolean result = am.addAccountExplicitly(a, null, params);
                if (!result)
                {
                    if (sPaused)
                        sErrorMessage = s;
                    else
                        MessageFragment.showMessage(R.string.error_adding_account);
                    return;
                }
                else
                {
                    final String authority = getString(R.string.authority);
                    ContentResolver.setIsSyncable(a, authority, 1);
                    ContentResolver.setSyncAutomatically(a, authority, true);
                    ContentResolver.addPeriodicSync(a, authority, new Bundle(), 600);

                    SxApp.setsLastPath(account_name+":/");
                    SxAccount sxAccount = new SxAccount(a);
                    sxAccount.updateVolumeList(client);
                }
                setResult(1);
                finish();
            }
        };
        task.execute();
    }

    public void addEnterpriseAccount(final LdapConnectionData c, final AsyncTaskListener listener, final String cert_sha1) {

        task = new SupportAsyncTask<Void, Void, String>() {
            private String mDialogMessage;
            private String clusterUri;
            private String token;
            private String ip;
            private boolean ssl;
            private long port;
            private String vcluster = null;

            private SXClient client;

            @Override
            protected void onPreExecute() {
                listener.preExecute();

                try {
                    client = new SXClient(SxApp.sFilesDir.getPath());
                }
                catch (Exception ex)
                {
                    client = null;
                    ex.printStackTrace();
                }
            }

            @Override
            protected String doInBackground(Void... params) {
                if (client == null)
                {
                    return getString(R.string.client_init_failed);
                }

                sOnClickListener = null;
                mDialogMessage = null;
                String link = client.fetch_sxauthd_credentials(c.username, c.password, c.url.getHost(), c.url.getPort(), c.device, SxApp.getUUID(), cert_sha1);
                if (link.isEmpty()) {
                    String lastNotice = client.lastNotice();
                    String cert_sha1 = null;

                    if (TextUtils.equals(client.getErrMsg(), "User rejected the certificate") && lastNotice!=null && lastNotice.startsWith("Server certificate:")) {
                        String phrase = "SHA1 fingerprint:";
                        int index = lastNotice.indexOf(phrase);
                        if (index >= 0) {
                            cert_sha1 = lastNotice.substring(index+phrase.length());
                        }
                    }

                    if (cert_sha1 != null) {
                        mDialogMessage = lastNotice;
                        final String finalCert_sha = cert_sha1;
                        sOnClickListener = new YesNoDialog.AcceptListener() {
                            @Override
                            public void onAcceptClicked(YesNoDialog dialog) {
                                addEnterpriseAccount(c, listener, finalCert_sha);
                                dialog.close();
                                sOnClickListener = null;
                            }
                        };
                        return "self-signed";
                    }
                    return client.getErrMsg();
                }
                final Uri uri = Uri.parse(link);

                if (!TextUtils.equals("sx",uri.getScheme()) || TextUtils.isEmpty(uri.getPath()))
                {
                    return getString(R.string.error_invalid_uri);
                }

                clusterUri = "sx://" + uri.getHost();
                token = uri.getQueryParameter("token");
                if (token != null)
                    token = token.replace(' ', '+');

                ip = uri.getQueryParameter("ip");
                if (ip != null) {
                    if (ip.isEmpty())
                        ip = null;
                    else {
                        try {
                            InetAddress[] list = InetAddress.getAllByName(c.url.getHost());
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

                ssl = !"n".equals(uri.getQueryParameter("ssl"));
                port = Long.parseLong(uri.getQueryParameter("port"));

                if (!client.initCluster(clusterUri, token, ip, port, ssl))
                {
                    return client.getErrMsg();
                }

                vcluster = SxAccount.readVCluster(client.getDesc());
                return null;
            }

            @Override
            protected void onPostExecute(final String error) {
                listener.finish();
                task = null;

                if (error == null) { // ok we have an sx:// url

                    AccountManager am = AccountManager.get(getApplicationContext());
                    String account_name = "*"+c.username+"@"+c.url.getHost()+"*";
                    for (Account acc : am.getAccountsByType(getResources().getString(R.string.account_type))) {
                        if (TextUtils.equals(acc.name, account_name)) {
                            if (sPaused)
                                sErrorMessage = getString(R.string.error_account_already_exists);
                            else
                                MessageFragment.showMessage(R.string.error_account_already_exists);
                            return;
                        }
                    }
                    Account a = new Account(account_name, getResources().getString(R.string.account_type));

                    Bundle params = new Bundle();
                    params.putString(SxAccount.PARAM_URI, clusterUri);
                    params.putString(SxAccount.PARAM_TOKEN, token);
                    if (!TextUtils.isEmpty(vcluster))
                        params.putString(SxAccount.PARAM_VCLUSTER, vcluster);

                    if (!TextUtils.isEmpty(ip))
                        params.putString(SxAccount.PARAM_IPADDRESS, ip);

                    params.putString(SxAccount.PARAM_PORT, String.valueOf(port));
                    params.putString(SxAccount.PARAM_SSL, String.valueOf(ssl));

                    boolean result = am.addAccountExplicitly(a, null, params);
                    if (!result)
                    {
                        if (sPaused)
                            sErrorMessage = getString(R.string.error_adding_account);
                        else
                            MessageFragment.showMessage(R.string.error_adding_account);
                        return;
                    }
                    else
                    {
                        final String authority = getString(R.string.authority);
                        ContentResolver.setIsSyncable(a, authority, 1);
                        ContentResolver.setSyncAutomatically(a, authority, true);
                        ContentResolver.addPeriodicSync(a, authority, new Bundle(), 600);

                        SxApp.setsLastPath(account_name+":/");
                        SxAccount sxAccount = new SxAccount(a);
                        sxAccount.updateVolumeList(client);
                    }
                    setResult(1);
                    finish();

                } else if (sOnClickListener != null) {
                    YesNoDialog dialog = new YesNoDialog();
                    dialog.setup(sOnClickListener,
                            getString(R.string.prompt_self_signed),
                            mDialogMessage,
                            getString(R.string.action_accept),
                            getString(R.string.action_cancel));
                    getSupportFragmentManager().beginTransaction().add(R.id.accountView, dialog, YesNoDialog.FRAGMENT_TAG).commit();
                }
                else
                {
                    if (sPaused)
                        sErrorMessage = error;
                    else
                        MessageFragment.showMessage(error);
                }
            }
        };
        task.execute();
    }

    public interface AsyncTaskListener
    {
        void preExecute();
        void finish();
    }
}
