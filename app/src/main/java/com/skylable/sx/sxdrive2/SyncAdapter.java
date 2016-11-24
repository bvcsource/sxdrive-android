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

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;

import com.skylable.sx.app.SxApp;
import com.skylable.sx.jni.SXClient;
import com.skylable.sx.providers.Contract;
import com.skylable.sx.ui.activities.ActivityMain;

import java.util.ArrayList;

/**
 * Created by tangarr on 19.10.15.
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {

    public static final Object sSyncToken = new Object();

    private SXClient mClient = null;

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
    }

    ArrayList<Long> findFavouriteFilesParents(SxVolume volume)
    {
        ArrayList<Long> result = new ArrayList<>();

        String rawQuery =
                String.format("SELECT a.%s from %s a, %s b WHERE ", Contract.COLUMN_ID, SxDatabaseHelper.TABLE_FILES, SxDatabaseHelper.TABLE_FILES) +
                String.format("a.%s = %d AND ", Contract.COLUMN_VOLUME_ID, volume.volumeId()) +
                String.format("a.%s = b.%s AND ", Contract.COLUMN_ID, Contract.COLUMN_PARENT_ID) +
                String.format("a.%s = 0 AND ", Contract.COLUMN_STARRED) +
                String.format("b.%s = 1 GROUP BY a.%s;", Contract.COLUMN_STARRED, Contract.COLUMN_ID);

        SQLiteDatabase db = SxDatabaseHelper.database();
        Cursor c = db.rawQuery(rawQuery, null);

        while (c.moveToNext())
        {
            result.add(c.getLong(0));
        }
        c.close();

        return result;
    }

    ArrayList<Long> findFavouriteFiles(SxVolume volume)
    {
        ArrayList<Long> result = new ArrayList<>();

        String rawQuery =
                String.format("SELECT %s from %s WHERE ", Contract.COLUMN_ID, SxDatabaseHelper.TABLE_FILES) +
                        String.format("%s = %d AND ", Contract.COLUMN_VOLUME_ID, volume.volumeId()) +
                        String.format("%s not like '%s' AND ", Contract.COLUMN_REMOTE_PATH, "%/") +
                        String.format("%s = 1;", Contract.COLUMN_STARRED);

        SQLiteDatabase db = SxDatabaseHelper.database();
        Cursor c = db.rawQuery(rawQuery, null);

        while (c.moveToNext())
        {
            result.add(c.getLong(0));
        }
        c.close();

        return result;
    }

    ArrayList<Long> findFavouriteDirectories(SxVolume volume)
    {
        ArrayList<Long> result = new ArrayList<>();

        String rawQuery =
                String.format("SELECT b.%s from %s a, %s b WHERE ", Contract.COLUMN_ID, SxDatabaseHelper.TABLE_FILES, SxDatabaseHelper.TABLE_FILES) +
                        String.format("a.%s = %d AND ", Contract.COLUMN_VOLUME_ID, volume.volumeId()) +
                        String.format("b.%s like '%s' AND ", Contract.COLUMN_REMOTE_PATH, "%/") +
                        String.format("a.%s = b.%s AND ", Contract.COLUMN_ID, Contract.COLUMN_PARENT_ID) +
                        String.format("a.%s = 0 AND ", Contract.COLUMN_STARRED) +
                        String.format("b.%s = 1 GROUP BY b.%s;", Contract.COLUMN_STARRED, Contract.COLUMN_ID);

        SQLiteDatabase db = SxDatabaseHelper.database();
        Cursor c = db.rawQuery(rawQuery, null);

        while (c.moveToNext())
        {
            result.add(c.getLong(0));
        }
        c.close();

        return result;
    }


    ArrayList<Long> refreshDirectory(long id, SXClient client, boolean returnSubDirs)
    {
        ArrayList<Long> list = new ArrayList<>();
        SxDirectory dir = new SxDirectory(id);
        dir.updateDirectory(client);
        if (returnSubDirs) {
            for (SxDirectory.SxFile subDir : dir.fileList(true)) {
                if (subDir.starred())
                    list.add(subDir.id());
            }
            return list;
        }
        else
            return null;
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        SxAccount a = new SxAccount(account);
        mClient = SxApp.getClient(a);
        if (mClient == null)
            return;

        if (!a.updateVolumeList(mClient))
        {
            Log.e("SyncAdapter", "Failed to UpdateVolumeList");
            return;
        }

        for (SxVolume v : a.volumes())
        {
            if (v.encrypted() == 1)
                continue;

            for (long id : findFavouriteFilesParents(v))
            {
                refreshDirectory(id, mClient, false);
            }

            ArrayList<Long> dirs = findFavouriteDirectories(v);
            while (!dirs.isEmpty())
            {
                long id = dirs.get(0);
                dirs.remove(0);
                dirs.addAll(refreshDirectory(id, mClient, true));
            }
            ActivityMain.onFavouriteFilesChanged(a.name(), null);

            for (long id: findFavouriteFiles(v))
            {
                try {
                    SxFileInfo fileInfo = new SxFileInfo(id);
                    if (fileInfo.filename().equals(".sxnewdir"))
                        continue;
                    if (fileInfo.needUpdate() && SxApp.sSyncQueue.indexOf(id) == -1) {
                        SxApp.sSyncQueue.requestDownload(id, true);
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
    public void onSyncCanceled() {
        super.onSyncCanceled();
        if (mClient != null)
            mClient.requestAbortXfer();
    }
}
