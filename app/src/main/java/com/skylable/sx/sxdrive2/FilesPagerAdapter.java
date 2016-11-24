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
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.util.Log;
import android.view.ViewGroup;

import com.skylable.sx.R;
import com.skylable.sx.ui.fragments.DownloadPageFragment;
import com.skylable.sx.ui.fragments.FilesFragment;
import com.skylable.sx.ui.fragments.FilesPagerSlider;
import com.skylable.sx.ui.fragments.VolumesFragment;
import com.skylable.sx.util.FileOps;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by tangarr on 23.09.15.
 */

public class FilesPagerAdapter extends FragmentPagerAdapter {
    String mAccount = null;
    FilesPagerSlider mSlider;
    List<Fragment> mFragments;
    FragmentManager mFragmentManager;
    Context mContext;
    boolean mHideFiles;

    public String[] getFragmentsTags()
    {
        String[] list = new String[mFragments.size()];
        for (int i=0; i<mFragments.size(); i++)
        {
            list[i] = mFragments.get(i).getTag();
        }
        return list;
    }

    public int openFileDownloadPage(long id, DownloadPageFragment.Type type)
    {
        DownloadPageFragment fragment = new DownloadPageFragment();
        switch (type) {
            case OPEN: {
                fragment.setup(id, new DownloadPageFragment.OnPageCloseListener() {
                    @Override
                    public void onClose() {
                        mSlider.previousPage();
                    }

                    @Override
                    public void onDownloadFinished(long id) {
                        mSlider.previousPage();
                        Fragment f = mFragments.get(mSlider.currentPage());
                        if (f instanceof FilesFragment) {
                            ((FilesFragment) f).refreshFile(id);
                        }
                        FileOps.openFile(id, mContext);
                    }
                }, null);
            } break;
            case OPEN_WITH:
            {
                fragment.setup(id, new DownloadPageFragment.OnPageCloseListener() {
                    @Override
                    public void onClose() {
                        mSlider.previousPage();
                    }

                    @Override
                    public void onDownloadFinished(long id) {
                        mSlider.previousPage();
                        Fragment f = mFragments.get(mSlider.currentPage());
                        if (f instanceof FilesFragment) {
                            ((FilesFragment) f).refreshFile(id);
                        }
                        FileOps.openFileWith(id, mContext);
                    }
                }, mContext.getString(R.string.action_open_with));
            } break;
            case SHARE:
            {
                fragment.setup(id, new DownloadPageFragment.OnPageCloseListener() {
                    @Override
                    public void onClose() {
                        mSlider.previousPage();
                    }

                    @Override
                    public void onDownloadFinished(long id) {
                        mSlider.previousPage();
                        Fragment f = mFragments.get(mSlider.currentPage());
                        if (f instanceof FilesFragment) {
                            ((FilesFragment) f).refreshFile(id);
                        }
                        FileOps.shareFile(id, mContext);
                    }
                }, mContext.getString(R.string.action_share));
            } break;
            case EXPORT:
            {
                fragment.setup(id, new DownloadPageFragment.OnPageCloseListener() {
                    @Override
                    public void onClose() {
                        mSlider.previousPage();
                    }

                    @Override
                    public void onDownloadFinished(long id) {
                        mSlider.previousPage();
                    }
                }, mContext.getString(R.string.action_export));
            }
        }
        mFragments.add(fragment);
        notifyDataSetChanged();
        return mFragments.size()-1;
    }

    public int openDirectory(long id)
    {
        FilesFragment fragment = new FilesFragment();
        fragment.setup(id, mSlider);
        if (mHideFiles)
            fragment.hideFiles();
        mFragments.add(fragment);
        notifyDataSetChanged();
        return mFragments.size()-1;
    }

    public void removeAfter(int removeAfterPosition)
    {
        if (removeAfterPosition+1 < mFragments.size()) {
            for (int i = mFragments.size() - 1; i > removeAfterPosition; i--) {
                Fragment f = mFragments.get(i);

                if (mFragmentManager.getFragments().contains(f))
                    mFragmentManager.beginTransaction().remove(f).commit();
                mFragments.remove(i);
            }
            notifyDataSetChanged();
        }
    }

    public void setAccount(String account)
    {
        mAccount = account;
        if (!mFragments.isEmpty()) {
            removeAfter(0);
            VolumesFragment volumeList = (VolumesFragment) mFragments.get(0);
            volumeList.setAccount(account);
        }
        notifyDataSetChanged();
    }

    public FilesPagerAdapter(FragmentManager fm, String account, String[] fragmentsTags, FilesPagerSlider slider, Context context, boolean hideFiles) {
        super(fm);
        mAccount = account;
        mSlider = slider;
        mContext = context;
        mFragments = new ArrayList<>();
        mFragmentManager = fm;
        mHideFiles = hideFiles;

        if (fragmentsTags != null)
        {
            for (String tag: fragmentsTags) {
                Fragment f = fm.findFragmentByTag(tag);
                mFragments.add(f);
                if (f instanceof VolumesFragment)
                {
                    ((VolumesFragment)f).setPagerSlider(mSlider);
                }
                else if (f instanceof FilesFragment)
                {
                    ((FilesFragment)f).setSlider(mSlider);
                    if (hideFiles)
                        ((FilesFragment)f).hideFiles();
                }
            }
        }
    }

    @Override
    public void setPrimaryItem(ViewGroup container, int position, Object object) {
        super.setPrimaryItem(container, position, object);
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
    public Fragment getItem(int position) {
        if (position == 0 && mFragments.isEmpty()) {
            VolumesFragment fragment = new VolumesFragment();
            fragment.setAccount(mAccount);
            fragment.setPagerSlider(mSlider);
            mFragments.add(fragment);
            return fragment;
        }
        return mFragments.get(position);
    }

    @Override
    public int getCount() {
        if (mFragments.isEmpty())
            return 1;
        else
            return mFragments.size();
    }

    public void requestRefreshDirectory(long id) {
        for (Fragment f : mFragments)
        {
            if (f instanceof FilesFragment)
            {
                FilesFragment ff = (FilesFragment)f;
                if (ff.directoryId() == id)
                {
                    ff.softRefresh();
                    return;
                }
            }
        }
    }

    public void addFragment(DownloadPageFragment clone) {
        mFragments.add(clone);
        notifyDataSetChanged();
    }

    public void hideFiles() {
        mHideFiles = true;
        for (Fragment f : mFragments)
        {
            if (f instanceof FilesFragment)
            {
                ((FilesFragment)f).hideFiles();
            }
        }
    }
}

