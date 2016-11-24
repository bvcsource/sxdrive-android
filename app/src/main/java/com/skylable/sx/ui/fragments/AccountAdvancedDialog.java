package com.skylable.sx.ui.fragments;

import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;

import com.skylable.sx.R;

import java.util.regex.Pattern;

/**
 * Created by tangarr on 21.10.15.
 */
public class AccountAdvancedDialog extends Fragment implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    private View mView = null;
    private EditText mTextAddress;
    private EditText mTextPort;
    private CheckBox mCheckBoxSSL;
    private View mButtonSave;
    private View mButtonCancel;

    private AccountNormalFragment mParentFragment = null;

    private String mAddress = null;
    private boolean mSsl;
    private long mPort;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.account_advanced_dialog, container, false);
        bindControls();
        setupColoring();

        if (mAddress != null)
        {
            mTextAddress.setText(mAddress);
            mCheckBoxSSL.setChecked(mSsl);
            mTextPort.setText(mPort+"");
        }

        return mView;
    }

    public void setup(String address, boolean ssl, long port)
    {
        if (mView != null)
        {
            mTextAddress.setText(address);
            mCheckBoxSSL.setChecked(ssl);
            mTextPort.setText(port+"");
        }
        else
        {
            mAddress = address;
            mSsl = ssl;
            mPort = port;
        }
    }

    public void setParentFragment(AccountNormalFragment fragment)
    {
        mParentFragment = fragment;
    }

    void bindControls()
    {
        mTextAddress = (EditText) mView.findViewById(R.id.address);
        mTextPort = (EditText) mView.findViewById(R.id.port);
        mCheckBoxSSL = (CheckBox) mView.findViewById(R.id.ssl);
        mButtonSave = mView.findViewById(R.id.button_ok);
        mButtonCancel = mView.findViewById(R.id.button_cancel);

        mView.setOnClickListener(this);
        mButtonSave.setOnClickListener(this);
        mButtonCancel.setOnClickListener(this);
        mCheckBoxSSL.setOnCheckedChangeListener(this);
    }

    private void setupColoring() {
        EditText[] list = { mTextAddress, mTextPort };
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
        mTextAddress.setOnFocusChangeListener(focusChangeListener);
        mTextPort.setOnFocusChangeListener(focusChangeListener);
    }

    boolean isValid() {
        mAddress = mTextAddress.getText().toString().trim();
        if (!TextUtils.isEmpty(mAddress)) {
            if (!isValidIPv4(mAddress) && !isValidIPv6(mAddress)) {
                mTextAddress.setError(getString(R.string.error_invalid_ipaddress));
                mTextAddress.requestFocus();
                return false;
            }
        }

        String tmp = mTextPort.getText().toString().trim();
        if(TextUtils.isEmpty(tmp))
        {
            boolean checked = mCheckBoxSSL.isChecked();
            mTextPort.setText(checked ? "443" : "80");
        }

        mPort = Long.parseLong(tmp);
        if(mPort<=0||mPort>65535) {
            mTextPort.setError(getString(R.string.error_invalid_port));
            mTextPort.requestFocus();
            return false;
        }
        mSsl = mCheckBoxSSL.isChecked();
        return true;
    }

    static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$");
    static final Pattern IPV6_STD_PATTERN = Pattern.compile(
            "^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");
    static final Pattern IPV6_HEX_COMPRESSED_PATTERN = Pattern.compile(
            "^((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)$");

    boolean isValidIPv4(String address)
    {
        return IPV4_PATTERN.matcher(address).matches();
    }

    boolean isValidIPv6(String address)
    {
        return IPV6_STD_PATTERN.matcher(address).matches() ||
                IPV6_HEX_COMPRESSED_PATTERN.matcher(address).matches();

    }

    @Override
    public void onClick(final View v) {
        final AccountAdvancedDialog _this = this;

        if (v == mButtonSave || v == mButtonCancel) {
            mView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (v == mButtonCancel)
                    {
                        getFragmentManager().beginTransaction().remove(_this).commit();
                    }
                    else if (v == mButtonSave)
                    {
                        if (isValid())
                        {
                            mParentFragment.setAdvanced(mAddress, mSsl, mPort);
                            getFragmentManager().beginTransaction().remove(_this).commit();
                        }
                    }
                }
            }, 200);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView == mCheckBoxSSL)
        {
            if (isChecked)
                mTextPort.setText("443");
            else
                mTextPort.setText("80");
        }
    }
}
