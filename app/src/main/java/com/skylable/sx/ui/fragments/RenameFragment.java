package com.skylable.sx.ui.fragments;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.content.SupportAsyncTask;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.jni.SXClient;
import com.skylable.sx.providers.Contract;
import com.skylable.sx.sxdrive2.SxDatabaseHelper;
import com.skylable.sx.sxdrive2.SxFileInfo;

import java.util.ArrayList;

/**
 * Created by tangarr on 01.10.15.
 */
public class RenameFragment extends InputDialogFragment {

    static private RenameAsyncTask task = null;
    private long mSelectedFileId = -1;
    private boolean isDir = false;
    static private OnFragmentRefreshListener mRefreshListener;
    private View mView = null;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = super.onCreateView(inflater, container, savedInstanceState);

        setSecondaryTitle(null);
        setTextInputEnabled(true);
        setOkButtonText(getString(R.string.action_rename));
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
                setTitle(getString(R.string.enter_new_directoryname));
            else
                setTitle(getString(R.string.enter_new_filename));
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    public void setup(long fileId, OnFragmentRefreshListener refreshListener)
    {
        mSelectedFileId = fileId;
        mRefreshListener = refreshListener;
        if (mView != null) {
            updateTitle();
        }
    }

    public void reSetup(OnFragmentRefreshListener refreshListener)
    {
        mRefreshListener = refreshListener;
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
            setInputError(getString(isDir ? R.string.dirname_empty : R.string.filename_empty));
            return;
        }
        else if (name.contains("/") || name.equals(".") || name.equals(".."))
        {
            setInputError(getString(isDir ? R.string.dirname_invalid : R.string.filename_invalid));
            return;
        }
        setTitle(getString(R.string.processing));
        task = new RenameAsyncTask(mSelectedFileId, name);
        task.execute();
    }

    class RenameAsyncTask
    {
        private SupportAsyncTask<Void, Void, String> mTask = new SupportAsyncTask<Void, Void, String>()
        {
            private SXClient mClient;
            @Override
            protected void onPreExecute() {
                showProgressBar();
                setLocket(true);
                hideSoftKeyboard();
            }

            @Override
            protected String doInBackground(Void... params) {
                mClient = SxApp.getCurrentClient();
                if (mClient == null)
                    return getString(R.string.no_data_connection);

                if (!mClient.rename(mSource, mDest, mVolume))
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

                if (mDest.endsWith("/"))
                {
                    String sql = String.format("UPDATE %s SET %s=%s || substr(%s, %d) where %s like %s AND %s=%d",
                            SxDatabaseHelper.TABLE_FILES,
                            Contract.COLUMN_REMOTE_PATH,
                            DatabaseUtils.sqlEscapeString(mDest),
                            Contract.COLUMN_REMOTE_PATH,
                            mSource.length()+1,
                            Contract.COLUMN_REMOTE_PATH,
                            DatabaseUtils.sqlEscapeString(mSource+"%"),
                            Contract.COLUMN_VOLUME_ID,
                            mVolumeId
                            );
                    SxDatabaseHelper.database().execSQL(sql);

                    ArrayList<Long> list = findFavouriteFiles(mVolumeId, mDest);
                    for (long id : list)
                        SxApp.sSyncQueue.requestDownload(id, true);
                }
                else {
                    ContentValues values = new ContentValues();
                    values.put(Contract.COLUMN_REMOTE_PATH, mDest);

                    SxDatabaseHelper.database().update(SxDatabaseHelper.TABLE_FILES,
                            values,
                            String.format("%s = %d", Contract.COLUMN_ID, mFileId),
                            null);
                    if (mStarred)
                    {
                        SxApp.sSyncQueue.requestDownload(mFileId, true);
                    }
                }
                mRefreshListener.onRefresh();
                close();
            }
        };
        private String mSource;
        private String mDest;

        private String mError = null;
        private long mFileId;
        private String mVolume;
        private boolean mStarred;
        private long mVolumeId;

        public RenameAsyncTask(long fileId, String name)
        {
            try {
                mFileId = fileId;
                SxFileInfo fileInfo = new SxFileInfo(mFileId);

                mSource = fileInfo.path();
                int len = mSource.length() - fileInfo.filename().length();
                if (fileInfo.isDir())
                    len--;

                mDest = mSource.substring(0, len) + name;
                if (fileInfo.isDir())
                    mDest += "/";

                mVolume = fileInfo.volume();
                mVolumeId = fileInfo.volumeId();
                mStarred = fileInfo.isFavourite();

                if (TextUtils.equals(mSource, mDest)) {
                    mError = "##DO_NOTHING##";
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                mError = ex.getMessage();
                mFileId = -1;
            }
        }
        public void execute()
        {
            if (mError == null)
                mTask.execute();
            else {
                task = null;
                if (mError.equals("##DO_NOTHING##"))
                    close();
                else
                    showError(mError);
            }
        }
        private void showError(String error)
        {
            close();
            MessageFragment.showMessage(error);
        }
    }

    ArrayList<Long> findFavouriteFiles(Long volumeId, String parent)
    {
        ArrayList<Long> result = new ArrayList<>();

        String rawQuery =
                String.format("SELECT %s from %s WHERE ", Contract.COLUMN_ID, SxDatabaseHelper.TABLE_FILES) +
                String.format("%s = %d AND ", Contract.COLUMN_VOLUME_ID, volumeId) +
                String.format("%s like %s AND ", Contract.COLUMN_REMOTE_PATH, DatabaseUtils.sqlEscapeString(parent+"%")) +
                String.format("%s not like '%s' AND ", Contract.COLUMN_REMOTE_PATH, "%/") +
                String.format("%s not like '%s' AND ", Contract.COLUMN_REMOTE_PATH, "%/.sxnewdir") +
                String.format("%s = 1;", Contract.COLUMN_STARRED);

        SQLiteDatabase db = SxDatabaseHelper.database();
        Cursor c = db.rawQuery(rawQuery, null);

        while (c.moveToNext())
        {
            result.add(c.getLong(0));
        }
        c.close();

        return result;
    }
}

