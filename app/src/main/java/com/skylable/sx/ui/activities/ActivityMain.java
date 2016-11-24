package com.skylable.sx.ui.activities;

import android.accounts.Account;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.content.SupportAsyncTask;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.sxdrive2.SxAccount;
import com.skylable.sx.ui.fragments.ChooseAccountFragment;
import com.skylable.sx.ui.fragments.ExportFragment;
import com.skylable.sx.ui.fragments.FilesPagerSlider;
import com.skylable.sx.ui.fragments.FloatingActionFragment;
import com.skylable.sx.ui.fragments.InputDialogFragment;
import com.skylable.sx.ui.fragments.MessageFragment;
import com.skylable.sx.ui.fragments.MkdirRemoteFragment;
import com.skylable.sx.sxdrive2.NavigationDrawer;
import com.skylable.sx.sxdrive2.NavigatorAdapter;
import com.skylable.sx.ui.fragments.OfflineFilesFragment;
import com.skylable.sx.ui.fragments.PendingFragment;
import com.skylable.sx.ui.fragments.ProcessingDialogFragment;
import com.skylable.sx.ui.fragments.PublicLinkFragment;
import com.skylable.sx.util.FileOps;

import java.io.File;
import java.util.ArrayList;

import static com.skylable.sx.util.FileOps.filenameFromUri;

public class ActivityMain extends ActionBarActivity {

    private static final int SELECT_FILE = 1;
    private static final int BACKPRESS_TIME = 3000;
    private static final String TAG_WORKING_FRAGMENT = "SxWorkingAreaFragment";

    private static ActivityMain sInstance = null;
    private Thread mThread = null;

    private long mLastBackPressTime = 0;
    private Toolbar mToolbar;
    Fragment mWorkingAreaFragment = null;
    private Toast mToast = null;
    private FloatingActionFragment.ActionListener mActionListener;
    private ActivityHandler mHandler;

    synchronized private void setInstance()
    {
        sInstance = this;
    }
    synchronized private void clearInstance()
    {
        sInstance = null;
    }
    synchronized static public ActivityMain getInstance()
    {
        return sInstance;
    }

    private static ArrayList<String> sFragmentsToRemove = new ArrayList<>();
    private static ArrayList<Pair<String,Long>> sFilesToUpdate = new ArrayList<>();

    public static void closeFragment(String tag)
    {
        if (sInstance == null)
        {
            if (ActivityAccount.sInstance == null)
                sFragmentsToRemove.add(tag);
            else {
                Fragment f = ActivityAccount.sInstance.getSupportFragmentManager().findFragmentByTag(tag);
                if (f != null)
                    ActivityAccount.sInstance.getSupportFragmentManager().beginTransaction().remove(f).commit();
            }
        }
        else
        {
            Fragment f = sInstance.getSupportFragmentManager().findFragmentByTag(tag);
            if (f != null)
                sInstance.getSupportFragmentManager().beginTransaction().remove(f).commit();
        }
    }

    public static void showToast(String msg, boolean timeLong)
    {
        ActivityMain instance = getInstance();
        if (instance == null)
            return;
        instance._showToast(msg, timeLong);
    }

    public static void updateDirectory(long id)
    {
        ActivityMain instance = getInstance();
        if (instance == null)
            return;
        instance._updateDirectory(id);
    }

    private void _updateDirectory(long id)
    {
        if (mThread == Thread.currentThread())
        {
            if (mWorkingAreaFragment instanceof FilesPagerSlider)
            {
                ((FilesPagerSlider)mWorkingAreaFragment).requestRefreshDirectory(id);
            }
        }
        else
        {
            Message message = Message.obtain();
            Bundle data = new Bundle();
            data.putLong("updateDir", id);
            message.setData(data);
            mHandler.sendMessage(message);
        }
    }

    private void _showToast(String msg, boolean timeLong)
    {
        if (mThread == Thread.currentThread())
            Toast.makeText(this, msg, timeLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
        else
        {
            Message message = Message.obtain();
            Bundle data = new Bundle();
            data.putString("showToast", msg);
            data.putBoolean("showToastTime", timeLong);
            message.setData(data);
            mHandler.sendMessage(message);
        }
    }

    public static void lockVolume(long id) {
        ActivityMain instance = getInstance();
        if (instance == null)
            return;
        instance._lockVolume(id);
    }

    private void _lockVolume(long id)
    {
        if (mThread == Thread.currentThread())
        {
            if (mWorkingAreaFragment instanceof FilesPagerSlider)
            {
                ((FilesPagerSlider)mWorkingAreaFragment).lockVolume(id);
            }
        }
        else
        {
            Message message = Message.obtain();
            Bundle data = new Bundle();
            data.putLong("lockVolume", id);
            message.setData(data);
            mHandler.sendMessage(message);
        }
    }

    private synchronized void _updateFilesFromList()
    {
        for (Pair<String, Long> p : sFilesToUpdate)
        {
            _onFavouriteFilesRefreshed(p.first, p.second);
        }
        sFilesToUpdate.clear();
    }

    synchronized public static void onFavouriteFilesChanged(String account, Long id)
    {
        ActivityMain instance = getInstance();
        if (instance == null) {
            sFilesToUpdate.add(new Pair<String, Long>(account, id));
            return;
        }
        instance._onFavouriteFilesRefreshed(account, id);
    }

    private void _onFavouriteFilesRefreshed(String account, Long id) {
        if (mThread == Thread.currentThread()) {
            if (mWorkingAreaFragment instanceof OfflineFilesFragment)
            {
                OfflineFilesFragment f = (OfflineFilesFragment) mWorkingAreaFragment;
                f.onRefresh();
            }
            if (id != null && mWorkingAreaFragment instanceof FilesPagerSlider)
            {
                FilesPagerSlider slider = (FilesPagerSlider) mWorkingAreaFragment;
                slider.refreshFile(id);
            }
        }
        else
        {
            Message message = Message.obtain();
            Bundle data = new Bundle();
            data.putString("updateFavourites", account);
            if (id != null)
                data.putLong("id", id);
            message.setData(data);
            mHandler.sendMessage(message);
        }
    }

    public static void lockAccount(String account) {
        ActivityMain instance = getInstance();
        if (instance == null)
            return;
        instance._lockAccount(account);
    }

    private void _lockAccount(String account) {
        if (mThread == Thread.currentThread())
        {
            SxAccount a = SxAccount.loadAccount(account);
            if (a!= null)
                a.lockAccount();
            if (TextUtils.equals(SxApp.currentAccountName(),account) && mWorkingAreaFragment instanceof FilesPagerSlider)
            {
                ((FilesPagerSlider)mWorkingAreaFragment).lockAccount();
            }
        }
        else
        {
            Message message = Message.obtain();
            Bundle data = new Bundle();
            data.putString("lockAccount", account);
            message.setData(data);
            mHandler.sendMessage(message);
        }
    }

    static class ActivityHandler extends Handler
    {
        ActivityMain mActivity;

        public ActivityHandler(ActivityMain activity)
        {
            mActivity = activity;
        }
        @Override
        public void handleMessage(Message message) {
            Bundle data = message.getData();
            if (data == null)
                return;
            if (data.containsKey("showToast"))
            {
                String msg = data.getString("showToast");
                boolean timeLong = data.getBoolean("showToastTime");
                mActivity._showToast(msg, timeLong);
            }
            else if (data.containsKey("updateDir"))
            {
                long id = data.getLong("updateDir");
                mActivity._updateDirectory(id);
            }
            else if (data.containsKey("lockVolume"))
            {
                long id = data.getLong("lockVolume");
                mActivity._lockVolume(id);
            }
            else if (data.containsKey("updateFavourites"))
            {
                String account = data.getString("updateFavourites");
                Long id = null;
                if (data.containsKey("id"))
                    data.getLong("id");
                mActivity._onFavouriteFilesRefreshed(account, id);
            }
            else if (data.containsKey("lockAccount")) {
                String account = data.getString("lockAccount");
                mActivity._lockAccount(account);
            }
        }
    }

    private ChooseAccountFragment createChooseAccountFragment()
    {
        final ChooseAccountFragment dialog = new ChooseAccountFragment();
        dialog.setChooseAccountListener(new ChooseAccountFragment.ChooseAccountListener() {
            @Override
            public void onChooseAccount(String account) {
                if (!TextUtils.equals(SxApp.currentAccountName(), account)) {
                    SxApp.setsLastPath(account + ":/");
                    if (mWorkingAreaFragment instanceof FilesPagerSlider) {
                        ((FilesPagerSlider) mWorkingAreaFragment).setAccount(account);
                    } else if (mWorkingAreaFragment instanceof OfflineFilesFragment) {
                        ((OfflineFilesFragment) mWorkingAreaFragment).onAccountChanged();
                    }
                }
            }

            @Override
            public void onClose() {
                getSupportFragmentManager().beginTransaction().remove(dialog).commit();
            }
        });
        return dialog;
    }

    static final String[] fragmentsTags = new String[] { "SX_MESSAGEBOX", ChooseAccountFragment.class.getName()};

    @Override
    public void onBackPressed() {

        InputDialogFragment inputDialogFragment = (InputDialogFragment) getSupportFragmentManager().findFragmentByTag(InputDialogFragment.FRAGMENT_TAG);
        if (inputDialogFragment != null && inputDialogFragment.onBackPressed())
            return;

        ExportFragment exportFragment = (ExportFragment) getSupportFragmentManager().findFragmentByTag(ExportFragment.class.getName());
        if (exportFragment != null)
        {
            exportFragment.onBackPressed();
            return;
        }

        for (String tag : fragmentsTags) {
            Fragment f = getSupportFragmentManager().findFragmentByTag(tag);
            if (f != null) {
                getSupportFragmentManager().beginTransaction().remove(f).commit();
                return;
            }
        }

        PublicLinkFragment publicLinkFragment = (PublicLinkFragment) getSupportFragmentManager().findFragmentByTag(PublicLinkFragment.class.getName());
        if (publicLinkFragment != null) {
            publicLinkFragment.onBackPressed();
            return;
        }

        FloatingActionFragment actionFragment = (FloatingActionFragment) getSupportFragmentManager().findFragmentByTag(FloatingActionFragment.class.getName());
        if (actionFragment != null && actionFragment.onBackPressed())
            return;

        if (mWorkingAreaFragment instanceof FilesPagerSlider)
        {
            FilesPagerSlider slider = (FilesPagerSlider) mWorkingAreaFragment;
            if (slider.currentPage() > 0)
            {
                slider.previousPage();
                return;
            }
        }

        if (mLastBackPressTime < System.currentTimeMillis() - BACKPRESS_TIME)
        {
            mToast = Toast.makeText(this, R.string.press_again_back, Toast.LENGTH_SHORT);
            mToast.show();
            mLastBackPressTime = System.currentTimeMillis();
        }
        else
        {
            mToast.cancel();
            super.onBackPressed();
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        if (savedInstanceState != null)
        mToolbar.setTitle(savedInstanceState.getString("title"));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mThread = Thread.currentThread();
        mHandler = new ActivityHandler(this);

        mToolbar = (Toolbar) findViewById(R.id.app_bar);
        setSupportActionBar(mToolbar);

        if (savedInstanceState != null ){
            Fragment f = getSupportFragmentManager().findFragmentByTag(ChooseAccountFragment.class.getName());
            if (f != null)
            {
                ChooseAccountFragment dialog = createChooseAccountFragment();
                getSupportFragmentManager().beginTransaction().replace(R.id.drawerLayout, dialog, ChooseAccountFragment.class.getName()).commit();
            }
        }

        mWorkingAreaFragment = getSupportFragmentManager().findFragmentByTag(TAG_WORKING_FRAGMENT);


        mToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.action_accounts: {
                        ChooseAccountFragment dialog = createChooseAccountFragment();
                        getSupportFragmentManager().beginTransaction().add(R.id.drawerLayout, dialog, ChooseAccountFragment.class.getName()).commit();
                    }
                    return true;
                }
                return false;
            }
        });

        mActionListener = new FloatingActionFragment.ActionListener() {
            @Override
            public void onAddFile() {
                if (SxApp.currentAccountName() != null) {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    startActivityForResult(Intent.createChooser(intent, null), SELECT_FILE);
                }
            }

            @Override
            public void onAddDir() {
                MkdirRemoteFragment f = new MkdirRemoteFragment();

                if (mWorkingAreaFragment instanceof FilesPagerSlider) {
                    FilesPagerSlider slider = (FilesPagerSlider) mWorkingAreaFragment;
                    f.setup(slider.currentDirectoryId(), slider);
                }

                getSupportFragmentManager().beginTransaction().add(R.id.mainActivity, f, f.FRAGMENT_TAG).commit();
            }
        };

        if (mWorkingAreaFragment instanceof FilesPagerSlider)
        {
            FilesPagerSlider slider = (FilesPagerSlider) mWorkingAreaFragment;
            slider.setActionListner(mActionListener);
        }

        final NavigationDrawer drawer = (NavigationDrawer) getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);

        NavigatorAdapter.NavigatorItemClickListener navigatorItemClickListener = new NavigatorAdapter.NavigatorItemClickListener() {
            @Override
            public void onClick(final NavigatorAdapter.NavigatorOption option) {

                if (option == NavigatorAdapter.NavigatorOption.OPTION_SETTINGS)
                {
                    Intent intent = new Intent(getApplicationContext(), ActivitySettings.class);
                    startActivity(intent);
                }
                else if (option == NavigatorAdapter.NavigatorOption.OPTION_ABOUT)
                {
                    Intent intent = new Intent(getApplicationContext(), AboutActivity.class);
                    startActivity(intent);
                }
                else if (option != drawer.sSelectedItem) {
                    drawer.selectOption(option);
                    changePage(option);
                }
                drawer.closeDrawer();
            }
        };

        drawer.setup((DrawerLayout) findViewById(R.id.drawerLayout), mToolbar, navigatorItemClickListener);

        SxApp.validateCurrentAccount();

        if (mWorkingAreaFragment == null)
            changePage(NavigationDrawer.sSelectedItem);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("ActivityMain", "onResume " + SxApp.currentAccountName());
        SxApp.validateCurrentAccount();

        setInstance();
        MessageFragment.setupFragmentManager(getSupportFragmentManager(), R.id.drawerLayout);
        if (mWorkingAreaFragment instanceof FilesPagerSlider)
        {
            FilesPagerSlider slider = (FilesPagerSlider)mWorkingAreaFragment;
            slider.setToolbar(mToolbar);

            if (!TextUtils.equals(SxApp.currentAccountName(), slider.account()))
                slider.setAccount(SxApp.currentAccountName());

            MkdirRemoteFragment fragment = (MkdirRemoteFragment)getSupportFragmentManager().findFragmentByTag(MkdirRemoteFragment.class.getName());
            if (fragment != null)
                fragment.setup(slider.currentDirectoryId(), slider);
        }

        if (mUploadPreProcessingTask != null)
            mUploadPreProcessingTask.execute();
        for (String tag : sFragmentsToRemove) {
            Fragment f = getSupportFragmentManager().findFragmentByTag(tag);
            if (f != null)
                getSupportFragmentManager().beginTransaction().remove(f).commit();
        }
        sFragmentsToRemove.clear();
        _updateFilesFromList();
    }

    @Override
    protected void onPause() {
        super.onPause();
        clearInstance();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("title", mToolbar.getTitle().toString());
        super.onSaveInstanceState(outState);
    }

    void changePage(NavigatorAdapter.NavigatorOption option)
    {
        Log.d("ActivityMain", "changePage");
        if (mWorkingAreaFragment != null)
        {
            FloatingActionFragment actionFragment = (FloatingActionFragment) getSupportFragmentManager().findFragmentByTag(FloatingActionFragment.class.getName());
            if (actionFragment != null)
                getSupportFragmentManager().beginTransaction().remove(actionFragment).commit();

            getSupportFragmentManager().beginTransaction().remove(mWorkingAreaFragment).commit();
            mWorkingAreaFragment = null;
        }
        switch (option)
        {
            case OPTION_FILES:{
                FilesPagerSlider slider = new FilesPagerSlider();
                slider.setAccount(SxApp.currentAccountName());
                slider.setToolbar(mToolbar);
                slider.setActionListner(mActionListener);
                mToolbar.setTitle(R.string.app_name);
                mWorkingAreaFragment = slider;
            }break;
            case OPTION_UPLOAD: {
                mToolbar.setTitle(R.string.navigator_transfers);
                mWorkingAreaFragment = new PendingFragment();
            }break;
            case OPTION_FAVOURITES: {
                mToolbar.setTitle(R.string.navigator_starred);
                mWorkingAreaFragment = new OfflineFilesFragment();
            }break;
        }
        if (mWorkingAreaFragment != null) {
            getSupportFragmentManager().beginTransaction().add(R.id.working_area, mWorkingAreaFragment, TAG_WORKING_FRAGMENT).commit();
        }
    }

    SupportAsyncTask<Void, Void, String> mUploadPreProcessingTask = null;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        if (requestCode == SELECT_FILE && resultCode == Activity.RESULT_OK)
        {
            if (SxApp.sCurrentDirectoryId == -1)
                return;

            final String action = data.getAction();

            if (Intent.ACTION_SEND_MULTIPLE.equals(action) && !data.hasExtra(Intent.EXTRA_STREAM))
                return;

            if (Intent.ACTION_SEND.equals(action) && !(data.hasExtra(Intent.EXTRA_STREAM) || (data.getData() != null)))
                return;

            mUploadPreProcessingTask = new SupportAsyncTask<Void, Void, String>() {

                @Override
                protected void onPreExecute() {
                    mUploadPreProcessingTask = null;
                    ProcessingDialogFragment fragment = new ProcessingDialogFragment();
                    getSupportFragmentManager().beginTransaction().add(R.id.mainActivity, fragment, InputDialogFragment.FRAGMENT_TAG).commit();
                }

                @Override
                protected String doInBackground(Void... params) {
                    Uri uri = data.getData();
                    return FileOps.prepareFileToUpload(uri, SxApp.sCurrentDirectoryId, ActivityMain.this);
                }

                @Override
                protected void onPostExecute(String s) {
                    if (s == null) {
                        final ContentResolver cr = getContentResolver();
                        String filename = filenameFromUri(data.getData(), cr);
                        Toast.makeText(ActivityMain.this,
                                getString(R.string.file_uplad_failed, filename),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ActivityMain.closeFragment(InputDialogFragment.FRAGMENT_TAG);
                    File f = new File(s);
                    if (f.exists()) {
                        SxApp.sSyncQueue.requestUpload(s, SxApp.sCurrentDirectoryId, false);
                        Toast.makeText(ActivityMain.this,
                                getString(R.string.upload_enqueued, f.getName()),
                                Toast.LENGTH_SHORT).show();
                    }
                    else
                        Toast.makeText(ActivityMain.this,
                                getString(R.string.file_uplad_failed, f.getName()),
                                Toast.LENGTH_SHORT).show();

                }
            };
        }
        else if (requestCode == ActivityAccount.REQUEST_CODE) {
            if (resultCode == 1) {
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement

        return super.onOptionsItemSelected(item);
    }
}
