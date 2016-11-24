package com.skylable.sx.ui.fragments;

import android.content.Context;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.ui.activities.ActivityAccount;

/**
 * Created by tangarr on 21.10.15.
 */
public class AccountNormalFragment extends Fragment implements View.OnClickListener {

    View mView;

    private EditText mTextCluster;
    private EditText mTextUsername;
    private EditText mTextPassword;
    private View mButtonAdvanced;
    private View mButtonAddAccount;

    private String mAddress;
    private boolean mSsl;
    private long mPort;

    static View layout = null;
    static View progress = null;

    private String hardcoded_cluster = SxApp.getStringResource(R.string.hardcoded_cluster);
    private String hardcoded_domain = SxApp.getStringResource(R.string.hardcoded_domain);

    private ActivityAccount mActivityAccount = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.account_normal_fragment, container, false);

        layout = mView.findViewById(R.id.layout);
        progress = mView.findViewById(R.id.login_progress);

        bindControls();
        setupColoring();

        if (!TextUtils.isEmpty(hardcoded_cluster)) {
            mTextCluster.setText(hardcoded_cluster);
            mTextCluster.setVisibility(View.GONE);
        }

        if (savedInstanceState == null)
        {
            mAddress = "";
            mSsl = true;
            mPort = 443;
        }
        else
        {
            AccountAdvancedDialog dialog = (AccountAdvancedDialog) getFragmentManager().findFragmentByTag(AccountAdvancedDialog.class.getName());
            if (dialog != null)
            {
                dialog.setParentFragment(this);
            }
            mAddress = savedInstanceState.getString("mAddress");
            mSsl = savedInstanceState.getBoolean("mSsl");
            mPort = savedInstanceState.getLong("mPort");
            boolean progresVisible = savedInstanceState.getBoolean("progressVisible");
            if (progresVisible) {
                if (layout != null)
                    layout.setVisibility(View.GONE);
                if (progress != null)
                    progress.setVisibility(View.VISIBLE);
            }
        }
        return mView;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        layout = null;
        progress = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("mAddress", mAddress);
        outState.putBoolean("mSsl", mSsl);
        outState.putLong("mPort", mPort);
        outState.putBoolean("progressVisible", (mView.findViewById(R.id.login_progress).getVisibility() == View.VISIBLE));
        super.onSaveInstanceState(outState);
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
        mButtonAdvanced = mView.findViewById(R.id.buttonAdvanced);
        mButtonAddAccount = mView.findViewById(R.id.buttonAddAccount);

        mView.setOnClickListener(this);
        mButtonAdvanced.setOnClickListener(this);
        mButtonAddAccount.setOnClickListener(this);
    }

    public void setAdvanced(String address, boolean ssl, long port)
    {
        mAddress = address;
        mSsl = ssl;
        mPort = port;
    }

    private void setupColoring() {
        EditText[] list = { mTextCluster, mTextPassword, mTextUsername };
        for (EditText e : list)
        {
            if (Build.VERSION.SDK_INT >= 16)
            {
                LightingColorFilter filter = new LightingColorFilter(Color.BLACK,
                        getResources().getColor(R.color.inactiveTextColor));
                e.getBackground().setColorFilter(filter);
            }
            e.setHintTextColor(getResources().getColor(R.color.inactiveTextColor));
        }

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
        //mTextAddress.setOnFocusChangeListener(focusChangeListener);
        //mTextPort.setOnFocusChangeListener(focusChangeListener);

        mTextCluster.setOnFocusChangeListener(focusChangeListener);
        mTextPassword.setOnFocusChangeListener(focusChangeListener);
        mTextUsername.setOnFocusChangeListener(focusChangeListener);
    }

    boolean isValid()
    {
        String cluster = mTextCluster.getText().toString().trim();
        if (TextUtils.isEmpty(cluster))
        {
            mTextCluster.setError(getString(R.string.error_field_required));
            mTextCluster.requestFocus();
            return false;
        }

        String username = mTextUsername.getText().toString().trim();
        if (TextUtils.isEmpty(username))
        {
            mTextUsername.setError(getString(R.string.error_field_required));
            mTextUsername.requestFocus();
            return false;
        }

        String password = mTextPassword.getText().toString();
        if (TextUtils.isEmpty(password))
        {
            mTextPassword.setError(getString(R.string.error_field_required));
            mTextPassword.requestFocus();
            return false;
        }
        if (password.length()<8)
        {
            mTextPassword.setError(getString(R.string.error_password_to_short));
            mTextPassword.requestFocus();
            return false;
        }
        return true;
    }

    public void onBackPressed()
    {
        AccountAdvancedDialog dialog = (AccountAdvancedDialog) getFragmentManager().findFragmentByTag(AccountAdvancedDialog.class.getName());
        if (dialog != null)
        {
            getFragmentManager().beginTransaction().remove(dialog).commit();
            return;
        }
        getFragmentManager().beginTransaction().remove(this).commit();
    }

    @Override
    public void onClick(View v) {
        final AccountNormalFragment _this = this;
        if (v == mButtonAdvanced)
        {
            v.postDelayed(new Runnable() {
                @Override
                public void run() {
                    AccountAdvancedDialog dialog = new AccountAdvancedDialog();
                    dialog.setup(mAddress, mSsl, mPort);
                    dialog.setParentFragment(_this);
                    getFragmentManager().beginTransaction().add(R.id.accountView, dialog, dialog.getClass().getName()).commit();
                }
            }, 200);
        }
        else if (v == mButtonAddAccount) {
            if (isValid())
            {
                hideSoftKeyboard(mTextCluster);

                String cluster = mTextCluster.getText().toString().trim();
                if (!cluster.startsWith("sx://"))
                {
                    cluster = "sx://"+cluster;
                }
                if (TextUtils.isEmpty(hardcoded_cluster) && !TextUtils.isEmpty(hardcoded_domain)) {
                    cluster = cluster + "." + hardcoded_domain;
                }

                ActivityAccount.SxConnectionData c =  new ActivityAccount.SxConnectionData(
                        cluster,
                        mTextUsername.getText().toString(),
                        mTextPassword.getText().toString(),
                        mAddress.isEmpty() ? null : mAddress,
                        mPort, mSsl);

                mActivityAccount.addNormallAccount(c, new ActivityAccount.AsyncTaskListener() {
                    @Override
                    public void preExecute() {
                        layout.setVisibility(View.GONE);
                        progress.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void finish() {
                        if (layout != null)
                            layout.setVisibility(View.VISIBLE);
                        if (progress != null)
                            progress.setVisibility(View.GONE);
                    }
                });

            }
        }
    }

    private void hideSoftKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

}
