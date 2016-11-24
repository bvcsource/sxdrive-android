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
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.skylable.sx.R;
import com.skylable.sx.ui.fragments.ExportFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by tangarr on 09.10.15.
 */
public class ExportAdapter extends FragmentPagerAdapter {

    ArrayList<LocalDirectoryFragment> mFragments = new ArrayList<>();
    Context mContext;
    ExportFragment mExportFragment;
    FragmentManager mFragmentManager;


    public ExportAdapter(FragmentManager fm, Context context, ExportFragment exportFragment, String[] fragmentTags) {
        super(fm);
        mFragmentManager = fm;
        mContext = context;
        mExportFragment = exportFragment;

        if (fragmentTags == null) {
            LocalDirectoryFragment f = new LocalDirectoryFragment();
            f.setup(new File("/"), context, exportFragment);
            mFragments.add(f);

            File storage = new File("/storage/");
            File mnt = new File("/storage/");

            if (storage.isDirectory())
            {
                openDirectory(storage);
            }
            else if (mnt.exists())
            {
                openDirectory(mnt);
            }
        }
        else
        {
            for (int i=0; i<fragmentTags.length; i++)
            {
                String tag = fragmentTags[i];
                LocalDirectoryFragment f = (LocalDirectoryFragment) fm.findFragmentByTag(tag);
                f.reinit(mContext, mExportFragment);
                mFragments.add(f);
            }
        }
    }

    public String path(int index)
    {
        return mFragments.get(index).path();
    }

    public String[] getFragmentsTags()
    {
        String tags[] = new String[mFragments.size()];
        for (int i=0; i<mFragments.size(); i++)
        {
            tags[i] = mFragments.get(i).getTag();
        }
        return tags;
    }

    @Override
    public Fragment getItem(int position) {
        return mFragments.get(position);
    }

    @Override
    public int getItemPosition(Object object) {
        if (mFragments.contains(object)) {
            int index = mFragments.indexOf(object);
            return index;
        }
        else
            return POSITION_NONE;
    }

    @Override
    public int getCount() {
        return mFragments.size();
    }

    public int openDirectory(File directory)
    {
        LocalDirectoryFragment f = new LocalDirectoryFragment();
        f.setup(directory, mContext, mExportFragment);
        mFragments.add(f);
        notifyDataSetChanged();
        return mFragments.size() - 1;
    }

    public void removeAfter(int index) {
        if (index+1 < mFragments.size()) {
            FragmentTransaction transaction = mFragmentManager.beginTransaction();
            for (int i = mFragments.size() - 1; i > index; i--) {
                LocalDirectoryFragment f = mFragments.get(i);
                mFragments.remove(i);
                transaction.remove(f);
            }
            transaction.commit();
            notifyDataSetChanged();
        }
    }

    static public class LocalDirectoryFragment extends Fragment
    {
        View mView = null;
        RecyclerView mRecyclerView = null;
        LocalDirectoryAdapter mAdapter = null;
        Context mContext = null;
        ExportFragment mExportFragment = null;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            mView = inflater.inflate(R.layout.recycler_view_fragment, container, false);
            mRecyclerView = (RecyclerView) mView.findViewById(R.id.recycler_view);
            mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
            if (mAdapter != null)
                mRecyclerView.setAdapter(mAdapter);

            if (savedInstanceState != null)
            {
                String path = savedInstanceState.getString("dir");
                File file = new File(path);
                setup(file, mContext, mExportFragment);
            }
            return mView;
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            outState.putString("dir", mAdapter.dirPath());
            super.onSaveInstanceState(outState);
        }

        public void setup(File directory, Context context, ExportFragment f)
        {
            mAdapter = new LocalDirectoryAdapter(directory, context, f);
            if (mRecyclerView != null)
                mRecyclerView.setAdapter(mAdapter);
        }

        public void reinit(Context context, ExportFragment f)
        {
            mContext = context;
            mExportFragment = f;
        }

        public boolean isWritable() {
            return mAdapter.isDirectoryWritable();
        }

        public String name() {
            return mAdapter.dirName();
        }

        public String path() {
            return mAdapter.dirPath();
        }

        public void refresh() {
            mAdapter.reloadFiles();
        }
    }

    static class LocalDirectoryAdapter extends RecyclerView.Adapter {

        File mRoot;
        ArrayList<File> mDirectories = new ArrayList<>();
        private Context mContext = null;
        ExportFragment mExportFragment;

        public LocalDirectoryAdapter(File directory, Context context, ExportFragment f)
        {
            mRoot = directory;
            mContext = context;
            reloadFiles();
            mExportFragment = f;
        }

        private void reloadFiles()
        {
            mDirectories.clear();
            String[] list = mRoot.list();
            Arrays.sort(list);
            for (String name : list)
            {
                File f = new File (mRoot.getAbsolutePath()+"/"+name);
                if (f.isDirectory() && f.canRead())
                    mDirectories.add(f);
            }
            notifyDataSetChanged();
        }


        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            View v = inflater.inflate(R.layout.files_item_file, parent, false);
            v.findViewById(R.id.lowerLayout).setVisibility(View.GONE);
            v.findViewById(R.id.more).setVisibility(View.GONE);
            v.findViewById(R.id.textSize).setVisibility(View.GONE);
            v.findViewById(R.id.imageFavourite).setVisibility(View.GONE);
            ((ImageView)v.findViewById(R.id.imageView)).setImageResource(R.drawable.directory);

            final DirectoryHolder holder = new DirectoryHolder(v);
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mExportFragment.openDirectory(holder.mFile);
                        }
                    }, 200);
                }
            });

            return holder;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            DirectoryHolder dh = (DirectoryHolder) holder;

            String name = mDirectories.get(position).getName();
            dh.mName.setText(name);
            dh.mFile = mDirectories.get(position);
        }

        @Override
        public int getItemCount() {
            return mDirectories.size();
        }

        public boolean isDirectoryWritable() {
            return mRoot.canWrite();
        }

        public String dirName() {
            String path = mRoot.getPath();
            switch (path)
            {
                case "/":
                    return mContext.getString(R.string.select_directory);
                default:
                    return mRoot.getName();
            }
        }

        public String dirPath() {
            return mRoot.getAbsolutePath();
        }

        class DirectoryHolder extends RecyclerView.ViewHolder
        {
            TextView mName;
            File mFile = null;
            public DirectoryHolder(View itemView) {
                super(itemView);
                mName = (TextView) itemView.findViewById(R.id.textName);
            }
        }
    }

}
