package com.skylable.sx.ui.fragments;

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
import com.skylable.sx.sxdrive2.SxFileInfo;

/**
 * Created by tangarr on 01.10.15.
 */
public class RemoveFragment extends InputDialogFragment {

    static private RemoveAsyncTask task = null;
    private long mSelectedFileId = -1;
    private boolean isDir = false;
    static private OnFragmentRefreshListener mOnFragmentRefreshListener;
    private View mView = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = super.onCreateView(inflater, container, savedInstanceState);

        setSecondaryTitle(null);
        setTextInputEnabled(false);
        setOkButtonText(getString(R.string.action_delete));
        setCancelButtonText(getString(R.string.action_cancel));

        boolean _updateTitle = true;
        if (savedInstanceState != null)
        {
            mSelectedFileId = savedInstanceState.getLong("fileId");
            if (task != null)
            {
                _updateTitle = false;
                setTitle(getString(R.string.processing));
                showProgressBar();
                setLocket(true);
            }
        }

        if (mSelectedFileId != -1 && _updateTitle)
            updateTitle();
        return mView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putLong("fileId", mSelectedFileId);
        super.onSaveInstanceState(outState);
    }

    private void updateTitle()
    {
        try {
            SxFileInfo info = new SxFileInfo(mSelectedFileId);
            isDir = info.isDir();
            if (isDir)
                setTitle(getString(R.string.ask_delete_directory, info.filename()));
            else
                setTitle(getString(R.string.ask_delete_file, info.filename()));
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    public void setup(long fileId, OnFragmentRefreshListener onFragmentRefreshListener)
    {
        mSelectedFileId = fileId;
        mOnFragmentRefreshListener = onFragmentRefreshListener;
        if (mView != null)
            updateTitle();
    }

    public void reSetup(OnFragmentRefreshListener onFragmentRefreshListener)
    {
        mOnFragmentRefreshListener = onFragmentRefreshListener;
    }

    public boolean onBackPressed()
    {
        if (task == null)
            close();
        return true;
    }

    @Override
    void onAcceptClicked() {
        task = new RemoveAsyncTask(mSelectedFileId);
        task.execute();
    }

    class RemoveAsyncTask
    {
        private SupportAsyncTask<Void, Void, String> mTask = new SupportAsyncTask<Void, Void, String>()
        {
            private SXClient mClient;
            @Override
            protected void onPreExecute() {
                setTitle(getString(R.string.processing));
                showProgressBar();
                setLocket(true);
                hideSoftKeyboard();
            }

            @Override
            protected String doInBackground(Void... params) {
                mClient = SxApp.getCurrentClient();
                if (mClient == null)
                    return getString(R.string.no_data_connection);

                if (!mClient.remove(mSource, mVolume))
                    return mClient.getErrMsg();
                return null;
            }

            @Override
            protected void onPostExecute(String s) {
                task = null;
                mClient = null;
                if (s != null)
                {
                    showError(s);
                    return;
                }
                SxDatabaseHelper.database().delete(SxDatabaseHelper.TABLE_FILES,
                        String.format("%s = %d", Contract.COLUMN_ID, mFileId),
                        null);
                mOnFragmentRefreshListener.onRefresh();
                close();
            }
        };
        private String mSource;
        private String mError = null;
        private long mFileId;
        private String mVolume;

        public RemoveAsyncTask(long fileId)
        {
            mFileId = fileId;
            try {
                SxFileInfo fileInfo = new SxFileInfo(mFileId);
                mSource = fileInfo.path();
                mVolume = fileInfo.volume();
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                mError = ex.getMessage();
            }
        }
        public void execute()
        {
            if (mError == null)
                mTask.execute();
            else {
                task = null;
                showError(mError);
            }
        }
        private void showError(String error)
        {
            close();
            MessageFragment.showMessage(error);
        }
    }
}
