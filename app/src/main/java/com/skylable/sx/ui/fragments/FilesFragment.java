package com.skylable.sx.ui.fragments;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.SupportAsyncTask;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.sxdrive2.FilesAdapter;
import com.skylable.sx.sxdrive2.SxFileInfo;
import com.skylable.sx.sxdrive2.SxMenuItemDecoration;
import com.skylable.sx.ui.activities.ActivityMain;
import com.skylable.sx.util.FileOps;

/**
 * Created by tangarr on 23.09.15.
 */

public class FilesFragment extends Fragment implements OnFragmentRefreshListener {

    View mView = null;
    private RecyclerView mRecyclerView = null;
    private LinearLayout emptyLayout;
    private ImageView emptyImage;
    private TextView emptyText;
    FilesPagerSlider mSlider = null;
    SwipeRefreshLayout mRefreshLayout;
    boolean mHideFiles = false;
    private SupportAsyncTask<Void, Void, Void> mTask = null;

    private long mDirectoryId = -1;
    String mTitle = null;

    private FilesAdapter.BackgroundRefresh mRefreshCallback;

    public long directoryId()
    {
        return mDirectoryId;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null)
        {
            mDirectoryId = savedInstanceState.getLong("mDirectoryId");
            mHideFiles = savedInstanceState.getBoolean("mHideFiles");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (savedInstanceState!=null)
        {
            mDirectoryId = savedInstanceState.getLong("mDirectoryId", -1);
            mHideFiles = savedInstanceState.getBoolean("mHideFiles");
        }

        mView = inflater.inflate(R.layout.files_fragment, container, false);
        mRecyclerView = (RecyclerView) mView.findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mRecyclerView.addItemDecoration(new SxMenuItemDecoration(getActivity()));
        emptyLayout = (LinearLayout) mView.findViewById(R.id.empty);
        emptyImage = (ImageView) mView.findViewById(R.id.empty_image);
        emptyText = (TextView) mView.findViewById(R.id.empty_text);

        mRefreshCallback = new FilesAdapter.BackgroundRefresh() {
            @Override
            public void onFinish(boolean changed) {
                if (mRefreshLayout.isRefreshing())
                    mRefreshLayout.setRefreshing(false);
            }
        };

        mRefreshLayout = (SwipeRefreshLayout) mView.findViewById(R.id.swipeRefreshLayout);
        mRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (mDirectoryId == -1) {
                    mRefreshLayout.setRefreshing(false);
                    return;
                }
                FilesAdapter adapter = (FilesAdapter) mRecyclerView.getAdapter();
                adapter.refreshInBackground(mRefreshCallback);
            }
        });

        if (mDirectoryId == -1)
        {
            emptyImage.setImageResource(R.drawable.no_files_graphic);
            emptyText.setText("");
        }
        else
        {
            setDirectoryId(mDirectoryId);
        }

        return mView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putLong("mDirectoryId", mDirectoryId);
        outState.putBoolean("mHideFiles", mHideFiles);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onResume() {
        super.onResume();
        SxApp.sCurrentDirectoryId = mDirectoryId;

    }

    public void setup(long directoryId, FilesPagerSlider slider)
    {
        mSlider = slider;
        setDirectoryId(directoryId);
    }

    public void setSlider(FilesPagerSlider slider)
    {
        mSlider = slider;
    }

    void setDirectoryId(long id)
    {
        mDirectoryId = id;

        final SxFileInfo tmp_finfo;
        try {
            Log.e("OPEN_DIR", "id="+id);
            tmp_finfo = new SxFileInfo(id);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            return;
        }
        if (TextUtils.equals(tmp_finfo.path(), "/"))
            mTitle = tmp_finfo.volume();
        else
            mTitle = tmp_finfo.filename();

        if (mRecyclerView != null)
        {
            final FilesFragment _this = this;
            emptyLayout.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);

            FilesAdapter.ClickListener clickListener = new FilesAdapter.ClickListener() {
                @Override
                public void onClicked(FilesAdapter.ItemType type, long id) {
                    switch (type)
                    {
                        case DIRECTORY:
                        {
                            if (mSlider != null)
                                mSlider.openDirectory(id);
                        } break;
                        case FILE:
                        {
                            int index = SxApp.sSyncQueue.indexOf(id);
                            boolean onList = (index >= 0);

                            try {
                                SxFileInfo file = new SxFileInfo(id);
                                if (onList) {
                                    mSlider.openFileDownloadPage(id, DownloadPageFragment.Type.OPEN);
                                } else if (file.needUpdate()) {
                                    SxApp.sSyncQueue.requestDownload(id, false);
                                    mSlider.openFileDownloadPage(id, DownloadPageFragment.Type.OPEN);
                                } else {
                                    FileOps.openFile(id, getActivity());
                                }
                            }
                            catch (Exception ex)
                            {
                                ex.printStackTrace();
                            }
                        }
                    }
                }

                @Override
                public void onOpenWith(long id) {
                    int index = SxApp.sSyncQueue.indexOf(id);
                    boolean onList = (index >= 0);

                    try {

                        SxFileInfo file = new SxFileInfo(id);
                        if (onList) {
                            mSlider.openFileDownloadPage(id, DownloadPageFragment.Type.OPEN_WITH);
                        } else if (file.needUpdate()) {
                            SxApp.sSyncQueue.requestDownload(id, false);
                            mSlider.openFileDownloadPage(id, DownloadPageFragment.Type.OPEN_WITH);
                        } else {
                            FileOps.openFileWith(id, getActivity());
                        }
                    }
                    catch (Exception ex)
                    {
                        ex.printStackTrace();
                    }
                }

                @Override
                public void onShare(long id) {
                    try {
                        int index = SxApp.sSyncQueue.indexOf(id);
                        boolean onList = (index >= 0);

                        SxFileInfo file = new SxFileInfo(id);
                        if (onList) {
                            mSlider.openFileDownloadPage(id, DownloadPageFragment.Type.SHARE);
                        } else if (file.needUpdate()) {
                            SxApp.sSyncQueue.requestDownload(id, false);
                            mSlider.openFileDownloadPage(id, DownloadPageFragment.Type.SHARE);
                        } else {
                            FileOps.shareFile(id, getActivity());
                        }
                    }
                    catch (Exception ex)
                    {
                        ex.printStackTrace();
                    }
                }

                @Override
                public void onExport(long fileId) {
                    ExportFragment f = new ExportFragment();
                    f.setup(fileId, mSlider);
                    mSlider.getFragmentManager().beginTransaction().add(R.id.mainActivity, f, f.getClass().getName()).commit();

                }

                @Override
                public void onFavourite(final long fileId) {
                    try {
                        final SxFileInfo fInfo = new SxFileInfo(fileId);
                        final boolean favourite = fInfo.isFavourite();

                        if (!fInfo.isDir()) {
                            Log.e("FAV", "FILE");
                            fInfo.updateFavourite(!favourite);
                            if (!favourite) {
                                if (fInfo.needUpdate() && SxApp.sSyncQueue.indexOf(fileId) == -1)
                                    SxApp.sSyncQueue.requestDownload(fileId, true);
                            }
                            else {
                                SxApp.sSyncQueue.cancelBackgroundTask(fileId);
                            }
                            ActivityMain.onFavouriteFilesChanged(fInfo.account(), fileId);
                        }
                        else
                        {
                            if (mTask != null)
                                return;

                            mTask = new SupportAsyncTask<Void, Void, Void>() {
                                ProcessingDialogFragment f;

                                @Override
                                protected void onPreExecute() {
                                    f = new ProcessingDialogFragment();
                                    mSlider.getFragmentManager().beginTransaction().add(R.id.mainActivity, f, InputDialogFragment.FRAGMENT_TAG).commit();
                                }

                                @Override
                                protected Void doInBackground(Void... params) {
                                    fInfo.updateFavourite(!favourite);
                                    return null;
                                }

                                @Override
                                protected void onPostExecute(Void aVoid) {
                                    mTask = null;
                                    ActivityMain.closeFragment(InputDialogFragment.FRAGMENT_TAG);
                                    if (!favourite) {
                                        String accountName = fInfo.account();
                                        Account account = SxApp.getAccount(accountName);
                                        if (account == null)
                                            return;
                                        Bundle bundle = new Bundle();
                                        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                                        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                                        ContentResolver.requestSync(account, SxApp.getStringResource(R.string.authority), bundle);
                                    }
                                    else
                                    {
                                        SxApp.sSyncQueue.cancelBackgroundTasks(fInfo.path());
                                    }
                                    ActivityMain.onFavouriteFilesChanged(fInfo.account(), fileId);
                                }
                            };
                            mTask.execute();
                        }
                    }
                    catch (Exception ex)
                    {
                        ex.printStackTrace();
                    }
                }

                @Override
                public void onRename(long fileId) {
                    RenameFragment f = new RenameFragment();
                    f.setup(fileId, _this);
                    mSlider.getFragmentManager().beginTransaction().add(R.id.mainActivity, f, InputDialogFragment.FRAGMENT_TAG).commit();
                }

                @Override
                public void onRemove(long fileId) {
                    RemoveFragment f = new RemoveFragment();
                    f.setup(fileId, _this);
                    mSlider.getFragmentManager().beginTransaction().add(R.id.mainActivity, f, InputDialogFragment.FRAGMENT_TAG).commit();
                }

                @Override
                public void onPublicLink(long mUnfoldedFileId) {
                    PublicLinkFragment f=new PublicLinkFragment();
                    f.setup(mUnfoldedFileId);
                    mSlider.getFragmentManager().beginTransaction().add(R.id.mainActivity, f, PublicLinkFragment.class.getName()).commit();
                }

                @Override
                public void accountLocked() {
                    mSlider.lockAccount();
                }
            };

            final FilesAdapter adapter = new FilesAdapter(getActivity(), mDirectoryId, clickListener, mRefreshCallback, mHideFiles);

            mRecyclerView.setAdapter(adapter);

            mRefreshLayout.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (adapter.isRefreshing()) {
                        mRefreshLayout.setRefreshing(true);
                    }
                }
            }, 100);

        }
    }

    public String title() {
        return mTitle;
    }

    public void softRefresh() {
        FilesAdapter adapter = (FilesAdapter) mRecyclerView.getAdapter();
        adapter.refreshInBackground(mRefreshCallback);
    }

    public void refreshFile(long id) {
        ((FilesAdapter)mRecyclerView.getAdapter()).refreshFile(id);
    }

    public void hideFiles() {
        mHideFiles = true;
    }

    @Override
    public void onRefresh() {
        softRefresh();
    }
}