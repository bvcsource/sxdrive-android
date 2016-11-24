package com.skylable.sx.ui.fragments;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.ui.activities.ActivityAccount;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by tangarr on 21.10.15.
 */
public class AccountEnterpriseFragment extends Fragment implements View.OnClickListener {

    View mView;

    private EditText mTextCluster;
    private EditText mTextUsername;
    private EditText mTextPassword;
    private EditText mTextDevice;
    private View mButtonAddAccount;

    private ActivityAccount mActivityAccount = null;
    private String hardcoded_domain = SxApp.getStringResource(R.string.hardcoded_domain);

    static View layout = null;
    static View progress = null;
    static boolean sTaskInProgress = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.account_enterprise_fragment, container, false);

        bindControls();
        setupColoring();

        layout = mView.findViewById(R.id.layout);
        progress = mView.findViewById(R.id.login_progress);

        if (savedInstanceState == null)
        {
            try {
                final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter != null)
                    mTextDevice.setText(adapter.getName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (mTextDevice.getText().toString().isEmpty())
            {
                TelephonyManager telephonyManager = (TelephonyManager) mActivityAccount.getSystemService(Context.TELEPHONY_SERVICE);
                String id = telephonyManager.getDeviceId();
                if (id != null)
                {
                    mTextDevice.setText(id);
                }
            }
        }
        else {
            if (sTaskInProgress) {
                layout.setVisibility(View.GONE);
                progress.setVisibility(View.VISIBLE);
            }
        }

        return mView;
    }

    @Override
    public void onPause() {
        super.onPause();
        layout = null;
        progress = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        layout = mView.findViewById(R.id.layout);
        progress = mView.findViewById(R.id.login_progress);
    }

    public void setActivityAccount(ActivityAccount activityAccount)
    {
        mActivityAccount = activityAccount;
    }

    void bindControls()
    {
        mTextCluster = (EditText) mView.findViewById(R.id.cluster);
        mTextUsername = (EditText) mView.findViewById(R.id.username);
        mTextPassword = (EditText) mView.findViewById(R.id.password);
        mTextDevice = (EditText) mView.findViewById(R.id.display);
        mButtonAddAccount = mView.findViewById(R.id.buttonAddAccount);

        mView.setOnClickListener(this);
        mButtonAddAccount.setOnClickListener(this);
    }

    private void setupColoring() {

        View.OnFocusChangeListener focusChangeListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (v instanceof EditText) {
                    EditText e = (EditText) v;
                    if (Build.VERSION.SDK_INT >= 16) {
                        LightingColorFilter filter = new LightingColorFilter(Color.BLACK,
                                hasFocus ?
                                        getResources().getColor(R.color.accentColor) :
                                        getResources().getColor(R.color.inactiveTextColor));
                        e.getBackground().setColorFilter(filter);

                    }
                }
            }
        };

        EditText[] list = { mTextCluster, mTextPassword, mTextUsername, mTextDevice };
        for (EditText e : list)
        {
            if (Build.VERSION.SDK_INT >= 16)
            {
                LightingColorFilter filter = new LightingColorFilter(Color.BLACK,
                        getResources().getColor(R.color.inactiveTextColor));
                e.getBackground().setColorFilter(filter);
            }
            e.setHintTextColor(getResources().getColor(R.color.inactiveTextColor));
            e.setOnFocusChangeListener(focusChangeListener);
        }
    }

    boolean isValid()
    {
        if (TextUtils.isEmpty(mTextCluster.getText().toString().trim()))
        {
            mTextCluster.setError(getString(R.string.error_field_required));
            mTextCluster.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(mTextUsername.getText().toString().trim()))
        {
            mTextUsername.setError(getString(R.string.error_field_required));
            mTextUsername.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(mTextPassword.getText().toString()))
        {
            mTextPassword.setError(getString(R.string.error_field_required));
            mTextPassword.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(mTextDevice.getText().toString().trim()))
        {
            mTextDevice.setError(getString(R.string.error_field_required));
            mTextDevice.requestFocus();
            return false;
        }

        return true;
    }

    public void onBackPressed()
    {
        getFragmentManager().beginTransaction().remove(this).commit();
    }

    @Override
    public void onClick(View v) {
        final AccountEnterpriseFragment _this = this;
        if (v == mButtonAddAccount) {
            if (isValid())
            {
                hideSoftKeyboard(mTextCluster);

                URL url = null;
                try {
                    String server = mTextCluster.getText().toString().trim();
                    if (!TextUtils.isEmpty(hardcoded_domain)) {
                        int index = server.indexOf(":");
                        if (index > 0) {
                            String host = server.substring(0, index);
                            String port = server.substring(index+1);
                            server = host+"."+hardcoded_domain+":"+port;
                        }
                        else
                            server = server + "." + hardcoded_domain;
                    }
                    if (!server.contains(":")) {
                        server = server + ":443";
                    }
                    url = new URL("https://" + server + "/.auth/api/v1/create");
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                    mTextCluster.setError(getString(R.string.error_incorrect_endpoint));
                    return;
                }

                ActivityAccount.LdapConnectionData c = new ActivityAccount.LdapConnectionData(
                        url,
                        mTextUsername.getText().toString().trim(),
                        mTextPassword.getText().toString(),
                        mTextDevice.getText().toString().trim());

                mActivityAccount.addEnterpriseAccount(c, new ActivityAccount.AsyncTaskListener() {
                    @Override
                    public void preExecute() {
                        sTaskInProgress = true;
                        layout.setVisibility(View.GONE);
                        progress.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void finish() {
                        if (layout != null)
                            layout.setVisibility(View.VISIBLE);
                        if (progress != null)
                            progress.setVisibility(View.GONE);
                        sTaskInProgress = false;
                    }
                }, null);
            }
        }
    }

    private void hideSoftKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

}
