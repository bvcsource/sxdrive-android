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
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.jni.SXClient;
import com.skylable.sx.jni.SXError;
import com.skylable.sx.util.FileOps;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by tangarr on 21.09.15.
 */
public class FilesAdapter extends RecyclerView.Adapter implements View.OnClickListener {

    static final int TYPE_DIRECTORY = 1;
    static final int TYPE_FILE= 2;
    static final int TYPE_SPACER = 3;

    private Context mContext;
    private SxDirectory mDirectory;
    private ClickListener mClickListener;
    private ArrayList<SxDirectory.SxFile> mFiles;
    private static HashMap<Long, Pair<BackgroundRefreshTask, SxDirectory>> sRefreshingTasks = new HashMap<>();
    private static ArrayList<Long> sPendingTasks = new ArrayList<>();
    private long mUnfoldedFileId = -1;
    private View mUnfoldedView = null;
    private int mUnfoldedItemIndex = -1;
    private  boolean mHideFiles = false;

    public static String sSxWeb = null;
    public static String sSxShare = null;

    private HashMap<RecyclerView.ViewHolder, Long> mViewHolderBindings = new HashMap<>();

    public boolean isRefreshing()
    {
        return sRefreshingTasks.containsKey(mDirectory.id());
    }

    public void refreshInBackground(final BackgroundRefresh callback)
    {
        BackgroundRefreshTask task;

        if (sRefreshingTasks.containsKey(mDirectory.id()))
        {
            task = sRefreshingTasks.get(mDirectory.id()).first;
            task.updateTask(callback, this);
        }
        else {
            task = new BackgroundRefreshTask(callback, this, mClickListener);
            sRefreshingTasks.put(mDirectory.id(), new Pair(task, mDirectory));

            if (sRefreshingTasks.size() == 1)
                task.execute();
            else
                sPendingTasks.add(mDirectory.id());
        }
    }

    private void loadFileList()
    {
        mFiles = mDirectory.fileList(mHideFiles);
    }

    public FilesAdapter(Context context, long directoryId, ClickListener clickListener, BackgroundRefresh refreshCallback, boolean hideFiles)
    {
        if (sRefreshingTasks.containsKey(directoryId)) {
            mDirectory = sRefreshingTasks.get(directoryId).second;
        }
        else {
            mDirectory = new SxDirectory(directoryId);
        }
        mHideFiles = hideFiles;
        mContext = context;
        mClickListener = clickListener;
        loadFileList();
        refreshInBackground(refreshCallback);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        RecyclerView.ViewHolder vh = null;
        View v;

        if (viewType == TYPE_SPACER)
        {
            v = new View(mContext);
            v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SxApp.convertDp2Px(90)));
            return new SpacerHolder(v);
        }


        LayoutInflater inflater = LayoutInflater.from(mContext);
        v = inflater.inflate(R.layout.files_item_file, parent, false);

        ImageView more = (ImageView) v.findViewById(R.id.more);
        View upperView = v.findViewById(R.id.upperLayout);
        final View options = v.findViewById(R.id.lowerLayout);
        options.setVisibility(View.GONE);

        options.findViewById(R.id.option_delete).setOnClickListener(this);
        options.findViewById(R.id.option_export).setOnClickListener(this);
        options.findViewById(R.id.option_favourite).setOnClickListener(this);
        options.findViewById(R.id.option_openwith).setOnClickListener(this);
        options.findViewById(R.id.option_rename).setOnClickListener(this);
        options.findViewById(R.id.option_share).setOnClickListener(this);
        final View share = options.findViewById(R.id.option_sharelink);
        share.setOnClickListener(this);

        switch (viewType)
        {
            case TYPE_DIRECTORY: {
                ((ImageView) upperView.findViewById(R.id.imageView)).setImageResource(R.drawable.directory);
                ((TextView) upperView.findViewById(R.id.textSize)).setVisibility(View.GONE);
                options.findViewById(R.id.optionsFileOnly).setVisibility(View.GONE);

                vh = new DirectoryHolder(v);
                final DirectoryHolder finalVh = (DirectoryHolder) vh;
                upperView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mClickListener != null) {
                            v.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mClickListener.onClicked(ItemType.DIRECTORY, finalVh.id);
                                }
                            }, 200);
                        }
                    }
                });
            } break;
            case TYPE_FILE: {
                vh = new FileHolder(v);
                final FileHolder finalVh = (FileHolder) vh;
                upperView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mClickListener != null) {
                            v.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mClickListener.onClicked(ItemType.FILE, finalVh.id);
                                }
                            }, 200);
                        }
                    }
                });
            } break;
        }

        final RecyclerView.ViewHolder tmpHolder= vh;
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (options.getVisibility() == View.VISIBLE)
                {
                    options.setVisibility(View.GONE);
                    mUnfoldedFileId = -1;
                    mUnfoldedView = null;
                    mUnfoldedItemIndex = -1;
                }
                else
                {
                    options.setVisibility(View.VISIBLE);
                    if (mUnfoldedView != null)
                    {
                        mUnfoldedView.setVisibility(View.GONE);
                    }
                    mUnfoldedView = options;

                    if (tmpHolder instanceof FileHolder)
                    {
                        mUnfoldedFileId = ((FileHolder)tmpHolder).id;
                        if (sSxWeb != null || sSxShare != null) {
                            share.setVisibility(View.VISIBLE);
                        }
                    }
                    else if (tmpHolder instanceof DirectoryHolder)
                    {
                        mUnfoldedFileId = ((DirectoryHolder)tmpHolder).id;
                        if (sSxShare != null) {
                            share.setVisibility(View.VISIBLE);
                        }
                    }
                    View empty = options.findViewById(R.id.option_empty);
                    empty.setVisibility(share.getVisibility());
                    mUnfoldedItemIndex = tmpHolder.getAdapterPosition();
                }
                notifyItemChanged(tmpHolder.getAdapterPosition());

            }
        });


        return vh;
    }

    static final String[] ext_image = {"png", "jpg", "jpeg", "bmp", "gif", "tiff"};
    static final String[] ext_music = {"mp3", "wma", "wave", "ogg"};
    static final String[] ext_movie = {"avi", "mov", "mp4", "wmv", "mkv", "mpg", "mpeg", "mp2"};
    static final String[] ext_code = {"xml", "html", "php", "js"};
    static final String[] ext_doc = {"doc", "docx", "odt", "rtf", "txt"};
    static final String[] ext_xls = {"xls", "xlsx", "ods"};
    static final String[] ext_pptx = {"ppt", "pptx", "odp"};
    static final String[] ext_pdf = {"pdf"};

    static final Pair<Integer, String[]>[] extensions = new Pair[]{
            new Pair<Integer, String[]>(R.drawable.file_image, ext_image),
            new Pair<Integer, String[]>(R.drawable.file_music, ext_music),
            new Pair<Integer, String[]>(R.drawable.file_movie, ext_movie),
            new Pair<Integer, String[]>(R.drawable.file_code, ext_code),
            new Pair<Integer, String[]>(R.drawable.file_txt, ext_doc),
            new Pair<Integer, String[]>(R.drawable.file_calc, ext_xls),
            new Pair<Integer, String[]>(R.drawable.file_presentation, ext_pptx),
            new Pair<Integer, String[]>(R.drawable.file_pdf, ext_pdf),
    };

    public static int getFileExtImageResource(String name)
    {   int index = name.lastIndexOf(".");
        if (index < 0)
            return R.drawable.file_generic;
        String ext = name.substring(index+1).toLowerCase();

        for (Pair p:extensions) {
            for (String e: (String[])p.second)
            {
                if (TextUtils.equals(e,ext))
                    return (Integer)p.first;
            }
        }
        return R.drawable.file_generic;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {

        if (holder instanceof SpacerHolder)
            return;

        SxDirectory.SxFile f = mFiles.get(position);
        View options = null;
        long id = f.id();

        mViewHolderBindings.put(holder, id);

        Integer starId = null;
        Integer optionStarrId = R.drawable.option_favourite_full;
        try {
            SxFileInfo fileInfo = new SxFileInfo(id);
            if (fileInfo.isFavourite()) {
                starId = (fileInfo.needUpdate() ? R.drawable.star_empty : R.drawable.star);
                optionStarrId = R.drawable.option_favourite;
            }
        }
        catch (Exception ex)
        {
            Log.e("FilesAdapter", "CRASH onBindViewHolder, position="+position);
            if (mDirectory == null)
                Log.e("mDirectory", "is null");
            else
            {
                Log.e("mDirectory", mDirectory.path());
            }
            Log.e("File", "id=" + mFiles.get(position).id() + ", " + mFiles.get(position).name());
            refreshInBackground(new BackgroundRefresh() {
                @Override
                public void onFinish(boolean changed) {
                    Log.e("FilesAdapter", "emergency refreshInBackground finished with value "+changed);
                }
            });
        }

        ImageView starView = null;

        if (holder instanceof DirectoryHolder)
        {
            DirectoryHolder dh = (DirectoryHolder)holder;

            dh.mName.setText(f.name());
            dh.id = f.id();
            options = dh.mOptions;
            starView = dh.mStar;
            dh.mOptionStar.setImageResource(optionStarrId);
        }
        else if (holder instanceof FileHolder)
        {
            FileHolder fh = (FileHolder)holder;

            fh.mName.setText(f.name());
            fh.mSize.setText(FileOps.humanReadableByteCount(f.size(), true));

            String created_at = f.created_at();
            if (created_at != null) {
                fh.mDate.setText(created_at);
                fh.mDate.setVisibility(View.VISIBLE);
            }
            else
                fh.mDate.setVisibility(View.GONE);

            fh.id = f.id();
            fh.mImage.setImageResource(getFileExtImageResource(f.name()));
            fh.mOptionStar.setImageResource(optionStarrId);
            options = fh.mOptions;
            starView = fh.mStar;
        }
        else
            return;

        View shareLink = options.findViewById(R.id.option_sharelink);
        View empty = options.findViewById(R.id.option_empty);
        if (holder instanceof DirectoryHolder)
        {
            if (sSxShare != null)
                shareLink.setVisibility(View.VISIBLE);
        }
        else {
            if (sSxWeb != null || sSxShare != null)
                shareLink.setVisibility(View.VISIBLE);
        }
        empty.setVisibility(shareLink.getVisibility());


        if (starId == null)
            starView.setVisibility(View.INVISIBLE);
        else
        {
            starView.setVisibility(View.VISIBLE);
            starView.setImageResource(starId);
        }

        if (id == mUnfoldedFileId)
        {
            options.setVisibility(View.VISIBLE);
            if (mUnfoldedView != null && mUnfoldedView != options)
                mUnfoldedView.setVisibility(View.GONE);
            mUnfoldedView = options;
        }
        else
        {
            options.setVisibility(View.GONE);
            if (mUnfoldedView == options)
                mUnfoldedView = null;
        }
    }

    @Override
    public int getItemCount() {
        return mFiles.size()+1;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == mFiles.size())
            return TYPE_SPACER;
        return mFiles.get(position).isDir() ? TYPE_DIRECTORY : TYPE_FILE;
    }

    @Override
    public void onClick(final View v) {
        Runnable action = new Runnable() {
            @Override
            public void run() {
                mUnfoldedView.setVisibility(View.GONE);
                mUnfoldedView = null;
                int index = mUnfoldedItemIndex;
                mUnfoldedItemIndex = -1;

                switch (v.getId())
                {
                    case R.id.option_openwith: {
                        mClickListener.onOpenWith(mUnfoldedFileId);
                    } break;
                    case R.id.option_share: {
                        mClickListener.onShare(mUnfoldedFileId);
                    } break;
                    case R.id.option_export: {
                        mClickListener.onExport(mUnfoldedFileId);

                    } break;
                    case R.id.option_favourite: {
                        mClickListener.onFavourite(mUnfoldedFileId);
                    } break;
                    case R.id.option_rename: {
                        mClickListener.onRename(mUnfoldedFileId);
                    } break;
                    case R.id.option_delete: {
                        mClickListener.onRemove(mUnfoldedFileId);
                    } break;
                    case R.id.option_sharelink: {
                        mClickListener.onPublicLink(mUnfoldedFileId);
                    } break;
                }
                mUnfoldedFileId = -1;
                notifyItemChanged(index);
            }
        };
        v.postDelayed(action, 100);
    }

    public void refreshFile(long id) {

        for (RecyclerView.ViewHolder h : mViewHolderBindings.keySet())
        {

            if (mViewHolderBindings.get(h) == id)
            {
                notifyItemChanged(h.getLayoutPosition());
                return;
            }
        }
    }

    public enum ItemType
    {
        DIRECTORY,
        FILE,
        SPACER
    }

    public interface ClickListener
    {
        void onClicked(ItemType type, long id);
        void onOpenWith(long fileId);
        void onShare(long fileId);
        void onExport(long fileId);
        void onFavourite(long fileId);
        void onRename(long fileId);
        void onRemove(long fileId);
        void onPublicLink(long mUnfoldedFileId);
        void accountLocked();
    }

    class DirectoryHolder extends RecyclerView.ViewHolder
    {
        TextView mName;
        long id = -1;
        View mOptions;
        ImageView mStar;
        ImageView mOptionStar;

        public DirectoryHolder(View itemView) {
            super(itemView);
            mName = (TextView) itemView.findViewById(R.id.textName);
            mOptions = itemView.findViewById(R.id.lowerLayout);
            mStar = (ImageView) itemView.findViewById(R.id.imageFavourite);
            mOptionStar = (ImageView) itemView.findViewById(R.id.option_favourite_icon);
        }
    }

    class FileHolder extends RecyclerView.ViewHolder
    {
        TextView mName;
        TextView mSize;
        TextView mDate;
        ImageView mImage;
        ImageView mStar;
        ImageView mOptionStar;
        long id = -1;
        View mOptions;

        public FileHolder(View itemView) {
            super(itemView);
            mName = (TextView) itemView.findViewById(R.id.textName);
            mSize = (TextView) itemView.findViewById(R.id.textSize);
            mDate = (TextView) itemView.findViewById(R.id.textDate);
            mImage = (ImageView) itemView.findViewById(R.id.imageView);
            mOptions = itemView.findViewById(R.id.lowerLayout);
            mStar = (ImageView) itemView.findViewById(R.id.imageFavourite);
            mOptionStar = (ImageView) itemView.findViewById(R.id.option_favourite_icon);
        }
    }

    class SpacerHolder extends RecyclerView.ViewHolder
    {
        public SpacerHolder(View itemView) {
            super(itemView);
        }
    }

    public interface BackgroundRefresh
    {
        void onFinish(boolean changed);
    }

    static class BackgroundRefreshTask extends SupportAsyncTask<Void, Void, Boolean>
    {
        private SXClient client;
        private String cluster = null;
        private SxDirectory tmpDir;
        private BackgroundRefresh mCallback;
        private FilesAdapter mAdapter;
        private long mId;
        private ClickListener mClickListener;

        public BackgroundRefreshTask(BackgroundRefresh callback, FilesAdapter adapter, ClickListener clickListener)
        {
            mCallback = callback;
            mAdapter = adapter;
            mId = mAdapter.mDirectory.id();
            mClickListener = clickListener;
        }
        public void updateTask(BackgroundRefresh callback, FilesAdapter adapter)
        {
            mCallback = callback;
            mAdapter = adapter;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            client = SxApp.getCurrentClient();
            if (client == null || cluster == null)
                return false;
            tmpDir = new SxDirectory(mId);
            return tmpDir.updateDirectory(client);
        }

        @Override
        protected void onPreExecute() {
            SxAccount account = SxAccount.loadAccount(SxApp.currentAccountName());
            if (account != null)
                cluster = account.cluster();
        }

        protected void onPostExecute(Boolean aBoolean) {
            if (client!=null && client.getErrNum() == SXError.SXE_EAUTH) {
                mClickListener.accountLocked();
                mCallback.onFinish(false);
                FilesAdapter.sRefreshingTasks.clear();
                return;
            }
            if (aBoolean) {
                mAdapter.mDirectory = tmpDir;
                mAdapter.mFiles = tmpDir.fileList(mAdapter.mHideFiles);
                mAdapter.notifyDataSetChanged();
            }
            mCallback.onFinish(aBoolean);
            FilesAdapter.sRefreshingTasks.remove(mId);
            if (!sPendingTasks.isEmpty())
            {
                long id = sPendingTasks.get(0);
                sPendingTasks.remove(0);
                sRefreshingTasks.get(id).first.execute();
            }
        }
    }
}
