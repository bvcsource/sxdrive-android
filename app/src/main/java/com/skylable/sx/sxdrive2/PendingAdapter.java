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
package com.skylable.sx.sxdrive2;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;

/**
 * Created by tangarr on 25.09.15.
 */
public class PendingAdapter extends RecyclerView.Adapter {

    private SyncQueue.Task[] mPending = null;
    private Context mContext;

    public PendingAdapter(Context context)
    {
        mContext = context;
    }

    @Override
    public TaskHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        LayoutInflater inflater = LayoutInflater.from(mContext);
        View v = inflater.inflate(R.layout.task_fragment_item, parent, false);
        v.findViewById(R.id.progressBar).setVisibility(View.GONE);

        final TaskHolder holder = new TaskHolder(v);

        View iconCancel = v.findViewById(R.id.iconCancel);
        iconCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.e("ADAPTER", "CANCEL TASK " + holder.mTask.fileId());
                        SxApp.sSyncQueue.cancelTask(holder.mTask.fileId());
                        updatePendingList();
                    }
                }, 200);
            }
        });

        return holder;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        TaskHolder taskHolder = (TaskHolder)holder;
        SyncQueue.Task task = mPending[position];
        taskHolder.mTask = task;
        taskHolder.mCurrentTaskAccount.setText(task.account());
        taskHolder.mCurrentTaskFilename.setText(task.filePath());

        int imgRes;
        switch (task.taskType())
        {
            case Upload:
                imgRes = R.drawable.task_upload;
                break;
            case Download:
                imgRes = R.drawable.task_download;
                break;
            default:
                imgRes = R.drawable.task_export;
                break;
        }

        taskHolder.mCurrentTaskIcon.setImageResource( imgRes );
    }

    @Override
    public int getItemCount() {
        if (mPending == null)
            return 0;
        else
            return mPending.length;
    }

    public void updatePendingList()
    {
        mPending = SxApp.sSyncQueue.getPendingList();
        notifyDataSetChanged();
    }

    class TaskHolder extends RecyclerView.ViewHolder
    {
        private ImageView mCurrentTaskIcon;
        private TextView mCurrentTaskAccount;
        private TextView mCurrentTaskFilename;
        SyncQueue.Task mTask;

        public TaskHolder(View itemView) {
            super(itemView);
            mCurrentTaskAccount = (TextView) itemView.findViewById(R.id.textAccount);
            mCurrentTaskFilename = (TextView) itemView.findViewById(R.id.textFilename);
            mCurrentTaskIcon = (ImageView) itemView.findViewById(R.id.iconType);
            mTask = null;
        }
    }
}
