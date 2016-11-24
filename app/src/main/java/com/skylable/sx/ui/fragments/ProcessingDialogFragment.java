package com.skylable.sx.ui.fragments;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.SupportAsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.jni.SXClient;
import com.skylable.sx.providers.Contract;
import com.skylable.sx.sxdrive2.SxDatabaseHelper;
import com.skylable.sx.sxdrive2.SxDirectory;
import com.skylable.sx.sxdrive2.SxFileInfo;

/**
 * Created by tangarr on 01.10.15.
 */
public class ProcessingDialogFragment extends InputDialogFragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);
        setTitle(getString(R.string.processing));
        setSecondaryTitle(null);
        setTextInputEnabled(true);
        showProgressBar();
        setLocket(true);
        return v;
    }

    public boolean onBackPressed()
    {
        return true;
    }

    @Override
    void onAcceptClicked() {
    }
}
