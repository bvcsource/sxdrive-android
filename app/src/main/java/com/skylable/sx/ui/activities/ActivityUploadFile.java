package com.skylable.sx.ui.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.ui.fragments.ChooseAccountFragment;
import com.skylable.sx.ui.fragments.FilesPagerSlider;
import com.skylable.sx.ui.fragments.MessageFragment;
import com.skylable.sx.ui.fragments.MkdirRemoteFragment;
import com.skylable.sx.util.FileOps;

import java.io.File;
import java.util.ArrayList;

public class ActivityUploadFile extends ActionBarActivity implements View.OnClickListener, FilesPagerSlider.OnPageChangeListener {

    private static ActivityUploadFile sInstance = null;
    private static ArrayList<String> sFragmentsToRemove = new ArrayList<>();
    private Toolbar mToolbar;
    private FilesPagerSlider mSlider;

    private TextView mButtonUpload;
    private TextView mButtonNewDir;
    private View mButtonCancel;

    synchronized private void setInstance()
    {
        sInstance = this;
    }
    synchronized private void clearInstance()
    {
        sInstance = null;
    }
    synchronized static public ActivityUploadFile getInstance()
    {
        return sInstance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MessageFragment.setupFragmentManager(getSupportFragmentManager(), 0);
        SxApp.validateCurrentAccount();

        String error = null;

        if (!SxApp.isStorageOK())
            error = SxApp.getStringResource(R.string.no_storage);
        if (!SxApp.isConnected())
            error = SxApp.getStringResource(R.string.no_data_connection);
        if (!isGoodIntent(getIntent()))
            error = SxApp.getStringResource(R.string.not_a_file);

        if (error != null)
        {
            setContentView(R.layout.message_fragment);
            View view = findViewById(R.id.mainActivity);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });

            TextView mTextMessage = (TextView) findViewById(R.id.textView);
            mTextMessage.setText(error);
        }
        else
        {
            setContentView(R.layout.activity_upload_file);
            mToolbar = (Toolbar) findViewById(R.id.app_bar);
            setSupportActionBar(mToolbar);

            mToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    switch (menuItem.getItemId()) {
                        case R.id.action_accounts: {
                            ChooseAccountFragment dialog = createChooseAccountFragment();
                            getSupportFragmentManager().beginTransaction().add(R.id.mainActivity, dialog, ChooseAccountFragment.class.getName()).commit();
                        }
                        return true;
                    }
                    return false;
                }
            });

            setupButtons();

            if (savedInstanceState == null)
            {
                mSlider = new FilesPagerSlider();
                mSlider.setToolbar(mToolbar);
                mSlider.hideFiles();
                getSupportFragmentManager().beginTransaction().add(R.id.working_area, mSlider, "FilesPagerSlider").commit();
                setButtonsEnabled(false);
            }
            else
            {
                mSlider = (FilesPagerSlider) getSupportFragmentManager().findFragmentByTag("FilesPagerSlider");
                int currentPage = savedInstanceState.getInt("currentPage");
                setButtonsEnabled(currentPage>0);
            }
            mSlider.setOnPageChangeListener(this);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("currentPage", mSlider.currentPage());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setInstance();
        MessageFragment.setupFragmentManager(getSupportFragmentManager(), R.id.mainActivity);

        if (mSlider == null)
            return;

        if (!TextUtils.equals(SxApp.currentAccountName(), mSlider.account()))
            mSlider.setAccount(SxApp.currentAccountName());

        for (String tag : sFragmentsToRemove) {
            Fragment f = getSupportFragmentManager().findFragmentByTag(tag);
            if (f != null)
                getSupportFragmentManager().beginTransaction().remove(f).commit();
        }
        sFragmentsToRemove.clear();
    }

    @Override
    protected void onPause() {
        super.onPause();
        clearInstance();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    private ChooseAccountFragment createChooseAccountFragment()
    {
        final ChooseAccountFragment dialog = new ChooseAccountFragment();
        dialog.setChooseAccountListener(new ChooseAccountFragment.ChooseAccountListener() {
            @Override
            public void onChooseAccount(String account) {
                if (!TextUtils.equals(SxApp.currentAccountName(), account)) {
                    SxApp.setsLastPath(account + ":/");
                    mSlider.setAccount(account);
                }
            }

            @Override
            public void onClose() {
                getSupportFragmentManager().beginTransaction().remove(dialog).commit();
            }
        });
        return dialog;
    }

    private void setupButtons() {
        mButtonUpload = (TextView) findViewById(R.id.button_ok);
        mButtonNewDir = (TextView) findViewById(R.id.button_newdir);
        mButtonCancel = findViewById(R.id.button_cancel);
        mButtonUpload.setOnClickListener(this);
        mButtonNewDir.setOnClickListener(this);
        mButtonCancel.setOnClickListener(this);
    }

    void setButtonsEnabled(boolean enabled)
    {
        int c;
        View.OnClickListener clickListener;
        if (enabled)
        {
            c= getResources().getColor(R.color.primaryColor);
            clickListener = this;
        }
        else
        {
            c = getResources().getColor(R.color.inactiveTextColor);
            clickListener = null;
        }

        mButtonUpload.setOnClickListener(clickListener);
        mButtonNewDir.setOnClickListener(clickListener);
        mButtonUpload.setTextColor(c);
        mButtonNewDir.setTextColor(c);
    }

    @Override
    public void onClick(final View v) {
        final ActivityUploadFile _this = this;
        v.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (v == mButtonUpload) {
                    Intent intent = getIntent();
                    String action = intent.getAction();
                    String filenames[];

                    if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                        ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                        final int count = uris.size();
                        filenames = new String[count];
                        for (int i = 0; i < count; i++)
                            filenames[i] = FileOps.prepareFileToUpload(uris.get(i), mSlider.currentDirectoryId(), _this);
                    } else {
                        final Uri uri;
                        if (Intent.ACTION_SEND.equals(action))
                            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                        else
                            uri = intent.getData();

                        filenames = new String[1];
                        filenames[0] = FileOps.prepareFileToUpload(uri, mSlider.currentDirectoryId(), _this);
                    }

                    for (String filename : filenames) {
                        File f = new File(filename);
                        if (f.exists()) {
                            SxApp.sSyncQueue.requestUpload(filename, SxApp.sCurrentDirectoryId, false);
                        }
                    }
                    finish();
                } else if (v == mButtonNewDir) {
                    MkdirRemoteFragment f = new MkdirRemoteFragment();
                    f.setup(mSlider.currentDirectoryId(), mSlider);
                    getSupportFragmentManager().beginTransaction().add(R.id.mainActivity, f, f.FRAGMENT_TAG).commit();
                } else if (v == mButtonCancel) {
                    finish();
                }
            }
        }, 200);
    }

    @Override
    public void pageChanged(int index) {
        setButtonsEnabled(index > 0);
    }

    public static boolean isGoodIntent(Intent intent)
    {
        final String action = intent.getAction();

        if (Intent.ACTION_SEND_MULTIPLE.equals(action) && !intent.hasExtra(Intent.EXTRA_STREAM))
            return false;

        if (Intent.ACTION_SEND.equals(action) && !(intent.hasExtra(Intent.EXTRA_STREAM) || (intent.getData() != null)))
            return false;

        return true;
    }

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
}
