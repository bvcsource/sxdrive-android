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
import android.support.v4.content.SupportAsyncTask;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.auth.SxClientEx;
import com.skylable.sx.jni.SXClient;

import java.util.ArrayList;

/**
 * Created by tangarr on 21.09.15.
 */
public class VolumesAdapter extends RecyclerView.Adapter {

    private Context mContext;
    private SxAccount mSxAccount;
    private ClickListener mClickListener;
    ArrayList<SxVolume> list;
    private String mVcluster = null;

    static SupportAsyncTask<Void, Void, Void> mRefreshTask = null;

    public VolumesAdapter(Context context, SxAccount account, ClickListener clickListener)
    {
        mContext = context;
        mSxAccount = account;
        mClickListener = clickListener;
        list = mSxAccount.volumes();
        mVcluster = mSxAccount.vcluster();
    }

    public void requestRefresh()
    {
        if (mRefreshTask != null)
        return;

        mRefreshTask = new SupportAsyncTask<Void, Void, Void>() {
            private SXClient client;
            ArrayList<SxVolume> tmpList = null;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected Void doInBackground(Void... params) {
                client = SxApp.getCurrentClient();
                if (client != null) {
                    mSxAccount.updateVolumeList(client);
                    tmpList = mSxAccount.volumes();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void x) {
                mRefreshTask = null;
                if (mSxAccount.isLocked()) {
                    mClickListener.accountLocked();
                }
                else if (tmpList != null) {
                    list = tmpList;
                    notifyDataSetChanged();
                }
            }
        };

        mRefreshTask.execute();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View v = inflater.inflate(R.layout.files_item_volume, parent, false);
        RecyclerView.ViewHolder vh = new VolumeHolder(v);
        final VolumeHolder finalVh = (VolumeHolder) vh;
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mClickListener != null) {
                    v.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mClickListener.onClicked(finalVh.id, finalVh.volume);
                        }
                    }, 200);
                }
            }
        });
        return vh;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            VolumeHolder volHolder = (VolumeHolder) holder;
            SxVolume sxVolume = list.get(position);

            String volumeName = sxVolume.name();
            if (!TextUtils.isEmpty(mVcluster)) {
                if (volumeName.startsWith(mVcluster+"."))
                    volumeName = volumeName.substring(mVcluster.length()+1);
            }
            volHolder.volumeName.setText(volumeName);
            volHolder.size.setText(sxVolume.sizeFormated());
            volHolder.used.setMax(1000);
            volHolder.used.setProgress((int) (1000 * sxVolume.used()));

            int imgRes;
            switch (sxVolume.encrypted())
            {
                case 0: imgRes = R.drawable.volume; break;
                case 1:
                case 3:
                    imgRes = R.drawable.volume_locked; break;
                case 2:
                case 4:
                    imgRes = R.drawable.volume_unlocked; break;
                default: imgRes = -1;
            }

            volHolder.image.setImageResource(imgRes);
            volHolder.id = sxVolume.volumeId();
            volHolder.volume = sxVolume;
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public class VolumeHolder extends RecyclerView.ViewHolder
    {
        TextView volumeName;
        TextView size;
        ProgressBar used;
        ImageView image;
        long id = -1;
        SxVolume volume = null;

        public VolumeHolder(View itemView) {
            super(itemView);
            volumeName = (TextView) itemView.findViewById(R.id.textVolume);
            size = (TextView) itemView.findViewById(R.id.textSize);
            used = (ProgressBar) itemView.findViewById(R.id.progressBar);
            image = (ImageView) itemView.findViewById(R.id.imageView);
        }
    }

    public interface ClickListener
    {
        void onClicked(long id, SxVolume volume);
        void accountLocked();
    }
}
