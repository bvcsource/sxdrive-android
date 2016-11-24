package com.skylable.sx.ui.activities;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.skylable.sx.R;

import org.apache.http.conn.ssl.AbstractVerifier;

public class ActivitySettings extends PreferenceActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        setupToolbar();
        setupAddAccountPreference();
        loadAccounts();
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

        toolbar.setTitle("Settings");
        toolbar.setClickable(true);
        toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        toolbar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    void loadAccounts()
    {
        PreferenceGroup accountsPreference = (PreferenceGroup) findPreference("accounts");
        Preference addNewAccountPreference = findPreference("add_account");

        AccountManager am = AccountManager.get(this);
        final Account[] accounts =  am.getAccountsByType(getResources().getString(R.string.account_type));
        addNewAccountPreference.setOrder(accounts.length + 1);

        for (int i=0; i<accounts.length; i++)
        {
            Preference p = new Preference(this);
            final String accountName = accounts[i].name;
            String name = accountName;

            if (accountName.startsWith("*") && accountName.endsWith("*")) {
                if (Build.VERSION.SDK_INT >= 11) {
                    p.setIcon(R.drawable.ic_account_enterprise);
                    Drawable icon = p.getIcon();
                    icon.setColorFilter( new LightingColorFilter(Color.BLACK,
                            getResources().getColor(R.color.secondaryIconColor)));
                    name = accountName.substring(1, accountName.length() - 1);
                }
                else {
                    name = "**" + accountName.substring(1, accountName.length() - 1);
                }
            }
            else if (Build.VERSION.SDK_INT >= 11) {
                p.setIcon(R.drawable.ic_account);
                Drawable icon = p.getIcon();
                icon.setColorFilter( new LightingColorFilter(Color.BLACK,
                        getResources().getColor(R.color.secondaryIconColor)));
            }
            int index = name.lastIndexOf("@");
            p.setTitle(name.substring(0, index));
            p.setSummary(name.substring(index + 1));
            accountsPreference.addPreference(p);
            p.setOrder(i);

            p.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(getApplicationContext(), ActivityAccountSettings.class);
                    intent.putExtra("account", accountName);
                    startActivityForResult(intent, ActivityAccount.REQUEST_CODE);
                    return false;
                }
            });
        }
    }

    void setupAddAccountPreference()
    {
        final Preference addNewAccountPreference = findPreference("add_account");

        if (Build.VERSION.SDK_INT >= 11) {
            addNewAccountPreference.setIcon(R.drawable.add_user);
            Drawable icon = addNewAccountPreference.getIcon();
            icon.setColorFilter( new LightingColorFilter(Color.BLACK,
                    getResources().getColor(R.color.accentColor)));
        }
        addNewAccountPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(getApplicationContext(), ActivityAccount.class);
                startActivityForResult(intent, ActivityAccount.REQUEST_CODE);
                return false;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ActivityAccount.REQUEST_CODE)
        {
            if (resultCode == 1)
            {
                PreferenceGroup accountsPreference = (PreferenceGroup) findPreference("accounts");
                Preference addNewAccountPreference = findPreference("add_account");
                addNewAccountPreference.setOrder(0);
                accountsPreference.removeAll();
                accountsPreference.addPreference(addNewAccountPreference);
                loadAccounts();
            }
        }
    }
}
