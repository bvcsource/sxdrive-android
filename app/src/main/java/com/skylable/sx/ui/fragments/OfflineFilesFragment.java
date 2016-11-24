package com.skylable.sx.ui.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.sxdrive2.OfflineFilesAdapter;
import com.skylable.sx.sxdrive2.SxDirectory;
import com.skylable.sx.sxdrive2.SxFileInfo;
import com.skylable.sx.sxdrive2.SxMenuItemDecoration;
import com.skylable.sx.util.FileOps;

/**
 * Created by tangarr on 23.10.15.
 */
public class OfflineFilesFragment extends Fragment implements OfflineFilesAdapter.ClickListener, OnFragmentRefreshListener {

    View mView = null;
    RecyclerView mRecyclerView;
    OfflineFilesAdapter mAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.offline_files_fragment, container, false);
        mRecyclerView = (RecyclerView) mView.findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mRecyclerView.addItemDecoration(new SxMenuItemDecoration(getActivity()));

        mAdapter = new OfflineFilesAdapter(SxApp.currentAccountName(), getActivity(), this);
        mRecyclerView.setAdapter(mAdapter);

        if (savedInstanceState != null)
        {
            Fragment f = getFragmentManager().findFragmentByTag(InputDialogFragment.FRAGMENT_TAG);
            if (f instanceof RemoveFragment)
            {
                ((RemoveFragment)f).reSetup(this);
            }
            else if (f instanceof RenameFragment)
            {
                ((RenameFragment)f).reSetup(this);
            }
        }
        return mView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (TextUtils.equals(SxApp.currentAccountName(), mAdapter.accountName()))
            onRefresh();
        else
            onAccountChanged();
    }

    public void onAccountChanged()
    {
        mAdapter = new OfflineFilesAdapter(SxApp.currentAccountName(), getActivity(), this);
        mRecyclerView.setAdapter(mAdapter);
    }


    @Override
    public void onClicked(long fileId) {
        try {
            SxFileInfo info = new SxFileInfo(fileId);
            if (info.needUpdate()) {
                MessageFragment.showMessage(R.string.file_not_downloaded_yet);
                return;
            }
            FileOps.openFile(fileId, getActivity());
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    @Override
    public void onOpenWith(long fileId) {
        try {
        SxFileInfo info = new SxFileInfo(fileId);
        if (info.needUpdate())
        {
            MessageFragment.showMessage(R.string.file_not_downloaded_yet);
            return;
        }
        FileOps.openFileWith(fileId, getActivity());
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }

    }

    @Override
    public void onShare(long fileId) {
        try {
            SxFileInfo info = new SxFileInfo(fileId);
            if (info.needUpdate()) {
                MessageFragment.showMessage(R.string.file_not_downloaded_yet);
                return;
            }
            FileOps.shareFile(fileId, getActivity());
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    @Override
    public void onExport(long fileId) {
        try {
            SxFileInfo info = new SxFileInfo(fileId);
            if (info.needUpdate()) {
                MessageFragment.showMessage(R.string.file_not_downloaded_yet);
                return;
            }
            ExportFragment f = new ExportFragment();
            f.setup(fileId, null);
            getFragmentManager().beginTransaction().add(R.id.mainActivity, f, f.getClass().getName()).commit();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void onFavourite(long fileId) {
        try {
            SxFileInfo fInfo = new SxFileInfo(fileId);
            boolean favourite = fInfo.isFavourite();
            fInfo.updateFavourite(!favourite);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    @Override
    public void onRename(long fileId) {
        RenameFragment f = new RenameFragment();
        f.setup(fileId, this);
        getFragmentManager().beginTransaction().add(R.id.mainActivity, f, InputDialogFragment.FRAGMENT_TAG).commit();
    }

    @Override
    public void onRemove(long fileId) {
        RemoveFragment f = new RemoveFragment();
        f.setup(fileId, this);
        getFragmentManager().beginTransaction().add(R.id.mainActivity, f, InputDialogFragment.FRAGMENT_TAG).commit();
    }

    @Override
    public void onRefresh() {
        if (mAdapter != null)
            mAdapter.refresh(true);
    }
}
