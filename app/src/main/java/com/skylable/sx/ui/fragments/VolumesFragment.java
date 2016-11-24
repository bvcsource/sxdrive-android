package com.skylable.sx.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.SupportAsyncTask;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.jni.SXClient;
import com.skylable.sx.jni.SXNativeException;
import com.skylable.sx.jni.SXUri;
import com.skylable.sx.jni.SXVolumeVec;
import com.skylable.sx.sxdrive2.FilesAdapter;
import com.skylable.sx.sxdrive2.SxAccount;
import com.skylable.sx.sxdrive2.SxMenuItemDecoration;
import com.skylable.sx.sxdrive2.SxVolume;
import com.skylable.sx.sxdrive2.VolumesAdapter;
import com.skylable.sx.ui.activities.ActivityAccount;

import java.io.File;
import java.util.ArrayList;

public class VolumesFragment extends Fragment {

    private View mView = null;
    private String mAccountName = null;
    private SxAccount mSxAccount = null;
    private FilesPagerSlider mSlider = null;

    private LinearLayout emptyLayout;
    private ImageView emptyImage;
    private TextView emptyText;
    private RecyclerView mRecyclerView;
    SwipeRefreshLayout mRefreshLayout;
    private VolumesAdapter mAdapter = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null)
        {
            mAccountName = savedInstanceState.getString("account");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.files_fragment, container, false);
        mRecyclerView = (RecyclerView) mView.findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mRecyclerView.addItemDecoration(new SxMenuItemDecoration(getActivity()));
        emptyLayout = (LinearLayout) mView.findViewById(R.id.empty);
        emptyImage = (ImageView) mView.findViewById(R.id.empty_image);
        emptyText = (TextView) mView.findViewById(R.id.empty_text);

        emptyLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TextUtils.equals(emptyText.getText(), getString(R.string.action_add_account))) {
                    Intent intent = new Intent(getActivity(), ActivityAccount.class);
                    startActivity(intent);
                }
            }
        });

        mRefreshLayout = (SwipeRefreshLayout) mView.findViewById(R.id.swipeRefreshLayout);
        mRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (mAdapter != null) {
                    mAdapter.requestRefresh();
                    mRefreshLayout.setRefreshing(false);
                }
                else if (mSxAccount!=null && mRecyclerView.getVisibility() == View.GONE) {
                    showRecyclerView(true);
                }
            }
        });
        mRefreshLayout.setVisibility(View.GONE);

        if (savedInstanceState == null) {
            if (mAccountName != null) {
                mSxAccount = SxAccount.loadAccount(mAccountName);
                if (mSxAccount == null)
                {
                    mSxAccount = SxAccount.loadFirstAccount();
                }
                if (mSxAccount != null) {
                    showRecyclerView(true);
                    mAccountName = mSxAccount.name();
                }
            }
        }
        else
        {
            mAccountName = savedInstanceState.getString("account");
            if (mAccountName != null)
            {
                mSxAccount = SxAccount.loadAccount(mAccountName);
                if (mSxAccount == null)
                {
                    mSxAccount = SxAccount.loadFirstAccount();
                }
                if (mSxAccount != null) {
                    showRecyclerView(true);
                    mAccountName = mSxAccount.name();
                }
            }

            Fragment f = mSlider.getFragmentManager().findFragmentByTag(InputDialogFragment.FRAGMENT_TAG);
            if (f instanceof FilterPasswordFragment)
            {
                ((FilterPasswordFragment)f).setVolumesFragment(this);
            }
        }
        return mView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mAccountName != null)
        {
            outState.putString("account", mAccountName);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onResume() {
        super.onResume();
        SxApp.sCurrentDirectoryId = -1;
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    public void setPagerSlider(FilesPagerSlider slider)
    {
        mSlider = slider;
    }

    public void setAccount(String account)
    {
        if (TextUtils.equals(mAccountName, account))
        {
            return;
        }
        mAccountName = account;
        mSxAccount = null;

        if (mView == null) {
            return;
        }

        if (account == null)
        {
            mRefreshLayout.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.GONE);
            emptyLayout.setVisibility(View.VISIBLE);
            emptyImage.setImageResource(R.drawable.no_account_graphic);
            emptyText.setText(R.string.action_add_account);
        }
        else {
            mSxAccount = SxAccount.loadAccount(mAccountName);
            showRecyclerView(true);
        }
    }

    SupportAsyncTask<Void, Void, Void> mUpdateTask = null;

    private void showRecyclerView(final boolean update)
    {
        if (mUpdateTask != null)
            return;

        if (mSxAccount != null && mSxAccount.isLocked()) {
            emptyLayout.setVisibility(View.VISIBLE);
            emptyText.setText(R.string.error_account_locked);
            emptyImage.setImageResource(R.drawable.no_volumes_graphic);
            return;
        }

        if (update) {
            mRecyclerView.setVisibility(View.GONE);
            emptyLayout.setVisibility(View.VISIBLE);
            emptyImage.setImageResource(R.drawable.no_volumes_graphic);
            emptyText.setText(R.string.volumelist_loading);

            mUpdateTask = new SupportAsyncTask<Void, Void, Void>() {
                String cluster;
                SXVolumeVec volumes = null;
                String sxweb=null;
                String sxshare=null;

                @Override
                protected void onPreExecute() {
                    cluster = mSxAccount.cluster();
                }

                @Override
                protected Void doInBackground(Void... params) {
                    SXClient client = SxApp.getCurrentClient();

                    if (client == null)
                        return null;

                    try {
                        SXUri uri = client.parseUri(cluster);
                        volumes = client.listVolumes(uri);
                        if (client.loadClusterMeta()) {
                            sxweb = client.getSxWeb();
                            sxshare = client.getSxShare();
                            if (sxweb.isEmpty())
                                sxweb = null;
                            if (sxshare.isEmpty())
                                sxshare = null;
                        }
                    }
                    catch (SXNativeException e) {
                        volumes = null;
                        e.printStackTrace();
                        return null;
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    FilesAdapter.sSxWeb = sxweb;
                    FilesAdapter.sSxShare = sxshare;
                    //super.onPostExecute(aVoid);
                    if (volumes != null) {
                        mSxAccount.updateVolumeList(volumes);
                    }
                    mUpdateTask = null;
                    showRecyclerView(false);
                }
            };
            mUpdateTask.execute();
            return;
        }

        mRefreshLayout.setVisibility(View.VISIBLE);

        ArrayList<SxVolume> volumes = mSxAccount.volumes();
        if (volumes.isEmpty()) {
            mRecyclerView.setVisibility(View.GONE);
            emptyLayout.setVisibility(View.VISIBLE);
            emptyImage.setImageResource(R.drawable.no_volumes_graphic);
            emptyText.setText(R.string.volumelist_empty);
        }
        else
        {
            emptyLayout.setVisibility(View.GONE);
            final VolumesFragment _this = this;

            mAdapter = new VolumesAdapter(getActivity(), mSxAccount, new VolumesAdapter.ClickListener() {
                @Override
                public void onClicked(long id, SxVolume volume) {
                    if (mSlider != null) {
                        if (volume.encrypted() == 1 || volume.encrypted() == 3)
                        {
                            removeAesConfig(volume);
                            FilterPasswordFragment fragment = new FilterPasswordFragment();
                            fragment.setup(volume, _this);
                            mSlider.getFragmentManager().beginTransaction().add(R.id.mainActivity, fragment, FilterPasswordFragment.FRAGMENT_TAG).commit();
                        }
                        else
                            mSlider.openDirectory(SxVolume.rootDirectoryId(id));
                    }
                }

                @Override
                public void accountLocked() {
                    lockAccount();
                }
            });

            mRecyclerView.setAdapter(mAdapter);
            mRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void removeAesConfig(SxVolume volume) {
        String list[] = { "35a5404d-1513-4009-904c-6ee5b0cd8634", "15b0ac3c-404f-481e-bc98-6598e4577bbd" };
        String[] tmp = SxApp.currentAccountName().split("@");
        String cluster = tmp[tmp.length-1];
        for (String uuid : list) {
            String path = SxApp.sConfigDir.getAbsolutePath() + "/" + cluster + "/volumes/" + volume.name() + "/" + uuid + "/";
            File key = new File(path+"key");
            key.delete();
            File custfp = new File(path+"custfp");
            custfp.delete();
        }
    }

    public void refreshAndOpen(SxVolume mVolume) {
        if (mAdapter == null)
            return;
        mAdapter.requestRefresh();
        if (mVolume != null)
            mSlider.openDirectory(SxVolume.rootDirectoryId(mVolume.volumeId()));
    }

    public void lockAccount() {
        if (!mSxAccount.isLocked()) {
            mSxAccount.lockAccount();
        }
        mRecyclerView.setVisibility(View.GONE);
        emptyLayout.setVisibility(View.VISIBLE);
        emptyText.setText(R.string.error_account_locked);
        emptyImage.setImageResource(R.drawable.no_volumes_graphic);
    }
}
