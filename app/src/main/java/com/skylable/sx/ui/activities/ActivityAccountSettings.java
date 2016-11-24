package com.skylable.sx.ui.activities;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.sxdrive2.SxAccount;

import static com.skylable.sx.R.string.are_you_sure_delete_account;

public class ActivityAccountSettings extends PreferenceActivity {

    String mAccountName;
    Account mAccount = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.account_preferences);

        Intent intent = getIntent();
        mAccountName = intent.getStringExtra("account");
        setupToolbar();
        setupCluster();
        setupRemoveAccountPreference();
    }

    void setupToolbar()
    {
        ViewGroup root = (ViewGroup) findViewById(android.R.id.content);
        Toolbar toolbar = (Toolbar) LayoutInflater.from(this).inflate(R.layout.app_toolbar, root, false);
        View content = root.getChildAt(0);

        int height;
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(R.attr.actionBarSize, tv, true)) {
            height = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
        }else{
            height = toolbar.getHeight();
        }
        content.setPadding(0, height, 0, 0);
        root.addView(toolbar, 0);

        String account = mAccountName;
        if (mAccountName.startsWith("*") && mAccountName.endsWith("*"))
            account = mAccountName.substring(1, mAccountName.length()-2);
        toolbar.setTitle(account);
        toolbar.setClickable(true);
        toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        toolbar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    void setupCluster()
    {
        mAccount = SxApp.getAccount(mAccountName);
        if (mAccount == null)
            return;

        Preference cluster = findPreference("preference_cluster");
        cluster.setSummary(AccountManager.get(getApplicationContext()).getUserData(mAccount, SxAccount.PARAM_URI));

        Preference username = findPreference("preference_username");

        int index = mAccountName.lastIndexOf("@");
        int startIndex = 0;
        if (mAccountName.startsWith("*") && mAccountName.endsWith("*"))
            startIndex = 1;
        username.setSummary(mAccountName.substring(startIndex, index));

    }

    private static Drawable sColoredIcon = null;

    void setupRemoveAccountPreference()
    {
        final Preference removeAccount = findPreference("action_remove");

        if (Build.VERSION.SDK_INT >= 11) {
            if (sColoredIcon == null) {
                Drawable icon;
                if (Build.VERSION.SDK_INT >= 21)
                    icon = getResources().getDrawable(R.drawable.option_remove, getTheme());
                else
                    icon = getResources().getDrawable(R.drawable.option_remove);

                if (icon != null) {
                    sColoredIcon = icon.getConstantState().newDrawable().mutate();
                    sColoredIcon.setColorFilter(new LightingColorFilter(Color.BLACK,
                            getResources().getColor(R.color.accentColor)));
                }
            }
            if (sColoredIcon != null)
                removeAccount.setIcon(sColoredIcon);
        }
        removeAccount.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                AlertDialog.Builder builder = new AlertDialog.Builder(ActivityAccountSettings.this);
                builder.setMessage(are_you_sure_delete_account)
                .setPositiveButton(R.string.action_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mAccount == null)
                            return;
                        AccountManager am = AccountManager.get(getApplicationContext());

                        if (Build.VERSION.SDK_INT >= 22)
                            am.removeAccountExplicitly(mAccount);
                        else
                            am.removeAccount(mAccount, null, null);

                        setResult(1);
                        finish();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .create().show();
                return false;
            }
        });
    }




}
