package com.skylable.sx.ui.fragments;

import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.skylable.sx.R;
import com.skylable.sx.sxdrive2.FilesPagerAdapter;
import com.skylable.sx.sxdrive2.SxFileInfo;
import com.skylable.sx.ui.fragments.DownloadPageFragment;
import com.skylable.sx.ui.fragments.FilesFragment;
import com.skylable.sx.ui.fragments.FloatingActionFragment;
import com.skylable.sx.ui.fragments.MessageFragment;
import com.skylable.sx.ui.fragments.VolumesFragment;

/**
 * Created by tangarr on 23.09.15.
 */
public class FilesPagerSlider extends Fragment {

    private ViewPager mPager = null;
    private FilesPagerAdapter mPagerAdapter = null;
    private Toolbar mToolbar = null;
    private FloatingActionFragment mFloatingActionFragment = null;
    private FloatingActionFragment.ActionListener mActionListener = null;
    private String mAccount = null;
    private int mHelperIndex = -1;
    boolean mHideFiles = false;
    private OnPageChangeListener onPageChangeListener = null;

    public void setOnPageChangeListener(OnPageChangeListener pageChangeListener)
    {
        onPageChangeListener = pageChangeListener;
    }


    public void setActionListner(FloatingActionFragment.ActionListener actionListner)
    {
        mActionListener = actionListner;
        if (mFloatingActionFragment != null)
        {
            mFloatingActionFragment.setActionListener(mActionListener);
        }
    }

    public long currentDirectoryId()
    {
        int index = mPager.getCurrentItem();
        Fragment f = mPagerAdapter.getItem(index);
        if (f instanceof FilesFragment)
            return  ((FilesFragment)f).directoryId();
        return -1;
    }


    public void setToolbar(Toolbar toolbar)
    {
        mToolbar = toolbar;
    }

    public String account()
    {
        return mAccount;
    }

    public void openFileDownloadPage(long id, DownloadPageFragment.Type type)
    {
        int index = mPagerAdapter.openFileDownloadPage(id, type);
        mPager.setCurrentItem(index, true);
    }
    public void openDirectory(long id)
    {
        mPagerAdapter.removeAfter(mPager.getCurrentItem());
        int index = mPagerAdapter.openDirectory(id);
        mPager.setCurrentItem(index, true);
    }
    public void openDirRefreshParent(long id)
    {
        int index = mPager.getCurrentItem();
        Fragment f = mPagerAdapter.getItem(index);

        openDirectory(id);

        if (f instanceof FilesFragment)
        {
            ((FilesFragment)f).softRefresh();
        }

    }

    public void previousPage()
    {
        Log.e("SLIDER", "previousPage " + (mPagerAdapter.getCount() - 2));
        int index = mPagerAdapter.getCount() - 2;
        if (index >= 0) {
            mPager.setCurrentItem(index, true);
        }
    }

    public int currentPage()
    {
        return mPager.getCurrentItem();
    }

    public void setAccount(String account)
    {
        mAccount = account;
        if (mPagerAdapter != null)
            mPagerAdapter.setAccount(account);
        if (mToolbar != null)
        {
            mToolbar.setTitle(R.string.app_name);
        }
    }

    public void hideFiles()
    {
        mHideFiles = true;
        if (mPagerAdapter != null)
            mPagerAdapter.hideFiles();
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, final Bundle savedInstanceState) {
        Log.e("SLIDER", "onCreate");
        String[] pagesTags = null;
        if (savedInstanceState != null)
        {
            mAccount = savedInstanceState.getString("account");
            pagesTags = savedInstanceState.getStringArray("pages");
            mFloatingActionFragment = (FloatingActionFragment) getFragmentManager().findFragmentByTag(FloatingActionFragment.class.getName());
            mHideFiles = savedInstanceState.getBoolean("mHideFiles");
        }

        if (mFloatingActionFragment == null)
        {
            mFloatingActionFragment = new FloatingActionFragment();
            mFloatingActionFragment.setActionListener(mActionListener);
        }
        else
        {
            mFloatingActionFragment.setActionListener(mActionListener);
        }

        View v = inflater.inflate(R.layout.files_slide, container, false);
        mPager = (ViewPager) v.findViewById(R.id.pager);
        mPagerAdapter = new FilesPagerAdapter(getChildFragmentManager(), mAccount, pagesTags, this, getActivity(), mHideFiles);
        mPager.setAdapter(mPagerAdapter);
        mPager.setPageTransformer(false, new FilesPageTransformer());
        mPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                if (position > mPagerAdapter.getCount()-1)
                {
                    Log.e("CRASH", "something went wrong");
                    mPager.setCurrentItem(mPagerAdapter.getCount()-1, false);
                    return;
                }

                mHelperIndex = position;
                Fragment f = mPagerAdapter.getItem(position);
                if (f instanceof FilesFragment)
                {
                    FilesFragment filesFragment = (FilesFragment) f;

                    if (mToolbar != null)
                        mToolbar.setTitle(filesFragment.title());

                    Fragment actionFragment = getFragmentManager().findFragmentByTag(FloatingActionFragment.class.getName());
                    if (actionFragment == null)
                    {
                        getFragmentManager().beginTransaction().add(R.id.working_area, mFloatingActionFragment, FloatingActionFragment.class.getName()).commit();
                    }
                }
                else
                {
                    if (mToolbar != null)
                        mToolbar.setTitle(R.string.app_name);
                    Fragment actionFragment = getFragmentManager().findFragmentByTag(FloatingActionFragment.class.getName());
                    if (actionFragment != null)
                    {
                        getFragmentManager().beginTransaction().remove(actionFragment).commit();
                    }
                }
                if (onPageChangeListener != null)
                    onPageChangeListener.pageChanged(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == 2)
                {
                    mHelperIndex = -1;
                }
                else if (state == 0)
                {
                    if (mHelperIndex != -1)
                    {
                        mPagerAdapter.removeAfter(mHelperIndex);
                        mHelperIndex = -1;
                    }
                }
            }
        });

        if (savedInstanceState != null) {
            int index = 0;
            if (savedInstanceState.containsKey("path"))
            {
                long[] array = savedInstanceState.getLongArray("path");
                for (long id: array) {
                    index = mPagerAdapter.openDirectory(id);
                }
                mPager.setCurrentItem(index, false);
            }

            Fragment f = getFragmentManager().findFragmentByTag(InputDialogFragment.FRAGMENT_TAG);
            if (f != null) {

                FilesFragment filesFragment = null;
                Fragment last_fragment= mPagerAdapter.getItem(mPagerAdapter.getCount()-1);
                if (last_fragment instanceof FilesFragment)
                    filesFragment = (FilesFragment) last_fragment;

                if (f instanceof RemoveFragment) {
                    ((RemoveFragment) f).reSetup(filesFragment);
                } else if (f instanceof RenameFragment) {
                    ((RenameFragment) f).reSetup(filesFragment);
                } else if (f instanceof ExportFragment) {
                    ((ExportFragment) f).reSetup(this);
                } else if (f instanceof MkdirRemoteFragment) {
                    ((MkdirRemoteFragment)f).setup(filesFragment.directoryId(), this);
                }
            }
        }

        return v;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mHelperIndex != -1)
        {
            mPagerAdapter.removeAfter(mHelperIndex);
            mHelperIndex = -1;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("account", mAccount);
        outState.putStringArray("pages", mPagerAdapter.getFragmentsTags());
        outState.putBoolean("mHideFiles", mHideFiles);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    public void requestRefreshDirectory(long id) {
        mPagerAdapter.requestRefreshDirectory(id);
    }

    public void lockVolume(long id) {
        if (!(mPagerAdapter.getItem(0) instanceof VolumesFragment))
            return;

        if (mPagerAdapter.getCount() > 1)
        {
            if (!(mPagerAdapter.getItem(1) instanceof FilesFragment))
                return;
            long dirId = ((FilesFragment) mPagerAdapter.getItem(1)).directoryId();
            try {
                SxFileInfo info = new SxFileInfo(dirId);
                if (info.volumeId() != id)
                    return;
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                return;
            }

            int index = mPager.getCurrentItem();
            Fragment last = mPagerAdapter.getItem(index);

            mPager.setCurrentItem(0, true);
            MessageFragment.showMessage(R.string.volume_locked);

        }

        mPager.post(new Runnable() {
            @Override
            public void run() {
                VolumesFragment f = (VolumesFragment) mPagerAdapter.getItem(0);
                f.refreshAndOpen(null);

            }
        });
    }

    public void refreshFile(long id) {
        try {
            SxFileInfo info = new SxFileInfo(id);
            long parentId = info.parentId();
            for (int i=1; i<mPagerAdapter.getCount(); i++)
            {
                if (mPagerAdapter.getItem(i) instanceof FilesFragment)
                {
                    FilesFragment filesFragment = (FilesFragment) mPagerAdapter.getItem(i);
                    if (filesFragment.directoryId() == parentId)
                        filesFragment.refreshFile(id);
                }
            }

        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    public void lockAccount() {
        Fragment f = mPagerAdapter.getItem(0);
        if (f instanceof VolumesFragment) {
            ((VolumesFragment)f).lockAccount();
        }
        if (mPagerAdapter.getCount() > 1) {
            mPager.setCurrentItem(0, true);
        }
    }

    class FilesPageTransformer implements ViewPager.PageTransformer {
        @Override
        public void transformPage(View view, float position) {
            if (Build.VERSION.SDK_INT >= 17) {
                int pageWidth = view.getWidth();
                if (position < -1) {
                    view.setAlpha(0);
                } else if (position <= 0) {
                    view.setAlpha(1 + position);
                    view.setTranslationX(pageWidth * -position);
                } else if (position <= 1) {
                    view.setAlpha(1);
                } else {
                    view.setAlpha(0);
                }
            }
        }
    }

    public interface OnPageChangeListener
    {
        void pageChanged(int index);
    }
}
