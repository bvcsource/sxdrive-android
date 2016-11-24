package com.skylable.sx.ui.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.sxdrive2.FilesAdapter;
import com.skylable.sx.sxdrive2.SxFileInfo;
import com.skylable.sx.sxdrive2.SyncQueue;

import java.lang.ref.WeakReference;

/**
 * Created by tangarr on 28.09.15.
 */
public class DownloadPageFragment extends Fragment implements View.OnClickListener {

    View mView = null;
    long mFileId = -1;

    ImageView mIcon;
    ImageView mIconCancel;
    TextView mTextTitle;
    TextView mTextFilename;
    TextView mTextProgress;
    ProgressBar mProgressBar;
    ProgressBar mProgressBarExport;
    Thread mThread = null;
    DownloadPageHandler mHandler;
    boolean mNeedUpdateView = true;
    String mFilename = null;
    OnPageCloseListener mCloseListener = null;
    String mError = null;
    String mTitle = null;
    private boolean mPaused = true;

    @Override
    public void onClick(View v) {
        if (v == mIconCancel)
        {
            SxApp.sSyncQueue.cancelTask(mFileId);
            if (mCloseListener != null)
                mCloseListener.onClose();
        }
    }

    public DownloadPageFragment cloneFragment() {
        DownloadPageFragment clone = new DownloadPageFragment();
        clone.mTitle = mTitle;
        clone.mFilename = mFilename;
        clone.mFileId = mFileId;
        clone.mError = mError;
        return clone;
    }


    public enum Type
    {
        OPEN,
        OPEN_WITH,
        SHARE,
        EXPORT
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.download_page_layout, container, false);
        mView.setOnClickListener(this);
        mThread = Thread.currentThread();
        mHandler = new DownloadPageHandler(this);

        mIcon = (ImageView) mView.findViewById(R.id.imageView);
        mIconCancel = (ImageView) mView.findViewById(R.id.iconCancel);
        mIconCancel.setOnClickListener(this);

        mTextTitle = (TextView) mView.findViewById(R.id.title);
        mTextFilename = (TextView) mView.findViewById(R.id.textFilename);
        mTextProgress = (TextView) mView.findViewById(R.id.textProgress);
        mProgressBar = (ProgressBar) mView.findViewById(R.id.progressBar);
        mProgressBar.setMax(1000);
        mProgressBarExport = (ProgressBar) mView.findViewById(R.id.progressBarExport);
        mProgressBarExport.setMax(1000);

        if (savedInstanceState != null)
        {
            mFileId = savedInstanceState.getLong("fileId");
            mFilename = savedInstanceState.getString("filename");
            if (savedInstanceState.containsKey("error"))
                mError = savedInstanceState.getString("error");
            mTextTitle.setText(savedInstanceState.getString("title"));
        }
        else

        if (mTitle != null)
        {
            mTextTitle.setText(mTitle);
        }
        if (mFilename != null)
        {
            mTextFilename.setText(mFilename);
            mIcon.setImageResource(FilesAdapter.getFileExtImageResource(mFilename));
        }
        if (mFileId >= 0)
            updateView(savedInstanceState);

        return mView;
    }

    private synchronized void setPaused(boolean paused)
    {
        mPaused = paused;
    }
    public synchronized boolean isPaused()
    {
        return mPaused;
    }

    @Override
    public void onResume() {
        super.onResume();
        setPaused(false);
        updateView(null);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("filename", mFilename);
        outState.putLong("fileId", mFileId);
        if (mError != null)
            outState.putString("error", mError);
        outState.putString("title", mTextTitle.getText().toString());
        super.onSaveInstanceState(outState);
    }

    public void downloadFailed(long id, String error)
    {
        if (mThread == Thread.currentThread()) {
            if (id == mFileId) {
                mProgressBar.setVisibility(View.GONE);
                mError = error;
                mTextProgress.setText(getString(R.string.download_failed)+":\n" + error);
                mTextProgress.setTextColor(Color.RED);
                mTextProgress.setGravity(Gravity.CENTER);
            }
        } else {
            Message msg = Message.obtain();
            Bundle bundle = new Bundle();
            bundle.putString("type", "downloadFailed");
            bundle.putLong("id", id);
            bundle.putString("error", error);
            msg.setData(bundle);
            mHandler.sendMessage(msg);
        }
    }

    private void updateView(Bundle savedInstance)
    {
        mNeedUpdateView = false;
        SyncQueue.Task currentTask = SxApp.sSyncQueue.getCurrentTask(null);
        if (currentTask == null) {
            mProgressBar.setVisibility(View.GONE);
            if (mError == null)
                mTextProgress.setText(R.string.task_finished);
            else
                downloadFailed(mFileId, mError);
        }
        else if (currentTask.fileId() != mFileId)
        {
            mProgressBar.setVisibility(View.GONE);
            onCurrentTaskChanged(currentTask.fileId());
        }
        else
        {
            mProgressBar.setVisibility(View.VISIBLE);

            if (currentTask.size() == 0)
            {
                mProgressBar.setIndeterminate(true);
                mTextProgress.setText("...");
            }
            else
            {
                double done = (double)currentTask.progress()/(double)currentTask.size();
                mProgressBar.setProgress((int)(1000*done));
                mTextProgress.setText(""+((int)(100*done))+"%");
            }
        }
        SxApp.sSyncQueue.setObserver(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        mNeedUpdateView = true;
        setPaused(true);
    }

    public void onCurrentTaskChanged(long id)
    {
        if (mThread == Thread.currentThread()) {
            if (isPaused())
                return;
            if (id == mFileId) {
                mProgressBar.setVisibility(View.VISIBLE);
                mTextProgress.setText("...");
            }
            else {
                int index = SxApp.sSyncQueue.indexOf(mFileId);
                if (index < 0) {
                    try {
                        SxFileInfo finfo = new SxFileInfo(mFileId);
                        if (!finfo.needUpdate()) {
                            openFile(mFileId);
                        }
                    }
                    catch (Exception ex)
                    {
                        ex.printStackTrace();
                    }

                } else {
                    mTextProgress.setText(getString(R.string.task_waiting, index));
                }
            }
        } else {
            Message msg = Message.obtain();
            Bundle bundle = new Bundle();
            bundle.putString("type", "currentTaskChanged");
            bundle.putLong("id", id);
            msg.setData(bundle);
            mHandler.sendMessage(msg);
        }
    }

    public void setup(long id, OnPageCloseListener closeListener, String title)
    {
        try {
            SxFileInfo finfo = new SxFileInfo(id);
            mFileId = id;
            mCloseListener = closeListener;
            mTitle = title;
            mFilename = finfo.filename();

            if (mView != null) {
                mTextFilename.setText(finfo.filename());
                mIcon.setImageResource(FilesAdapter.getFileExtImageResource(finfo.filename()));
                if (mTitle != null)
                    mTextTitle.setText(mTitle);
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    public void setProgress(double progress, long fileId) {
        if (mThread == Thread.currentThread()) {
            if (fileId == mFileId)
            {
                mProgressBar.setIndeterminate(false);
                mProgressBar.setProgress((int)(1000*progress));
                mTextProgress.setText(""+((int)(100*progress))+"%");
            }
        } else {
            Message msg = Message.obtain();
            Bundle bundle = new Bundle();
            bundle.putString("type", "progress");
            bundle.putDouble("value", progress);
            bundle.putLong("id", fileId);
            msg.setData(bundle);
            mHandler.sendMessage(msg);
        }
    }

    public void setExportProgress(double progress, long fileId) {
        if (mThread == Thread.currentThread()) {
            if (fileId == mFileId)
            {
                mProgressBarExport.setVisibility(View.VISIBLE);
                mProgressBarExport.setProgress((int) (1000 * progress));
            }
        } else {
            Message msg = Message.obtain();
            Bundle bundle = new Bundle();
            bundle.putString("type", "progressExport");
            bundle.putDouble("value", progress);
            bundle.putLong("id", fileId);
            msg.setData(bundle);
            mHandler.sendMessage(msg);
        }
    }

    public void openFile(long fileId)
    {
        if (mThread == Thread.currentThread()) {
            if (fileId == mFileId)
            {
                mProgressBar.setIndeterminate(false);
                mProgressBar.setProgress(1000);
                mTextProgress.setText("100%");

                if (mCloseListener != null && !mNeedUpdateView)
                    mCloseListener.onDownloadFinished(fileId);
            }
        } else {
            Message msg = Message.obtain();
            Bundle bundle = new Bundle();
            bundle.putString("type", "openFile");
            bundle.putLong("id", fileId);
            msg.setData(bundle);
            mHandler.sendMessage(msg);
        }
    }

    static class DownloadPageHandler extends Handler
    {
        WeakReference<DownloadPageFragment> weakReference;
        DownloadPageHandler(DownloadPageFragment fragment)
        {
            weakReference = new WeakReference<DownloadPageFragment>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            String msgType =  msg.getData().getString("type");
            DownloadPageFragment fragment = weakReference.get();
            if (msgType == null || fragment == null)
                return;
            switch (msgType)
            {
                case "progress":
                {
                    double progress = msg.getData().getDouble("value");
                    long id = msg.getData().getLong("id");
                    fragment.setProgress(progress, id);
                } break;
                case "progressExport":
                {
                    double progress = msg.getData().getDouble("value");
                    long id = msg.getData().getLong("id");
                    fragment.setExportProgress(progress, id);
                } break;
                case "openFile":
                {
                    long id = msg.getData().getLong("id");
                    fragment.openFile(id);
                } break;
                case "currentTaskChanged":
                {
                    long id = msg.getData().getLong("id");
                    fragment.onCurrentTaskChanged(id);
                } break;
                case "downloadFailed":
                {
                    long id = msg.getData().getLong("id");
                    String error = msg.getData().getString("error");
                    fragment.downloadFailed(id, error);

                } break;
                default:
                {
                    Log.e("PendingHandler", "INVALID TYPE: "+msgType);
                }
            }
        }
    }
    public interface OnPageCloseListener
    {
        void onClose();
        void onDownloadFinished(long id);
    }
}
