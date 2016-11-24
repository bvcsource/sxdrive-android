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
package com.skylable.sx.app;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Hashtable;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import com.skylable.sx.R;
import com.skylable.sx.jni.SXClient;
import com.skylable.sx.jni.SXError;
import com.skylable.sx.sxdrive2.SxAccount;
import com.skylable.sx.sxdrive2.SyncQueue;
import com.skylable.sx.ui.fragments.MessageFragment;

public class SxApp extends Application
{
    private static final String TAG = SxApp.class.getSimpleName();
    private static final String SX_STORAGE = "storage";
    private static final String SX_CONFIG = "config";
    private static final String PREFS_DEVICE_ID = "device_id";
    private static final String PROP_DEBUG = "com.skylable.sx.debug";

    public static SxApp sInstance;
    public static File sCacheDir;
    public static File sFilesDir;
    public static File sConfigDir;
    public static PackageManager sPM;
    public static PackageInfo sPackageInfo;
    public static Bitmap sLargeIcon;

    public static long sCurrentDirectoryId = -1;

    public static SyncQueue sSyncQueue = new SyncQueue();

    private static String sLastPath = null;

    private static ConnectivityManager sCM;
    private static boolean sStorageOK;

    static private SXClient sClient = null;

    synchronized static public SXClient getCurrentClient()
    {
        ConnectivityManager cm = (ConnectivityManager)sInstance.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        boolean connected = info != null && info.isConnectedOrConnecting();

        if (!connected)
        {
            if (sClient != null)
                sClient = null;
        }
        else
        {
            if (sClient == null)
            {
                if (currentAccountName() != null)
                {
                    SxAccount a = SxAccount.loadAccount(currentAccountName());
                    SXClient client;
                    try {
                        client = new SXClient(SxApp.sFilesDir.getPath());
                    }
                    catch (Exception ex)
                    {
                        Log.e("Exception", ""+SxApp.sFilesDir);
                        ex.printStackTrace();
                        return null;
                    }
                    boolean result = client.initCluster(a.cluster(), a.token(), a.ipAddress(), a.port(), a.ssl());
                    if (result)
                        sClient = client;
                    else
                    {
                        Log.e("SxApp", "Failed to initialize cluster: " + client.getErrMsg());
                        if (client != null && client.getErrNum() == SXError.SXE_EAUTH) {
                            a.lockAccount();
                        }
                        return null;
                    }
                }
            }
        }
        return sClient;
    }

    static public SXClient getClient(SxAccount account)
    {
        ConnectivityManager cm = (ConnectivityManager)sInstance.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        boolean connected = info != null && info.isConnectedOrConnecting();

        if (!connected)
        {
            return null;
        }

        SXClient client;
        try {
            client = new SXClient(SxApp.sFilesDir.getPath());
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            return null;
        }
        boolean result = client.initCluster(account.cluster(), account.token(), account.ipAddress(), account.port(), account.ssl());
        if (result)
        {
            return client;
        }
        else
        {
            Log.e("SxAccount", "Failed to initialize cluster: "+client.getErrMsg());
            return null;
        }
    }

    static public void setsLastPath(String lastPath)
    {
        String old_account = currentAccountName();
        sLastPath = lastPath;
        if (!TextUtils.equals(old_account, currentAccountName()))
        {
            sClient = null;
        }
    }

    public static String currentAccountName()
    {
        if (sLastPath == null)
            return null;
        int index, start;
        if (sLastPath.startsWith("sx://")) {
            start = 5;
            index = sLastPath.indexOf(":", 5);
        }
        else {
            start = 0;
            index = sLastPath.indexOf(":");
        }
        if (index > 0)
            return sLastPath.substring(start, index-start);
        return sLastPath;
    }
    public static String currentPath()
    {
        if (sLastPath == null)
            return null;
        int index;
        if (sLastPath.startsWith("sx://"))
            index = sLastPath.indexOf(":", 5);
        else
            index = sLastPath.indexOf(":");
        return sLastPath.substring(index);
    }

    public static void validateCurrentAccount() {
        AccountManager am = AccountManager.get(sInstance);
        Account[] accounts =  am.getAccountsByType(getStringResource(R.string.account_type));
        if (SxApp.currentAccountName() != null)
        {
            String account = SxApp.currentAccountName();
            boolean valid = false;
            for (Account a : accounts)
            {
                if (TextUtils.equals(a.name, account))
                {
                    valid = true;
                    break;
                }
            }
            if (!valid)
                SxApp.setsLastPath(null);
        }
        if (SxApp.currentAccountName() == null)
        {
            if (accounts.length > 0)
            {
                SxApp.setsLastPath(accounts[0].name+":/");
                Log.d("Current account", accounts[0].name);
                SxAccount.loadAccount(accounts[0].name);
            }
        }
    }

    public static Account getAccount(String mAccountName) {
        AccountManager am = AccountManager.get(sInstance);
        Account[] accounts =  am.getAccountsByType(getStringResource(R.string.account_type));

        for (Account a: accounts)
        {
            if (a.name.equals(mAccountName))
                return a;
        }
        return null;
    }

    public class MESSAGE_CODES
    {
        static public final int DISABLE_NEXT_MESSAGE_DIALOG = 101;
        static public final int CLOSE_UPLOAD_ACTIVITY = 102;
        static public final String PROMPT = "prompt";

    }

    public static int convertDp2Px(int dp)
    {
        DisplayMetrics displayMetrics = sInstance.getResources().getDisplayMetrics();
        return Math.round(dp * displayMetrics.density);
    }

    public static int convertPx2Dp(int px)
    {
        DisplayMetrics displayMetrics = sInstance.getResources().getDisplayMetrics();
        return Math.round(px / displayMetrics.density);
    }

    @Override
    public void onCreate()
    {
        Log.wtf("SxApp", "onCreate");
        super.onCreate();

        sInstance = this;

        sPM = getPackageManager();
        sCM = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        sPackageInfo = gatherPackageInfo();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            sLargeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);

        setupDirectories();

        if (!isDebug())
        {
            Log.i(TAG, "Release build");
            if (isConnected())
                pingHome();
        }
        SxAccount.removeOldAccounts();
    }

    private boolean isDebug()
    {
        Class<?> clazz;
        java.lang.reflect.Method method;
        try
        {
            clazz = Class.forName("android.os.SystemProperties");
            method = clazz.getDeclaredMethod("get", String.class);
            return Boolean.valueOf((String) method.invoke(null, PROP_DEBUG));
        } catch (Exception e)
        {
            return false;
        }
    }

    private void pingHome()
    {
        final String fingerprint = getUUID() +
                        "." + String.valueOf(sPackageInfo.versionCode).replace(".", "-") +
                        "." + Build.VERSION.RELEASE.replace(".", "-") +
                        ".android.skylable.com";
        new Thread()
        {
            public void run()
            {
                try
                {
                    InetAddress.getByName(fingerprint);
                } catch (UnknownHostException ignored)
                {}
            }
        }.start();
    }

    public static String getUUID()
    {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(sInstance);
        String uuid = prefs.getString(PREFS_DEVICE_ID, null);

        if (uuid != null)
            return uuid;

        uuid = Secure.getString(sInstance.getContentResolver(), Secure.ANDROID_ID);

        prefs.edit().putString(PREFS_DEVICE_ID, uuid).apply();
        return uuid;
    }

    public static String getStringResource(int intRes) {
        return sInstance.getString(intRes);
    }

    public static String getStringResource(int intRes, Object ... params) {
        return sInstance.getString(intRes, params);
    }

    private PackageInfo gatherPackageInfo()
    {
        try
        {
            return sPM.getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e)
        {
            throw new RuntimeException(e);
        }
    }

    private void setupDirectories()
    {
        sFilesDir = getExternalFilesDir(SX_STORAGE);

        if (sFilesDir == null) {
            MessageFragment.showMessage("Function getExternalFilesDir failed. Restarting device may resolve problem.");
            Log.e("CRASH", "HERE");
            return;
        }

        if (!sFilesDir.exists())
        {
            sFilesDir.mkdir();
            sFilesDir.setExecutable(true, false);
        }

        try
        {
            final File nomedia = new File(sFilesDir, ".nomedia");
            if (!nomedia.exists())
                nomedia.createNewFile();
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        sCacheDir = getExternalCacheDir();

        if (sCacheDir == null)
            return;

        for (File file: sCacheDir.listFiles())
            file.delete();

        sConfigDir = new File(getApplicationInfo().dataDir, SX_CONFIG);
        sStorageOK = true;
    }

    public static boolean isStorageOK()
    {
        return sStorageOK;
    }

    public static boolean isConnected()
    {
        NetworkInfo ni = sCM.getActiveNetworkInfo();
        return ((ni != null) && ni.isConnected());
    }
}
