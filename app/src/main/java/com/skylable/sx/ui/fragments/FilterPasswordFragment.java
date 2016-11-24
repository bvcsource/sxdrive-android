package com.skylable.sx.ui.fragments;

import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.SupportAsyncTask;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.jni.SXClient;
import com.skylable.sx.sxdrive2.SxDatabaseHelper;
import com.skylable.sx.sxdrive2.SxVolume;

/**
 * Created by tangarr on 01.10.15.
 */
public class FilterPasswordFragment extends InputDialogFragment {
    private View mView = null;
    SxVolume mVolume = null;
    static CheckPasswordTask mTask;
    VolumesFragment mVolumesFragment = null;
    boolean mTaskStarted = false;
    boolean mCheckboxVisible = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = super.onCreateView(inflater, container, savedInstanceState);

        setTitle(SxApp.getStringResource(R.string.encrypted_volume));
        setSecondaryTitle(null);
        setTextInputEnabled(false);
        setOkButtonText(getString(android.R.string.ok));
        setCancelButtonText(getString(R.string.action_cancel));

        if (savedInstanceState != null)
        {
            Long volumeId = savedInstanceState.getLong("volume");
            mTaskStarted = savedInstanceState.getBoolean("task");

            mVolume = SxVolume.loadVolume(volumeId);
            if (mTask != null)
                mTask.setFragment(this);
        }
        else
        {
            mTask = null;
        }

        if (mVolume != null)
            initView();

        showSoftKeyboard();
        return mView;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mTask != null)
            mTask.setFragment(null);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mTaskStarted && mTask == null)
        {
            close();
            mVolumesFragment.refreshAndOpen(null);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putLong("volume", mVolume.volumeId());
        outState.putBoolean("task", mTask != null);
        super.onSaveInstanceState(outState);
    }

    public void setup(SxVolume volume, VolumesFragment fragment)
    {
        mVolume = volume;
        mVolumesFragment = fragment;
        if (mView != null)
            initView();
    }

    public void setVolumesFragment(VolumesFragment fragment)
    {
        Log.e("FPF", "setVolumesFragment");
        mVolumesFragment = fragment;
    }

    private void initView()
    {
        if (mTask == null) {
            String fp = mVolume.aesFingerprint();
            if (!fp.isEmpty()) {
                setSecondaryTitle(getString(R.string.volume_unlock));
                setPasswordInputEnabled(true, false);
            } else {
                setSecondaryTitle(getString(R.string.volume_new_password));
                setPasswordInputEnabled(true, true);
                if (mVolume.encrypted() >= 3)
                {
                    setCheckboxText(getString(R.string.encrypt_filenames));
                    setCheckboxVisible(true);
                    setCheckboxValue(false);
                    mCheckboxVisible = true;
                }
            }
        }
        else {
            showProgress();
            if (mVolume.encrypted() >= 3) {
                mCheckboxVisible = true;
            }
        }
    }

    private void showProgress()
    {
        setSecondaryTitle(getString(R.string.login_progress_calculate_token));
        showProgressBar();
    }


    public boolean onBackPressed()
    {
        if (mTask == null)
            close();
        return true;
    }

    @Override
    void onAcceptClicked() {
        String tmp = getInputText();
        if (tmp.length() < 8) {
            setInputError(getString(R.string.error_password_to_short));
            return;
        }
        if (mVolume.aesFingerprint().isEmpty())
        {
            if (!TextUtils.equals(tmp, getInputText2()))
            {
                setInputError(getString(R.string.password_do_not_match));
                return;
            }
            tmp = getInputText2();
        }
        else
            tmp = null;

        mTask = new CheckPasswordTask(mVolume, getInputText(), tmp, this, getCheckboxValue());
        mTask.start();
    }

    static class CheckPasswordTask extends SupportAsyncTask<Void, Void, String>
    {
        String mPassword1, mPassword2;
        SxVolume mVolume;
        SXClient mClient;
        FilterPasswordFragment mFragment;
        boolean mEncryptFilenames;

        public CheckPasswordTask(SxVolume volume, String password1, String password2, FilterPasswordFragment fragment, boolean encryptFilenames)
        {
            mVolume = volume;
            mPassword1 = password1;
            mPassword2 = password2;
            mFragment = fragment;
            mEncryptFilenames = encryptFilenames;
        }

        public void setFragment(FilterPasswordFragment fragment)
        {
            mFragment = fragment;
        }

        public void start()
        {
            mFragment.mTaskStarted = true;
            mFragment.showProgress();
            mFragment.hideSoftKeyboard();
            execute();
        }

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected String doInBackground(Void... params) {
            mClient = SxApp.getCurrentClient();
            if (mClient == null)
                return SxApp.getStringResource(R.string.no_data_connection);

            mClient.setEncryptFilenames(mEncryptFilenames);

            if (mClient.checkPassword(mPassword1, mPassword2, mVolume.name())) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return null;
            }
            else {

                String lastNotice = mClient.lastNotice();
                if (lastNotice.isEmpty())
                    return mClient.getErrMsg();
                else
                    return lastNotice;
            }
        }

        @Override
        protected void onPostExecute(String s) {
            mTask = null;
            if (s == null)
            {
                String fp = "";
                if (mClient != null)
                {
                    fp = mClient.volumeAesFingerprint(mVolume.name());
                }

                SQLiteDatabase db = SxDatabaseHelper.database();
                if (Build.VERSION.SDK_INT>=11)
                    db.beginTransactionNonExclusive();
                else
                    db.beginTransaction();
                mVolume.updateDatabase(mVolume.encrypted()==1 ? 2 : 4, fp, db);
                db.setTransactionSuccessful();
                db.endTransaction();
                if (mFragment != null) {
                    mFragment.close();
                    mFragment.mVolumesFragment.refreshAndOpen(mVolume);
                }
            }
            else {
                if (mFragment != null) {
                    mFragment.hideProgressBar();
                    if (mFragment.mCheckboxVisible) {
                        mFragment.setCheckboxVisible(true);
                    }
                    mFragment.setPasswordInputEnabled(true, mVolume.aesFingerprint().isEmpty());
                    MessageFragment.showMessage(s);
                }
            }
        }
    }

}
