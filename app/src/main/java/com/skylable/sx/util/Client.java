package com.skylable.sx.util;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.util.Log;

import com.skylable.sx.app.SxApp;
import com.skylable.sx.auth.SxClientEx;
import com.skylable.sx.jni.SXClient;
import com.skylable.sx.jni.SXError;
import com.skylable.sx.jni.SXNativeException;
import com.skylable.sx.jni.SXUri;
import com.skylable.sx.sxdrive2.SxAccount;
import com.skylable.sx.sxdrive2.SxFileInfo;

public class Client
{
    private static final String TAG = Client.class.getSimpleName();

    private static boolean isRecoverable(int error)
    {
        return (error == SXError.SXE_ECFG) || (error == SXError.SXE_EARG);
    }


    public static SxClientEx getClientEx(String accountName, SxClientEx.OnProgressListener listener) throws SXNativeException {

        final SxClientEx client = new SxClientEx(SxApp.sFilesDir.getPath(),listener);
        SxAccount account = SxAccount.loadAccount(accountName);
        SXUri uri = client.parseUri(account.cluster());

        if (!client.loadAndUpdate(uri))
        {
            if (!client.initCluster(account.cluster(), account.token(), account.ipAddress(), account.port(), account.ssl())
                    || client.loadAndUpdate(uri))
                throw new SXNativeException("Cluster init failed: " + client.getErrMsg());
        }
        return client;
    }
}
