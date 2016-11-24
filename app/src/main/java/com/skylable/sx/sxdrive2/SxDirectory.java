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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.skylable.sx.app.SxApp;
import com.skylable.sx.jni.SXClient;
import com.skylable.sx.jni.SXDirEntry;
import com.skylable.sx.jni.SXDirEntryVec;
import com.skylable.sx.jni.SXNativeException;
import com.skylable.sx.jni.SXUri;
import com.skylable.sx.providers.Contract;
import com.skylable.sx.util.FileOps;

import org.w3c.dom.Text;

import java.io.File;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

/**
 * Created by tangarr on 22.09.15.
 */
public class SxDirectory {

    private long mId;
    private long mVolumeId;
    private int mStarred;
    private String mPath;

    private static DateFormat sDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public SxDirectory(long id)
    {
        mId = id;
        String[] columns = new String[] { Contract.COLUMN_VOLUME_ID, Contract.COLUMN_REMOTE_PATH, Contract.COLUMN_STARRED };
        String where = String.format("%s=%d", Contract.COLUMN_ID, mId);

        SQLiteDatabase database = SxDatabaseHelper.database();
        Cursor c = database.query(SxDatabaseHelper.TABLE_FILES, columns, where, null, null, null, null );

        if (!c.moveToFirst())
        {
            Log.e("CRASH", "dir_id="+id);
        }

        mVolumeId = c.getLong(0);
        mPath = c.getString(1);
        mStarred = c.getInt(2);

        if (mPath.length() > 1) {
            mPath = mPath.substring(0, mPath.length() - 1);
        }
        c.close();
    }

    public long id()
    {
        return mId;
    }

    public String name()
    {
        if (mPath.equals("/"))
            return mPath;
        else
        {
            int index = mPath.lastIndexOf("/");
            return mPath.substring(index);
        }
    }

    public String path()
    {
        return mPath;
    }

    public long insertChild(String path, String rev, long size, SQLiteDatabase database)
    {
        path = path.replace("//","/");
        ContentValues values = new ContentValues();
        values.put(Contract.COLUMN_REMOTE_PATH, path);
        values.put(Contract.COLUMN_REMOTE_REV, rev);
        values.put(Contract.COLUMN_SIZE, size);
        values.put(Contract.COLUMN_VOLUME_ID, mVolumeId);
        values.put(Contract.COLUMN_PARENT_ID, mId);
        values.put(Contract.COLUMN_STARRED, mStarred);

        return database.insert(SxDatabaseHelper.TABLE_FILES, null, values);
    }


    void updateChild(long id, String rev, long size, SQLiteDatabase database)
    {
        ContentValues values = new ContentValues();
        values.put(Contract.COLUMN_REMOTE_REV, rev);
        values.put(Contract.COLUMN_SIZE, size);
        String where = String.format("%s=%d", Contract.COLUMN_ID, id);
        database.update(SxDatabaseHelper.TABLE_FILES, values, where, null);

    }

    void removeChild(long id, String child, SQLiteDatabase database)
    {
        if (child.endsWith("/"))
        {
            //TODO crete CTE selecting all subdirectories to remove
        }
        else
        {
            //TODO remove filePath if exists (LOCAL_PATH)
        }
        String where = String.format("%s=%d", Contract.COLUMN_ID, id);
        database.delete(SxDatabaseHelper.TABLE_FILES, where, null);
    }

    boolean updateDirectory(SXClient client)
    {
        if (client == null)
            return false;


        String volume = volume();
        String path = mPath;
        if (!path.endsWith("/"))
        {
            path+='/';
        }
        String uri = String.format("%s/%s%s", client.clusterUri(), volume, path);

        String etag_file = mId+".etag";

        SXDirEntryVec list;
        SXUri sxuri;
        try {
            sxuri = client.parseUri(uri);
            list = client.listFiles(sxuri, false, etag_file);
        } catch (SXNativeException e) {
            if (TextUtils.equals(e.what(), "Not modified"))
                Log.i("SxDirectory", mPath+" not modified");
            else
                e.printStackTrace();
            return false;
        }

        HashMap<String, Long> cachedFiles = new HashMap<>();

        String[] columns = new String[] { Contract.COLUMN_REMOTE_PATH, Contract.COLUMN_ID };
        String where = String.format("%s=%d", Contract.COLUMN_PARENT_ID, mId);

        SQLiteDatabase database = SxDatabaseHelper.database();

        Cursor c = database.query(SxDatabaseHelper.TABLE_FILES, columns, where, null, null, null, null);
        while (c.moveToNext())
        {
            String file_path = c.getString(0);
            long file_id = c.getLong(1);
            cachedFiles.put(file_path, file_id);
        }
        c.close();

        if (Build.VERSION.SDK_INT >= 11)
            database.beginTransactionNonExclusive();
        else
            database.beginTransaction();

        int counter = 0;

        for (SXDirEntry e: list) {
            if (cachedFiles.containsKey(e.getName()))
            {
                long file_id = cachedFiles.get(e.getName());
                cachedFiles.remove(e.getName());
                if (e.getName().endsWith("/"))
                    continue;
                String rev = e.getRev();
                long size = e.getSize();
                updateChild(file_id, rev, size, database);
            }
            else
            {
                if (e.getName().endsWith("/"))
                    insertChild(e.getName(), "", 0, database);
                else
                    insertChild(e.getName(), e.getRev(), e.getSize(), database);
            }
            counter++;
            if (counter >= 100)
            {
                database.yieldIfContendedSafely();
                counter = 0;
            }
        }

        for (String file: cachedFiles.keySet() ) {
            long file_id = cachedFiles.get(file);
            removeChild(file_id, file, database);
            counter++;
            if (counter >= 100)
            {
                database.yieldIfContendedSafely();
                counter = 0;
            }
        }
        database.setTransactionSuccessful();
        database.endTransaction();
        return true;
    }

    private String volume()
    {
        String[] columns = new String[] { Contract.COLUMN_VOLUME};
        String where = String.format("%s=%d", Contract.COLUMN_ID, mVolumeId);
        SQLiteDatabase database = SxDatabaseHelper.database();
        Cursor c = database.query(SxDatabaseHelper.TABLE_VOLUMES, columns, where, null, null, null, null);
        c.moveToFirst();
        String vol = c.getString(0);
        c.close();
        return vol;
    }

    public ArrayList<SxFile> fileList(boolean hideFiles)
    {
        ArrayList<SxFile> files = new ArrayList<>();
        ArrayList<SxFile> directories = new ArrayList<>();
        String[] columns = new String[] { Contract.COLUMN_REMOTE_PATH, Contract.COLUMN_SIZE, Contract.COLUMN_STARRED, Contract.COLUMN_ID, Contract.COLUMN_REMOTE_REV };
        String where = String.format("%s=%s", Contract.COLUMN_PARENT_ID, mId);
        String order = Contract.COLUMN_REMOTE_PATH;

        SQLiteDatabase database = SxDatabaseHelper.database();
        Cursor c = database.query(SxDatabaseHelper.TABLE_FILES, columns, where, null, null, null, order);
        while (c.moveToNext())
        {
            String path = c.getString(0);
            if (path.endsWith("/.sxnewdir"))
                continue;

            long size = c.getLong(1);
            boolean starred = c.getInt(2) > 0;
            long id = c.getLong(3);
            String rev = c.getString(4);
            SxFile f = new SxFile(id, path, starred, size, rev);
            if (f.isDir())
                directories.add(f);
            else if (!hideFiles)
                files.add(f);
        }
        c.close();
        directories.addAll(files);
        return directories;
    }

    static class SxFile
    {
        private long mId;
        private long mSize;
        private String mPath;
        private boolean mStarred;
        private String mRevision;

        SxFile(long id, String path, boolean starred, long size, String revision)
        {
            mPath = path;
            mId = id;
            mSize = size;
            mStarred = starred;
            mRevision = revision;
        }
        public boolean isDir()
        {
            return mPath.endsWith("/");
        }
        public boolean starred()
        {
            return mStarred;
        }
        public String name()
        {
            int len, index;
            if (mPath.endsWith("/")) {
                index = mPath.lastIndexOf("/", mPath.length() - 2) + 1;
                len = mPath.length() - 1;
            }
            else
            {
                index = mPath.lastIndexOf("/")+1;
                len = mPath.length();
            }
            return mPath.substring(index, len);
        }
        public String parentPath() {
            int len;
            if (mPath.endsWith("/"))
                len = mPath.lastIndexOf("/", mPath.length() - 2);
            else
                len = mPath.lastIndexOf("/");
            return mPath.substring(0, len);
        }
        public String path() { return mPath;}
        public long size()
        {
            return mSize;
        }
        public long id()
        {
            return mId;
        }
        public String revision() {
            return mRevision;
        }
        public String created_at() {
            if (mRevision == null)
                return null;
            else {
                int index = mRevision.indexOf('.');
                if (index <= 0)
                    return null;
                String time_str = mRevision.substring(0, index);
                try {
                    sDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                    Date utc_date = sDateFormat.parse(time_str);
                    sDateFormat.setTimeZone(TimeZone.getDefault());
                    time_str = sDateFormat.format(utc_date);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                return time_str;
            }
        }

    }
}
