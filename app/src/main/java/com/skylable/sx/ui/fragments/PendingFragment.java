package com.skylable.sx.ui.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.sxdrive2.PendingAdapter;
import com.skylable.sx.sxdrive2.SyncQueue;

import java.lang.ref.WeakReference;

/**
 * Created by tangarr on 25.09.15.
 */
public class PendingFragment extends Fragment implements View.OnClickListener {

    PendingHandler mHandler=null;
    Thread mThread = null;
    View mView = null;

    private LinearLayout layoutCurrent;
    private LinearLayout layoutPending;
    private LinearLayout layoutEmpty;

    private ImageView mCurrentTaskIcon;
    private ImageView mCurrentTaskCancel;
    private TextView mCurrentTaskAccount;
    private TextView mCurrentTaskFilename;
    private ProgressBar mCurrentTaskProgresBar;
    private ProgressBar mCurrentTaskExportProgresBar;

    private RecyclerView mRecyclerView;
    private PendingAdapter mAdapter;

    private long currentTaskId = -1;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mHandler = new PendingHandler(this);
        mThread = Thread.currentThread();
        mView = inflater.inflate(R.layout.pending_fragment, container, false);

        layoutCurrent = (LinearLayout) mView.findViewById(R.id.currentTaskLayout);
        layoutPending = (LinearLayout) mView.findViewById(R.id.pendingLayout);
        layoutEmpty = (LinearLayout) mView.findViewById(R.id.emptyLayout);

        View currentTask = mView.findViewById(R.id.currentTask);
        mCurrentTaskAccount = (TextView) currentTask.findViewById(R.id.textAccount);
        mCurrentTaskFilename = (TextView) currentTask.findViewById(R.id.textFilename);
        mCurrentTaskIcon = (ImageView) currentTask.findViewById(R.id.iconType);
        mCurrentTaskCancel = (ImageView) currentTask.findViewById(R.id.iconCancel);
        mCurrentTaskCancel.setOnClickListener(this);

        mCurrentTaskProgresBar = (ProgressBar) currentTask.findViewById(R.id.progressBar);
        mCurrentTaskProgresBar.setMax(1000);

        mCurrentTaskExportProgresBar = (ProgressBar) currentTask.findViewById(R.id.progressBarExport);
        mCurrentTaskExportProgresBar.setMax(1000);

        mRecyclerView = (RecyclerView) mView.findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAdapter = new PendingAdapter(getActivity());
        mRecyclerView.setAdapter(mAdapter);

        updateView();
        return mView;
    }

    @Override
    public void onResume() {
        Log.d("PendingFragment", "onResume");
        super.onResume();
        SxApp.sSyncQueue.setObserver(this);
    }

    @Override
    public void onPause() {
        Log.d("PendingFragment", "onPause");
        super.onPause();
        SxApp.sSyncQueue.setObserver(null);
    }

    public void requestUpdate()
    {
        if (Thread.currentThread() == mThread)
        {
            updateView();
        }
        else {
            Message msg = Message.obtain();
            Bundle bundle = new Bundle();
            bundle.putString("type", "queueChanged");
            msg.setData(bundle);
            mHandler.sendMessage(msg);
        }
    }

    public void setCurrentTaskProgress(double progress, long fileId)
    {
        if (Thread.currentThread() == mThread) {
            if (fileId == currentTaskId)
            {
                mCurrentTaskProgresBar.setIndeterminate(false);
                mCurrentTaskProgresBar.setProgress((int)(1000*progress));
            }
        }
        else
        {
            Message msg = Message.obtain();
            Bundle bundle = new Bundle();
            bundle.putString("type", "progress");
            bundle.putDouble("value", progress);
            bundle.putLong("id", fileId);
            msg.setData(bundle);
            mHandler.sendMessage(msg);
        }
    }

    public void setCurrentTaskExportProgress(double progress, long fileId) {

        if (Thread.currentThread() == mThread) {
            if (fileId == currentTaskId)
            {
                mCurrentTaskExportProgresBar.setVisibility(View.VISIBLE);
                mCurrentTaskExportProgresBar.setProgress((int)(1000*progress));
            }
        }
        else
        {
            Message msg = Message.obtain();
            Bundle bundle = new Bundle();
            bundle.putString("type", "exportProgress");
            bundle.putDouble("value", progress);
            bundle.putLong("id", fileId);
            msg.setData(bundle);
            mHandler.sendMessage(msg);
        }

    }

    private void updateView()
    {
        boolean showEmptyLayout = true;
        SyncQueue.Task currentTask = SxApp.sSyncQueue.getCurrentTask(mHandler);
        if (currentTask != null)
        {
            showEmptyLayout = false;
            layoutCurrent.setVisibility(View.VISIBLE);
            mCurrentTaskAccount.setText(currentTask.account());
            mCurrentTaskFilename.setText(currentTask.filePath());

            int imgRes;
            switch (currentTask.taskType())
            {
                case Upload:
                    imgRes = R.drawable.task_upload_current;
                    break;
                case Download:
                    imgRes = R.drawable.task_download_current;
                    break;
                case Export:
                    imgRes = R.drawable.task_export_current;
                    break;
                default:
                    imgRes = R.drawable.task_export_current;
                    break;
            }

            mCurrentTaskIcon.setImageResource(imgRes);
            currentTaskId = currentTask.fileId();

            long progress = currentTask.progress();
            long size = currentTask.size();

            if (size == 0)
            {
                mCurrentTaskProgresBar.setIndeterminate(true);
            }
            else
            {
                double done = 1000*(double)progress/(double)size;
                mCurrentTaskProgresBar.setProgress((int)done);
            }
        }
        else {
            layoutCurrent.setVisibility(View.GONE);
            currentTaskId = -1;
        }

        mAdapter.updatePendingList();
        if (mAdapter.getItemCount() > 0)
        {
            showEmptyLayout = false;
            layoutPending.setVisibility(View.VISIBLE);
        }
        else
            layoutPending.setVisibility(View.GONE);

        if (showEmptyLayout)
            layoutEmpty.setVisibility(View.VISIBLE);
        else
            layoutEmpty.setVisibility(View.GONE);
    }

    @Override
    public void onClick(View v) {
        if (v == mCurrentTaskCancel)
        {
            SyncQueue.Task currentTask = SxApp.sSyncQueue.getCurrentTask(mHandler);
            if (currentTask != null) {
                currentTask.requestCancel();
            }
        }
    }


    static class PendingHandler extends Handler
    {
        WeakReference<PendingFragment> weakReference;
        PendingHandler(PendingFragment fragment)
        {
            weakReference = new WeakReference<PendingFragment>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            String msgType =  msg.getData().getString("type");
            PendingFragment fragment = weakReference.get();
            if (msgType == null || fragment == null)
                return;
            switch (msgType)
            {
                case "queueChanged":
                    fragment.updateView();
                    break;
                case "progress":
                {
                    double progress = msg.getData().getDouble("value");
                    long id = msg.getData().getLong("id");
                    fragment.setCurrentTaskProgress(progress, id);
                } break;
                case "exportProgress":
                {
                    double progress = msg.getData().getDouble("value");
                    long id = msg.getData().getLong("id");
                    fragment.setCurrentTaskExportProgress(progress, id);
                } break;
                default:
                {
                    Log.e("PendingHandler", "INVALID TYPE: "+msgType);
                }
            }
        }
    }

}
