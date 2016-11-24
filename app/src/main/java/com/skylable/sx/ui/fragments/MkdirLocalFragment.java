package com.skylable.sx.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.skylable.sx.R;

import java.io.File;

/**
 * Created by tangarr on 01.10.15.
 */
public class MkdirLocalFragment extends InputDialogFragment {

    private String mDir = null;
    private ExportFragment mExportFragment = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);

        setTitle(getString(R.string.enter_directoryname));
        setSecondaryTitle(null);
        setTextInputEnabled(true);
        setOkButtonText(getString(R.string.action_create));
        setCancelButtonText(getString(R.string.action_cancel));

        showSoftKeyboard();

        return v;
    }

    void setup(String dir, ExportFragment exportFragment)
    {
        mDir = dir;
        mExportFragment = exportFragment;
    }

    public boolean onBackPressed()
    {
        close();
        return true;
    }

    @Override
    void onAcceptClicked() {
        String name = getInputText();
        if (name.isEmpty())
        {
            setInputError(getString(R.string.dirname_empty));
            return;
        }
        if (name.equals(".") || name.equals("..") || name.contains("/"))
        {
            setInputError(getString(R.string.dirname_invalid));
            return;
        }
        String path = mDir+"/"+name;
        File newDir = new File(path);
        if (newDir.exists())
        {
            setInputError(getString(R.string.directory_exists));
            return;
        }
        if (!newDir.mkdir())
        {
            setInputError(getString(R.string.mkdir_failed));
            return;
        }
        close();
        mExportFragment.refreshAndOpenDirectory(newDir);
    }
}
