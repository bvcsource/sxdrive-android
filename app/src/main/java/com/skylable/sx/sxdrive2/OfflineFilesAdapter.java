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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.skylable.sx.R;
import com.skylable.sx.providers.Contract;
import com.skylable.sx.util.FileOps;

import java.util.ArrayList;

/**
 * Created by tangarr on 23.10.15.
 */
public class OfflineFilesAdapter extends RecyclerView.Adapter implements View.OnClickListener {

    private ArrayList<SxDirectory.SxFile> mFiles = new ArrayList<>();
    private String mAccountName;
    private Context mContext;

    private View mUnfoldedView = null;
    private int mUnfoldedItemIndex = -1;
    private ClickListener mClickListener;

    public OfflineFilesAdapter(String account, Context context, ClickListener clickListener)
    {
        mContext = context;
        mAccountName = account;
        mClickListener = clickListener;
        refresh(false);
    }

    public String accountName()
    {
        return mAccountName;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View v = inflater.inflate(R.layout.files_item_file, parent, false);
        v.findViewById(R.id.textPath).setVisibility(View.VISIBLE);
        final OfflineFileHolder holder = new OfflineFileHolder(v);
        holder.mLowerLayout.setVisibility(View.GONE);

        holder.mLowerLayout.findViewById(R.id.option_delete).setOnClickListener(this);
        holder.mLowerLayout.findViewById(R.id.option_export).setOnClickListener(this);
        holder.mLowerLayout.findViewById(R.id.option_favourite).setOnClickListener(this);
        holder.mLowerLayout.findViewById(R.id.option_openwith).setOnClickListener(this);
        holder.mLowerLayout.findViewById(R.id.option_rename).setOnClickListener(this);
        holder.mLowerLayout.findViewById(R.id.option_share).setOnClickListener(this);

        v.findViewById(R.id.upperLayout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mClickListener != null)
                        {
                            mClickListener.onClicked(mFiles.get(holder.mItemIndex).id());
                        }
                    }
                }, 200);

            }
        });

        holder.mIconMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mUnfoldedView == holder.mLowerLayout) {
                    mUnfoldedView.setVisibility(View.GONE);
                    mUnfoldedView = null;
                    mUnfoldedItemIndex = -1;
                } else {
                    if (mUnfoldedView != null) {
                        mUnfoldedView.setVisibility(View.GONE);
                    }
                    mUnfoldedItemIndex = holder.mItemIndex;
                    mUnfoldedView = holder.mLowerLayout;
                    mUnfoldedView.setVisibility(View.VISIBLE);
                }
                notifyItemChanged(holder.mItemIndex);
            }
        });

        return holder;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder _holder, int position) {

        SxDirectory.SxFile file = mFiles.get(position);

        OfflineFileHolder holder = (OfflineFileHolder) _holder;
        holder.mTextName.setText(file.name());
        holder.mTextSize.setText(FileOps.humanReadableByteCount(file.size(), true));
        holder.mTextParentPath.setText(file.parentPath());
        holder.mIcon.setImageResource(FilesAdapter.getFileExtImageResource(file.name()));
        holder.mItemIndex = position;

        if (file.starred())
        {
            try {
                SxFileInfo info = new SxFileInfo(file.id());
                holder.mIconStarr.setVisibility(View.VISIBLE);
                holder.mIconStarr.setImageResource(info.needUpdate() ? R.drawable.star_empty : R.drawable.star);
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }
        else
            holder.mIconStarr.setVisibility(View.INVISIBLE);

        if (position == mUnfoldedItemIndex)
        {
            if (mUnfoldedView != holder.mLowerLayout) {
                if (mUnfoldedView != null)
                    mUnfoldedView.setVisibility(View.GONE);
                mUnfoldedView = holder.mLowerLayout;
                mUnfoldedView.setVisibility(View.VISIBLE);
            }
        }
        else {
            if (mUnfoldedView == holder.mLowerLayout) {
                mUnfoldedView.setVisibility(View.GONE);
                mUnfoldedView = null;
            }
        }
    }

    @Override
    public int getItemCount() {
        return mFiles.size();
    }

    public void refresh(boolean notify) {
        mUnfoldedView = null;
        mUnfoldedItemIndex = -1;

        if (mAccountName == null)
        {
            if (!mFiles.isEmpty()) {
                mFiles.clear();
                notifyDataSetChanged();
            }
            return;
        }

        ArrayList<SxDirectory.SxFile> result = new ArrayList<>();
        SQLiteDatabase db = SxDatabaseHelper.database();
        String rawQuery =
                String.format("SELECT f.%s, f.%s, f.%s, f.%s, v.%s, f.%s ", Contract.COLUMN_ID, Contract.COLUMN_REMOTE_PATH, Contract.COLUMN_STARRED, Contract.COLUMN_SIZE, Contract.COLUMN_VOLUME, Contract.COLUMN_REMOTE_REV) +
                String.format("FROM %s f, %s v, %s a ", SxDatabaseHelper.TABLE_FILES, SxDatabaseHelper.TABLE_VOLUMES, SxDatabaseHelper.TABLE_ACCOUNTS) +
                String.format("WHERE f.%s=v.%s AND a.%s=? AND f.%s>0 AND a.%s=v.%s AND f.%s not like ?",
                        Contract.COLUMN_VOLUME_ID,
                        Contract.COLUMN_ID,
                        Contract.COLUMN_ACCOUNT,
                        Contract.COLUMN_STARRED,
                        Contract.COLUMN_ID,
                        Contract.COLUMN_ACCOUNT_ID,
                        Contract.COLUMN_REMOTE_PATH
                ) +
                String.format("ORDER BY v.%s, f.%s;", Contract.COLUMN_VOLUME, Contract.COLUMN_REMOTE_PATH);
        Cursor c = db.rawQuery(rawQuery, new String[] {mAccountName, "%/"});
        while (c.moveToNext())
        {
            long id = c.getLong(0);
            String path = c.getString(1);
            boolean starred = c.getInt(2) > 0;
            long size = c.getLong(3);
            String volume = c.getString(4);
            String rev = c.getString(5);

            if (path.endsWith("/.sxnewdir"))
                continue;

            SxDirectory.SxFile f = new SxDirectory.SxFile(id, volume+path, starred, size, rev);
            result.add(f);
        }
        c.close();
        mFiles = result;
        if (notify)
            notifyDataSetChanged();
    }

    @Override
    public void onClick(final View v) {

        if (mUnfoldedItemIndex == -1)
        {
            Toast.makeText(mContext, "mUnfoldedItemIndex value lost, this shold not happen", Toast.LENGTH_LONG).show();
            return;
        }

        final SxDirectory.SxFile file = mFiles.get(mUnfoldedItemIndex);
        final long id = file.id();
        v.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mClickListener != null) {
                    mUnfoldedView.setVisibility(View.GONE);
                    mUnfoldedView = null;
                    int index = mUnfoldedItemIndex;
                    mUnfoldedItemIndex = -1;

                    switch (v.getId()) {
                        case R.id.option_openwith: {
                            mClickListener.onOpenWith(id);
                        } break;
                        case R.id.option_share: {
                            mClickListener.onShare(id);
                        } break;
                        case R.id.option_export: {
                            mClickListener.onExport(id);

                        } break;
                        case R.id.option_favourite: {
                            mClickListener.onFavourite(id);

                            SxDirectory.SxFile f = new SxDirectory.SxFile(id, file.path(), !file.starred(), file.size(), file.revision());
                            mFiles.set(index, f);
                            notifyItemChanged(index);
                        } break;
                        case R.id.option_rename: {
                            mClickListener.onRename(id);
                        } break;
                        case R.id.option_delete: {
                            mClickListener.onRemove(id);
                        } break;
                    }
                }
            }
        }, 100);

    }

    class OfflineFileHolder extends RecyclerView.ViewHolder
    {
        ImageView mIcon;
        ImageView mIconStarr;
        ImageView mIconMore;
        TextView mTextName;
        TextView mTextParentPath;
        TextView mTextSize;
        View mLowerLayout;
        int mItemIndex = -1;

        public OfflineFileHolder(View itemView) {
            super(itemView);
            mIcon = (ImageView) itemView.findViewById(R.id.imageView);
            mIconStarr = (ImageView) itemView.findViewById(R.id.imageFavourite);
            mIconMore = (ImageView) itemView.findViewById(R.id.more);
            mTextName = (TextView) itemView.findViewById(R.id.textName);
            mTextParentPath = (TextView) itemView.findViewById(R.id.textPath);
            mTextSize = (TextView) itemView.findViewById(R.id.textSize);
            mLowerLayout = itemView.findViewById(R.id.lowerLayout);

        }
    }

    public interface ClickListener
    {
        void onClicked(long fileId);
        void onOpenWith(long fileId);
        void onShare(long fileId);
        void onExport(long fileId);
        void onFavourite(long fileId);
        void onRename(long fileId);
        void onRemove(long fileId);
    }

}
