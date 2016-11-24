package com.skylable.sx.ui.fragments;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.provider.SyncStateContract;
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
public class MkdirRemoteFragment extends InputDialogFragment {

    private MkDirAsyncTask task = null;
    private long mDirectoryId = -1;
    private FilesPagerSlider mSlider = null;


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

    public void setup(long directoryId, FilesPagerSlider slider)
    {
        mDirectoryId = directoryId;
        mSlider = slider;
    }

    public boolean onBackPressed()
    {
        if (task == null)
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

        task = new MkDirAsyncTask(mDirectoryId, name);
        task.execute();
    }




    class MkDirAsyncTask
    {
        private SupportAsyncTask<String, Void, String> mTask = new SupportAsyncTask<String, Void, String>()
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
            protected String doInBackground(String... params) {
                mClient = SxApp.getCurrentClient();
                if (mClient == null)
                    return getString(R.string.no_data_connection);
                if (!mClient.sxmkdir(params[0], mVolume))
                    return mClient.getErrMsg();
                return null;
            }

            @Override
            protected void onPostExecute(String s) {
                mClient = null;
                if (s != null)
                {
                    showError(s);
                    return;
                }
                SxDirectory dir = new SxDirectory(mParentId);
                SQLiteDatabase db = SxDatabaseHelper.database();
                if (Build.VERSION.SDK_INT >= 11)
                    db.beginTransactionNonExclusive();
                else
                    db.beginTransaction();
                long id =  dir.insertChild(dir.path()+"/"+mName+"/", "", 0, db);
                db.setTransactionSuccessful();
                db.endTransaction();

                openDirectory(id);
            }
        };
        private String mPath;
        private String mName;
        private String mError = null;
        private long mParentId;
        private String mVolume;

        public MkDirAsyncTask(long parentId, String name)
        {
            mName = name;
            mParentId = parentId;

            try {
                SxFileInfo fileInfo = new SxFileInfo(parentId);
                mPath = fileInfo.path() + name;
                mVolume = fileInfo.volume();
                long volumeId = fileInfo.volumeId();

                Cursor c = SxDatabaseHelper.database().query(SxDatabaseHelper.TABLE_FILES,
                        new String[]{Contract.COLUMN_ID},
                        Contract.COLUMN_REMOTE_PATH + "=? AND " + Contract.COLUMN_VOLUME_ID + "=" + volumeId,
                        new String[]{mPath + "/"}, null, null, null);
                if (c.moveToFirst()) {
                    mError = getString(R.string.directory_exists);
                }
                c.close();
            }
            catch (Exception ex)
            {
                mError = ex.getMessage();
                ex.printStackTrace();
            }
        }
        public void execute()
        {
            if (mError == null)
                mTask.execute(mPath);
            else
                showError(mError);
        }
        private void showError(String error)
        {
            close();
            MessageFragment.showMessage(error);
        }
        private void openDirectory(long id)
        {
            close();
            if (mSlider != null)
                mSlider.openDirRefreshParent(id);

        }
    }
}
