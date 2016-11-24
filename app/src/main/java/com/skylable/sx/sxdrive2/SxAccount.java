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
package com.skylable.sx.sxdrive2;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.jni.SXClient;
import com.skylable.sx.jni.SXError;
import com.skylable.sx.jni.SXNativeException;
import com.skylable.sx.jni.SXUri;
import com.skylable.sx.jni.SXVolume;
import com.skylable.sx.jni.SXVolumeVec;
import com.skylable.sx.providers.Contract;
import com.skylable.sx.util.Client;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by tangarr on 21.09.15.
 */
public class SxAccount {
    public static final String PARAM_URI = "uri";
    public static final String PARAM_TOKEN = "token";
    public static final String PARAM_IPADDRESS = "ipaddress";
    public static final String PARAM_PORT = "port";
    public static final String PARAM_SSL = "ssl";
    public static final String PARAM_VCLUSTER = "vcluster";
    public static final String PARAM_LOCKED = "locked";


    private Account mAccount;
    private long mId;

    private boolean _loadIdFromDatabase(SQLiteDatabase db)
    {
        Cursor cursor = db.query(SxDatabaseHelper.TABLE_ACCOUNTS,
                new String[] {Contract.COLUMN_ID},
                Contract.COLUMN_ACCOUNT + "=?",
                new String[] {mAccount.name}, null, null, null);
        if (cursor.getCount() == 0)
            return false;
        cursor.moveToFirst();
        mId = cursor.getLong(0);
        cursor.close();
        return true;
    }
    private void _addAccountToDatabase(SQLiteDatabase db)
    {
        ContentValues values = new ContentValues();
        values.put(Contract.COLUMN_ACCOUNT, mAccount.name);
        mId = db.insert(SxDatabaseHelper.TABLE_ACCOUNTS, null, values);
    }

    public SxAccount(Account account)
    {
        mAccount = account;
        SQLiteDatabase db = SxDatabaseHelper.database();
        if (!_loadIdFromDatabase(db))
            _addAccountToDatabase(db);
    }

    public static void removeOldAccounts()
    {
        AccountManager am = AccountManager.get(SxApp.sInstance);
        Account[] accounts =  am.getAccountsByType(SxApp.getStringResource(R.string.account_type));
        for (Account a : accounts)
        {
            if (a.name.startsWith("sx://")) {
                if (Build.VERSION.SDK_INT >= 22)
                    am.removeAccountExplicitly(a);
                else
                    am.removeAccount(a, null, null);
            }
        }
    }

    public static SxAccount loadAccount(String name)
    {
        AccountManager am = AccountManager.get(SxApp.sInstance);
        Account[] accounts =  am.getAccountsByType(SxApp.getStringResource(R.string.account_type));
        for (Account a: accounts)
        {
            if (TextUtils.equals(a.name, name))
            {
                SxAccount sxAccount = new SxAccount(a);
                return sxAccount;
            }
        }
        return null;
    }

    public static SxAccount loadFirstAccount()
    {
        AccountManager am = AccountManager.get(SxApp.sInstance);
        Account[] accounts =  am.getAccountsByType(SxApp.getStringResource(R.string.account_type));
        if (accounts.length > 0)
            return new SxAccount(accounts[0]);
        else
            return null;
    }

    public void lockAccount() {
        SQLiteDatabase database = SxDatabaseHelper.database();
        if (Build.VERSION.SDK_INT >= 11)
            database.beginTransactionNonExclusive();
        else
            database.beginTransaction();

        for (SxVolume v: volumes()) {
            v.removeFromDatabase(database);
        }
        database.setTransactionSuccessful();
        database.endTransaction();
        AccountManager am = AccountManager.get(SxApp.sInstance);
        am.setUserData(mAccount, PARAM_LOCKED, "true");
    }

    public String name()
    {
        return mAccount.name;
    }

    public boolean isLocked() {
        AccountManager am = AccountManager.get(SxApp.sInstance);
        String locked = am.getUserData(mAccount, PARAM_LOCKED);
        if (locked == null)
            return false;
        return Boolean.parseBoolean(locked);
    }

    public String cluster()
    {
        AccountManager am = AccountManager.get(SxApp.sInstance);
        return am.getUserData(mAccount, PARAM_URI);
    }
    public String ipAddress()
    {
        AccountManager am = AccountManager.get(SxApp.sInstance);
        return am.getUserData(mAccount, PARAM_IPADDRESS);
    }
    public String token()
    {
        AccountManager am = AccountManager.get(SxApp.sInstance);
        return am.getUserData(mAccount, PARAM_TOKEN);
    }
    public long port()
    {
        AccountManager am = AccountManager.get(SxApp.sInstance);
        String port = am.getUserData(mAccount, PARAM_PORT);
        return Long.parseLong(port);
    }
    public boolean ssl()
    {
        AccountManager am = AccountManager.get(SxApp.sInstance);
        String ssl = am.getUserData(mAccount, PARAM_SSL);
        return Boolean.parseBoolean(ssl);
    }

    public boolean updateVolumeList(SXClient client) {
        SXVolumeVec volumes;
        try {
            SXUri uri = client.parseUri(cluster());
            volumes = client.listVolumes(uri);
        } catch (SXNativeException e) {
            e.printStackTrace();

            if (client.getErrNum()== SXError.SXE_EAUTH) {
                lockAccount();
                return true;
            }
            return false;
        }
        return updateVolumeList(volumes);
    }

    public boolean updateVolumeList(SXVolumeVec volumes)
    {
        HashMap<String, SxVolume> volumesHashMap = new HashMap<>();
        for (SxVolume vol: SxVolume.loadSxVolumes(mId)) {
            volumesHashMap.put(vol.name(), vol);
        }

        SQLiteDatabase database = SxDatabaseHelper.database();
        if (Build.VERSION.SDK_INT >= 11)
            database.beginTransactionNonExclusive();
        else
            database.beginTransaction();
        for (SXVolume v: volumes)
        {
            if (volumesHashMap.containsKey(v.getName()))
            {
                SxVolume vol = volumesHashMap.get(v.getName());
                /*
                int encrypted = vol.encrypted();
                if (v.isEncrypted() == 0)
                    encrypted = 0;
                    */
                int encrypted = vol.encrypted();
                if (v.isEncrypted() != vol.encrypted())
                {
                    if (v.isEncrypted() == 1 && vol.encrypted() != 2)
                        encrypted = v.isEncrypted();
                    else if (v.isEncrypted() == 3 && vol.encrypted() != 4)
                        encrypted = v.isEncrypted();
                }
                vol.updateDatabase(encrypted, v.fingerprint() , v.used(), v.size(), database);
                volumesHashMap.remove(vol.name());
            }
            else
            {
                SxVolume.insertToDatabase(mId, v.getName(), v.isEncrypted(), v.fingerprint(), v.used(), v.size(),database);
            }
        }
        for (SxVolume v:volumesHashMap.values()) {
            v.removeFromDatabase(database);
        }
        database.setTransactionSuccessful();
        database.endTransaction();
        return true;
    }

    public ArrayList<SxVolume> volumes()
    {
        return SxVolume.loadSxVolumes(mId);
    }

    public String vcluster() {
        AccountManager am = AccountManager.get(SxApp.sInstance);
        return am.getUserData(mAccount, PARAM_VCLUSTER);
    }

    public static String readVCluster(String desc_json) {
        if (desc_json==null)
            return null;
        try {
            JSONObject json = new JSONObject(desc_json);
            return json.getString("vc");
        } catch (JSONException e) {
            return null;
        }
    }
}
