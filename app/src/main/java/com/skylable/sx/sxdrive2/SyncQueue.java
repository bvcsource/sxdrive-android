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

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.auth.SxClientEx;
import com.skylable.sx.jni.SXError;
import com.skylable.sx.jni.SXNativeException;
import com.skylable.sx.jni.SXUri;
import com.skylable.sx.ui.activities.ActivityMain;
import com.skylable.sx.ui.fragments.DownloadPageFragment;
import com.skylable.sx.ui.fragments.PendingFragment;
import com.skylable.sx.util.Client;
import com.skylable.sx.util.FileOps;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class SyncQueue {

    private Object mSyncToken = new Object();
    private Task mCurrentTask = null;
    private ArrayList<Task> mPendingTasks = new ArrayList<>();
    private WeakReference<Fragment> weakReference = new WeakReference<>(null);

    private SyncQueueThread mQueueThread = null;

    public void setObserver(Fragment fragment) {
        synchronized (mSyncToken) {
            weakReference = new WeakReference<>(fragment);
        }
    }

    private void checkQueue()
    {
        if (mQueueThread == null) {
            mQueueThread = new SyncQueueThread();
            mQueueThread.start();
        } else {

            Fragment weak = weakReference.get();
            if (weak instanceof PendingFragment) {
                PendingFragment f = (PendingFragment) weak;
                f.requestUpdate();
            }
        }
    }

    public void requestExport(long fileId, String path)
    {
        Log.e("EXPORT", "request");
        synchronized (mSyncToken)
        {
            Task t = new Task(mSyncToken, path, fileId, TaskType.Export, false);
            mPendingTasks.add(t);
            checkQueue();
        }
    }

    public void requestDownload(long fileId, boolean backgroundTask) {
        synchronized (mSyncToken) {
            Task t = new Task(mSyncToken, null, fileId, TaskType.Download, backgroundTask);
            mPendingTasks.add(t);
            checkQueue();
        }
    }

    public void requestUpload(String local_file, long remoteDirId, boolean backgroundTask) {
        synchronized (mSyncToken) {
            Task t = new Task(mSyncToken, local_file, remoteDirId, TaskType.Upload, backgroundTask);
            mPendingTasks.add(t);
            checkQueue();
        }
    }

    public Task getCurrentTask(Handler handler) {
        //TODO connect task with handler
        synchronized (mSyncToken) {
            return mCurrentTask;
        }
    }

    Task[] getPendingList() {
        Task[] list;
        synchronized (mSyncToken) {
            list = new Task[mPendingTasks.size()];
            for (int i = 0; i < mPendingTasks.size(); i++) {
                list[i] = mPendingTasks.get(i);
            }
        }
        return list;
    }

    public int indexOf(long id)
    {
        synchronized (mSyncToken)
        {
            if (mCurrentTask != null && mCurrentTask.fileId() == id)
                return 0;
            for (Task t : mPendingTasks)
            {
                if (t.fileId() == id)
                    return mPendingTasks.indexOf(t)+1;
            }
            return -1;
        }
    }

    public void cancelTask(long mFileId) {
        synchronized (mSyncToken)
        {
            if (mCurrentTask != null && mCurrentTask.fileId() == mFileId)
            {
                mCurrentTask.requestCancel();
            }
            else
            {
                for (Task t : mPendingTasks)
                {
                    if (t.fileId() == mFileId)
                    {
                        mPendingTasks.remove(t);
                        return;
                    }
                }
            }
        }
    }

    public void cancelBackgroundTask(long fileId) {
        synchronized (mSyncToken)
        {
            if (mCurrentTask != null && mCurrentTask.fileId() == fileId)
            {
                if (mCurrentTask.background())
                    mCurrentTask.requestCancel();
            }
            else
            {
                for (Task t : mPendingTasks)
                {
                    if (t.fileId() == fileId)
                    {
                        if (t.background())
                            mPendingTasks.remove(t);
                        return;
                    }
                }
            }
        }
    }

    public void cancelBackgroundTasks(String parent_dir) {
        synchronized (mSyncToken)
        {
            if (mCurrentTask != null && mCurrentTask.filePath().startsWith(parent_dir))
            {
                if (mCurrentTask.background())
                    mCurrentTask.requestCancel();
            }
            ArrayList<Task> removeList = new ArrayList<>();
            for (Task t : mPendingTasks)
            {
                if (t.filePath().startsWith(parent_dir) && t.background())
                {
                    removeList.add(t);
                }
            }
            mPendingTasks.removeAll(removeList);
        }
    }

    public class Task
    {
        private Object mSyncToken;
        private String mAccount;
        private String mFile;
        private String mExportPath = null;
        private long mId;
        private TaskType mTaskType;
        private long mProgress;
        private long mSize;
        private String mRev;
        private boolean mCanceling = false;
        private boolean mBackground;

        public Task (Object syncToken, String localFile, long id, TaskType taskType, boolean background)
        {
            mSyncToken = syncToken;
            mSize = mProgress = 0;
            mBackground = background;

            SxFileInfo fi;
            try {
                fi = new SxFileInfo(id);
            }
            catch (Exception ex)
            {
                mTaskType=TaskType.Invalid;
                return;
            }
            mAccount = fi.account();
            mTaskType = taskType;
            mId = id;
            if (mTaskType == TaskType.Upload)
            {
                mFile = localFile;
            }
            else
            {
                if (mTaskType == TaskType.Export)
                {
                    mExportPath = localFile;
                }
                mFile = fi.path();
            }
        }
        public boolean background() {
            return mBackground;
        }
        public String account()
        {
            return mAccount;
        }
        public String filePath()
        {
            return mFile;
        }
        public String filename()
        {
            int index = mFile.lastIndexOf("/");
            if (index < 0)
                return mFile;
            else
                return mFile.substring(index);
        }
        public TaskType taskType()
        {
            return mTaskType;
        }
        public long fileId() { return mId; }
        public void setProgress(long size, long progress)
        {
            synchronized (mSyncToken)
            {
                mSize = size;
                mProgress = progress;
            }
        }
        public long size()
        {
            synchronized (mSyncToken)
            {
                return mSize;
            }
        }
        public long progress()
        {
            synchronized (mSyncToken)
            {
                return mProgress;
            }
        }
        public String rev()
        {
            return mRev;
        }
        public String exportPath()
        {
            return mExportPath;
        }

        public void requestCancel() {
            synchronized (mSyncToken)
            {
                mCanceling = true;
            }
        }

        public boolean canceling()
        {
            synchronized (mSyncToken)
            {
                return mCanceling;
            }
        }
    }

    public enum TaskType
    {
        Upload,
        Download,
        Export,
        Invalid
    }

    class SyncQueueThread extends Thread implements SxClientEx.OnProgressListener
    {
        SxClientEx mClient = null;
        SyncQueue mQueue;
        Task mTask = null;
        boolean mFilterError;
        String mErrorMessage;
        int mLastErrorCode;
        NotificationCompat.Builder mBuilder;
        NotificationManager mNotificationManager =  (NotificationManager) SxApp.sInstance.getSystemService(Context.NOTIFICATION_SERVICE);
        final int mNotificationId = 0;

        SyncQueueThread()
        {
            setPriority(MIN_PRIORITY);
            mFilterError = false;
            mErrorMessage = null;
        }

        boolean upload()
        {
            mFilterError = false;
            mErrorMessage = null;
            try {
                mClient = Client.getClientEx(mTask.account(), this);
            } catch (SXNativeException e) {
                e.printStackTrace();
                if (e.getMessage().contains("Invalid credentials")) {
                    mErrorMessage = SxApp.getStringResource(R.string.invalid_credentials);
                    mLastErrorCode = SXError.SXE_EAUTH;
                    return false;
                }
                mErrorMessage = SxApp.getStringResource(R.string.no_data_connection);
                return false;
            }

            SxAccount account = SxAccount.loadAccount(mTask.account());
            SxFileInfo fi;

            try {
                fi = new SxFileInfo(mTask.fileId());
                int index = mTask.filePath().lastIndexOf("/");
                String filename = mTask.filePath().substring(index+1);

                String remote_path = (fi.volume() + "/" + fi.path()+"/"+filename).replace("//", "/");

                SXUri remote = mClient.parseUri(account.cluster() + "/" + remote_path);
                boolean result = mClient.localToRemote(mTask.filePath(), remote);
                if (!result)
                {
                    throw new Exception(mClient.getErrMsg());
                }
            }
            catch (Exception ex)
            {
                if (mClient.getErrNum() == SXError.SXE_EFILTER)
                    mFilterError = true;
                mErrorMessage = ex.getMessage();
                mLastErrorCode = mClient.getErrNum();
                mClient = null;
                ex.printStackTrace();
                return false;
            }
            mClient = null;
            return true;
        }

        File download()
        {
            mFilterError = false;
            mErrorMessage = null;
            File result = null;
            try {
                mClient = Client.getClientEx(mTask.account(), this);
            } catch (SXNativeException e) {
                e.printStackTrace();
                if (e.getMessage().contains("Invalid credentials")) {
                    mErrorMessage = SxApp.getStringResource(R.string.invalid_credentials);
                    mLastErrorCode = SXError.SXE_EAUTH;
                    return null;
                }
                mErrorMessage = SxApp.getStringResource(R.string.no_data_connection);
                return null;
            }
            SxAccount account = SxAccount.loadAccount(mTask.account());
            SxFileInfo fi;

            try {
                fi = new SxFileInfo(mTask.fileId());
                SXUri remote = mClient.parseUri(account.cluster() + "/" + fi.volume() + "/" + fi.path());
                File localTmp = new File(SxApp.sCacheDir.getPath()+"/"+fi.parentId()+"/"+fi.filename());

                if (!localTmp.getParentFile().exists())
                {
                    localTmp.getParentFile().mkdirs();
                }
                localTmp.setReadable(true, false);

                String rev = mClient.remoteToLocal(remote, localTmp.getAbsolutePath());
                if (rev == null)
                {
                    localTmp.delete();
                    throw new Exception(mClient.getErrMsg());
                }
                else
                {
                    localTmp.setReadable(true, true);
                    File local = new File(SxApp.sFilesDir.getPath()+"/"+fi.parentId()+"/"+fi.filename());
                    if (!local.getParentFile().exists())
                    {
                        local.getParentFile().mkdirs();
                    }
                    if (localTmp.renameTo(local)) {
                        fi.updateAfterDownload(rev, local.getAbsolutePath(), local.length(), local.lastModified());
                        result = local;
                    }
                    else {
                        fi.updateAfterDownload(rev, localTmp.getAbsolutePath(), localTmp.length(), localTmp.lastModified());
                        result = localTmp;
                    }
                }
            }
            catch (Exception ex)
            {
                if (mClient.getErrNum() == SXError.SXE_EFILTER)
                    mFilterError = true;
                mErrorMessage = ex.getMessage();
                mLastErrorCode = mClient.getErrNum();
                mClient = null;
                ex.printStackTrace();
                return null;
            }
            mClient = null;
            return result;
        }



        @Override
        public void run() {

            mBuilder = new NotificationCompat.Builder(SxApp.sInstance);

            while (true)
            {
                mTask = null;
                synchronized (mSyncToken)
                {
                    if (mPendingTasks.isEmpty())
                    {
                        mNotificationManager.cancel(mNotificationId);
                        mQueueThread = null;
                        break;
                    }
                    mCurrentTask = mPendingTasks.get(0);
                    mTask = mCurrentTask;
                    mPendingTasks.remove(0);

                    Fragment weak = weakReference.get();
                    if (weak instanceof PendingFragment) {
                        PendingFragment f = (PendingFragment) weak;
                        f.requestUpdate();
                    }
                    else if (weak instanceof DownloadPageFragment)
                    {
                        DownloadPageFragment f = (DownloadPageFragment) weak;
                        f.onCurrentTaskChanged(mTask.fileId());
                    }
                }

                mLastErrorCode = SXError.SXE_NOERROR;
                SxFileInfo info;
                try {
                    info = new SxFileInfo(mTask.fileId());
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                    mCurrentTask = null;
                    Fragment weak = weakReference.get();
                    if (weak instanceof PendingFragment) {
                        PendingFragment f = (PendingFragment) weak;
                        f.requestUpdate();
                    }
                    continue;
                }

                if (mTask.taskType() == TaskType.Upload)
                {
                    int index = mTask.filePath().lastIndexOf("/");
                    String filename = mTask.filePath().substring(index+1);
                    mBuilder.setContentTitle(SxApp.getStringResource(R.string.app_name) + " - " +SxApp.getStringResource(R.string.uploading));
                    mBuilder.setContentText(filename);
                    mBuilder.setSmallIcon(R.drawable.task_upload_current);
                    mBuilder.setProgress(0, 0, true);
                    mNotificationManager.notify(mNotificationId, mBuilder.build());
                    if (upload())
                    {
                        ActivityMain.showToast(SxApp.getStringResource(R.string.file_upladed, filename), false);
                        ActivityMain.updateDirectory(mTask.fileId());
                    }
                    else if ( mLastErrorCode != SXError.SXE_ABORT)
                    {
                        if (mErrorMessage == null)
                            ActivityMain.showToast(SxApp.getStringResource(R.string.file_uplad_failed, info.filename()), true);
                        else
                            ActivityMain.showToast(mErrorMessage, true);
                    }
                }
                else {
                    mBuilder.setContentTitle(SxApp.getStringResource(R.string.app_name) + " - " +SxApp.getStringResource(R.string.downloading));
                    mBuilder.setContentText(info.filename());
                    mBuilder.setSmallIcon(R.drawable.task_download_current);
                    mBuilder.setProgress(0,0,true);

                    mNotificationManager.notify(mNotificationId, mBuilder.build());

                    File input = null;
                    if (mTask.taskType() == TaskType.Export)
                    {
                        if (!info.needUpdate()) {
                            input = new File(info.localPath());
                            mTask.setProgress(100,100);
                            onProgressChange(100, 100);
                        }
                    }
                    if (input == null)
                        input = download();

                    if (input == null)
                    {
                        boolean showToast = true;
                        synchronized (mSyncToken)
                        {
                            Fragment weak = weakReference.get();
                            if (weak instanceof DownloadPageFragment)
                            {
                                DownloadPageFragment f = (DownloadPageFragment) weak;
                                if (!f.isPaused()) {
                                    f.downloadFailed(mTask.fileId(), mErrorMessage);
                                    showToast = false;
                                }
                            }
                            if (showToast && mLastErrorCode != SXError.SXE_ABORT)
                            {
                                String msg;
                                if (mTask.taskType() == TaskType.Download)
                                    msg = SxApp.getStringResource(R.string.file_download_failed, info.filename());
                                else
                                    msg = SxApp.getStringResource(R.string.file_export_failed, info.filename());

                                if (mErrorMessage == null)
                                    ActivityMain.showToast(msg, true);
                                else
                                    ActivityMain.showToast(msg+"\n"+mErrorMessage, true);

                            }
                        }
                    }

                    if (mTask.taskType() == TaskType.Export && input != null)
                    {
                        File output = new File(mTask.exportPath());
                        FileOps.exportFile(input, output, new FileOps.ProgressListener() {
                            @Override
                            public void onProgress(long size, long progress) {
                                if (size > 0)
                                {
                                    double done = (double)progress/(double)size;
                                    synchronized (mSyncToken) {
                                        Fragment weak = weakReference.get();
                                        if (weak instanceof PendingFragment) {
                                            PendingFragment f = (PendingFragment) weak;
                                            f.setCurrentTaskExportProgress(done, mTask.fileId());
                                        }
                                        else if (weak instanceof DownloadPageFragment)
                                        {
                                            DownloadPageFragment f = (DownloadPageFragment) weak;
                                            f.setExportProgress(done, mTask.fileId());
                                        }
                                    }
                                }
                            }
                        });
                    }

                    if (input != null) {
                        boolean showToast = true;
                        synchronized (mSyncToken) {
                            Fragment weak = weakReference.get();
                            if (weak instanceof DownloadPageFragment) {
                                DownloadPageFragment f = (DownloadPageFragment) weak;
                                if (!f.isPaused()) {
                                    f.openFile(mTask.fileId());
                                    showToast = false;
                                }
                            }
                        }
                        if (info.isFavourite())
                        {
                            ActivityMain.onFavouriteFilesChanged(info.account(), mTask.fileId());
                        }
                        if (showToast)
                        {
                            String msg;
                            if (mTask.taskType() == TaskType.Download)
                                msg = SxApp.getStringResource(R.string.file_downloaded, info.filename());
                            else
                                msg = SxApp.getStringResource(R.string.file_exported, info.filename());
                            ActivityMain.showToast(msg, false);
                        }
                    }

                }

                synchronized (mSyncToken)
                {
                    mCurrentTask = null;

                    if (mFilterError)
                    {
                        SxVolume vol = SxVolume.loadVolume(info.volumeId());
                        if (vol.encrypted() > 0)
                        {
                            if (vol.encrypted() == 2) {
                                SQLiteDatabase db = SxDatabaseHelper.database();
                                if (Build.VERSION.SDK_INT >= 11)
                                    db.beginTransactionNonExclusive();
                                else
                                    db.beginTransaction();
                                vol.updateDatabase(1, vol.aesFingerprint(), db);
                                db.setTransactionSuccessful();
                                db.endTransaction();

                                ActivityMain.lockVolume(info.volumeId());
                            }
                            else if (vol.encrypted() == 4) {
                                SQLiteDatabase db = SxDatabaseHelper.database();
                                if (Build.VERSION.SDK_INT >= 11)
                                    db.beginTransactionNonExclusive();
                                else
                                    db.beginTransaction();
                                vol.updateDatabase(3, vol.aesFingerprint(), db);
                                db.setTransactionSuccessful();
                                db.endTransaction();

                                ActivityMain.lockVolume(info.volumeId());
                            }


                            ArrayList<Task> removeList = new ArrayList<>();
                            for (Task t : mPendingTasks) {
                                try {
                                    SxFileInfo tinfo = new SxFileInfo(t.fileId());
                                    if (tinfo.volumeId() == info.volumeId()) {
                                        removeList.add(t);
                                    }
                                }
                                catch (Exception ex)
                                {
                                    ex.printStackTrace();
                                }
                            }
                            mPendingTasks.removeAll(removeList);
                        }
                    }
                    else if (mLastErrorCode == SXError.SXE_EAUTH) {
                        ArrayList<Task> removeList = new ArrayList<>();
                        for (Task t : mPendingTasks) {
                            try {
                                SxFileInfo tinfo = new SxFileInfo(t.fileId());
                                if (tinfo.account() == info.account()) {
                                    removeList.add(t);
                                }
                            }
                            catch (Exception ex)
                            {
                                ex.printStackTrace();
                            }
                        }
                        mPendingTasks.removeAll(removeList);
                        ActivityMain.lockAccount(info.account());
                    }

                    Fragment weak = weakReference.get();
                    if (weak instanceof PendingFragment) {
                        PendingFragment f = (PendingFragment) weak;
                        f.requestUpdate();
                    }
                }
            }
        }



        @Override
        public void onProgressChange(long size, long progress) {
            if (mTask.canceling())
            {
                mClient.requestAbortXfer();
            }
            if (size > 0)
            {
                double done = (double)progress/(double)size;
                mBuilder.setProgress(1000, (int)(1000*done), false);
                mNotificationManager.notify(mNotificationId, mBuilder.build());
                synchronized (mSyncToken) {
                    Fragment weak = weakReference.get();
                    if (weak instanceof PendingFragment) {
                        PendingFragment f = (PendingFragment) weak;
                        f.setCurrentTaskProgress(done, mTask.fileId());
                    }
                    else if (weak instanceof DownloadPageFragment)
                    {
                        DownloadPageFragment f = (DownloadPageFragment) weak;
                        f.setProgress(done, mTask.fileId());
                    }
                }
            }
        }
    }
}
