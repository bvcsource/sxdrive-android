package com.skylable.sx.ui.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.sxdrive2.ExportAdapter;
import com.skylable.sx.sxdrive2.SxFileInfo;

import java.io.File;

/**
 * Created by tangarr on 08.10.15.
 */
public class ExportFragment extends Fragment implements View.OnClickListener, ViewPager.OnPageChangeListener {

    private View mView = null;

    private TextView mTextDirectory;
    private TextView mButtonExport;
    private TextView mButtonNewDir;
    private TextView mButtonCancel;
    private ViewPager mViewPager;
    private ExportAdapter mAdapter;
    private View mCard;
    private int mIndex;
    private boolean mPaused = true;
    private long mFileId = -1;
    private FilesPagerSlider mSlider = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.export_fragment, container, false);

        String tags[] = null;

        if (savedInstanceState != null)
        {
            tags = savedInstanceState.getStringArray("fragments");
            mFileId = savedInstanceState.getLong("fileId");
        }

        mTextDirectory = (TextView) mView.findViewById(R.id.textView);
        mButtonExport = (TextView) mView.findViewById(R.id.button_export);
        mButtonNewDir = (TextView) mView.findViewById(R.id.button_newdir);
        mButtonCancel = (TextView) mView.findViewById(R.id.button_cancel);
        mViewPager = (ViewPager) mView.findViewById(R.id.viewPager);
        mAdapter = new ExportAdapter(getChildFragmentManager(), getActivity(), this, tags);
        mViewPager.setAdapter(mAdapter);
        mCard = mView.findViewById(R.id.card);

        mView.setOnClickListener(this);
        mButtonExport.setOnClickListener(this);
        mButtonNewDir.setOnClickListener(this);
        mButtonCancel.setOnClickListener(this);
        mCard.setOnClickListener(this);

        mIndex = mAdapter.getCount()-1;
        if (mIndex == 0)
            setExportButtonEnabled(false);
        mViewPager.setCurrentItem(mIndex, false);
        mViewPager.setOnPageChangeListener(this);

        mTextDirectory.setText(mAdapter.path(mAdapter.getCount()-1));

        return mView;
    }

    public void setup(long fileId, FilesPagerSlider slider)
    {
        mFileId = fileId;
        mSlider = slider;
    }

    public void reSetup(FilesPagerSlider slider)
    {
        mSlider = slider;
    }

    public long fileId()
    {
        return mFileId;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {

        outState.putStringArray("fragments", mAdapter.getFragmentsTags());
        outState.putLong("fileId", mFileId);
        super.onSaveInstanceState(outState);
    }

    public void onBackPressed()
    {
        int index = mViewPager.getCurrentItem();
        if (index > 0)
        {
            mViewPager.setCurrentItem(index - 1, true);
        }
        else
        {
            getFragmentManager().beginTransaction().remove(this).commit();
        }
    }

    void setExportButtonEnabled(boolean enabled)
    {
        int c;
        View.OnClickListener listener;

        if (enabled)
        {
            c= getActivity().getResources().getColor(R.color.primaryColor);
            listener = this;
        }
        else
        {
            c = getActivity().getResources().getColor(R.color.inactiveTextColor);
            listener = null;
        }
        mButtonExport.setTextColor(c);
        mButtonNewDir.setTextColor(c);
        mButtonExport.setOnClickListener(listener);
        mButtonNewDir.setOnClickListener(listener);
    }

    public void openDirectory(File directory)
    {
        mAdapter.removeAfter(mIndex);
        mIndex = mAdapter.openDirectory(directory);
        mViewPager.setCurrentItem(mIndex, true);
    }

    public void refreshAndOpenDirectory(File directory)
    {
        mAdapter.removeAfter(mIndex);

        ExportAdapter.LocalDirectoryFragment f = (ExportAdapter.LocalDirectoryFragment) mAdapter.getItem(mIndex);
        f.refresh();
        mIndex = mAdapter.openDirectory(directory);
        mViewPager.setCurrentItem(mIndex, true);
    }

    @Override
    public void onResume() {
        super.onResume();
        mPaused = false;
    }

    @Override
    public void onPause() {
        super.onPause();
        mAdapter.removeAfter(mIndex);
        mPaused = true;
    }

    @Override
    public void onClick(final View v) {
        final ExportFragment _this = this;
        if (v == mView)
            close();
        else {
            v.postDelayed(new Runnable() {
                @Override
                public void run() {
                    switch (v.getId())
                    {
                        case R.id.button_export:
                            export();
                            break;
                        case R.id.button_newdir: {
                            MkdirLocalFragment f = new MkdirLocalFragment();
                            f.setup(mAdapter.path(mViewPager.getCurrentItem()), _this);
                            getFragmentManager().beginTransaction().add(R.id.mainActivity, f, f.FRAGMENT_TAG ).commit();
                        } break;
                        case R.id.button_cancel:
                            close();
                    }
                }
            },200);
        }
    }

    private void export()
    {
        close();
        try {
            SxFileInfo finfo = new SxFileInfo(mFileId);
            String export_path = mAdapter.path(mViewPager.getCurrentItem())+"/"+finfo.filename();

            SxApp.sSyncQueue.requestExport(mFileId, export_path);
            if (mSlider != null)
                mSlider.openFileDownloadPage(mFileId, DownloadPageFragment.Type.EXPORT);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    private void close() {
        getFragmentManager().beginTransaction().remove(this).commit();
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {
        ExportAdapter.LocalDirectoryFragment f = (ExportAdapter.LocalDirectoryFragment) mAdapter.getItem(position);
        setExportButtonEnabled(f.isWritable());
        mTextDirectory.setText(f.path());
        mIndex = position;
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        if (state == 0)
        {
            mAdapter.removeAfter(mIndex);
        }
    }
}
