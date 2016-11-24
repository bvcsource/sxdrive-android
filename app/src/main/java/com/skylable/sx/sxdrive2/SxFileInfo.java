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

import com.skylable.sx.providers.Contract;

import java.io.File;

/**
 * Created by tangarr on 25.09.15.
 */
public class SxFileInfo {

    String mPath;
    String mVolume;
    String mAccount;
    String mRev;
    String mLocalRev;
    long mLocalDate;
    String mLocalPath;
    long mSize;
    long mId;
    long mParentId;
    long mVolumeId;
    boolean mFavourite;

    public SxFileInfo(long id) throws Exception {
        mId = id;
        SQLiteDatabase database = SxDatabaseHelper.database();
        String[] columns = new String[] {
                Contract.COLUMN_REMOTE_PATH,
                Contract.COLUMN_VOLUME_ID,
                Contract.COLUMN_SIZE,
                Contract.COLUMN_PARENT_ID,
                Contract.COLUMN_REMOTE_REV,
                Contract.COLUMN_LOCAL_PATH,
                Contract.COLUMN_LOCAL_REV,
                Contract.COLUMN_LOCALTIME,
                Contract.COLUMN_STARRED };
        String where = String.format("%s=%d", Contract.COLUMN_ID, id);
        Cursor c = database.query(SxDatabaseHelper.TABLE_FILES, columns, where, null, null, null, null);
        if (!c.moveToFirst()) {
            throw new Exception("Unable to find file ID="+mId+" in database");
        }

        mPath = c.getString(0);
        mVolumeId = c.getLong(1);
        mSize = c.getLong(2);
        mParentId = c.getLong(3);
        mRev = c.getString(4);
        mLocalPath = c.getString(5);
        mLocalRev = c.getString(6);
        mLocalDate = c.getLong(7);
        mFavourite = c.getInt(8) > 0;
        c.close();

        columns = new String[] { Contract.COLUMN_VOLUME, Contract.COLUMN_ACCOUNT_ID };
        where = String.format("%s=%d", Contract.COLUMN_ID, mVolumeId);
        c = database.query(SxDatabaseHelper.TABLE_VOLUMES, columns, where, null, null, null, null);
        c.moveToFirst();

        mVolume = c.getString(0);
        long accountId = c.getLong(1);
        c.close();

        columns = new String[] { Contract.COLUMN_ACCOUNT };
        where = String.format("%s=%d", Contract.COLUMN_ID, accountId);
        c = database.query(SxDatabaseHelper.TABLE_ACCOUNTS, columns, where, null, null, null, null);
        c.moveToFirst();

        mAccount = c.getString(0);
        c.close();
    }

    public String path()
    {
        return mPath;
    }
    public String volume()
    {
        return mVolume;
    }
    public long volumeId()
    {
        return mVolumeId;
    }
    public String account()
    {
        return mAccount;
    }
    public String localPath()
    {
        return mLocalPath;
    }
    public long parentId() { return mParentId; }
    public long size()
    {
        return mSize;
    }
    public String filename()
    {
        if (mPath.equals("/"))
            return mPath;

        int index;
        if (mPath.endsWith("/"))
        {
            int len = mPath.length();
            index = mPath.lastIndexOf("/", mPath.length()-2);
            return mPath.substring(index+1, len-1);
        }
        index = mPath.lastIndexOf("/");
        return mPath.substring(index+1);
    }

    void updateAfterDownload(String rev, String local_path, long size, long lastModified)
    {
        SQLiteDatabase database = SxDatabaseHelper.database();
        if (Build.VERSION.SDK_INT >= 11)
            database.beginTransactionNonExclusive();
        else
            database.beginTransaction();

        ContentValues values = new ContentValues();
        values.put(Contract.COLUMN_REMOTE_REV, rev);
        values.put(Contract.COLUMN_LOCAL_REV, rev);
        values.put(Contract.COLUMN_SIZE, size);
        values.put(Contract.COLUMN_LOCAL_PATH, local_path);
        values.put(Contract.COLUMN_LOCALTIME, lastModified);

        String where = String.format("%s=%d", Contract.COLUMN_ID, mId);

        database.update(SxDatabaseHelper.TABLE_FILES, values, where, null);
        database.setTransactionSuccessful();
        database.endTransaction();

        mRev = rev;
        mSize = size;
    }

    public void updateFavourite(boolean favourite)
    {
        if (mFavourite != favourite)
        {
            mFavourite = favourite;
            SQLiteDatabase database = SxDatabaseHelper.database();
            if (Build.VERSION.SDK_INT >= 11)
                database.beginTransactionNonExclusive();
            else
                database.beginTransaction();

            ContentValues values = new ContentValues();
            values.put(Contract.COLUMN_STARRED, favourite ? 1 : 0);
            String where = String.format("%s=%d", Contract.COLUMN_ID, mId);
            database.update(SxDatabaseHelper.TABLE_FILES, values, where, null);
            database.setTransactionSuccessful();
            database.endTransaction();
        }

    }

    public boolean needUpdate()
    {
        if (!TextUtils.equals(mRev, mLocalRev)) {
            return true;
        }
        File file = new File(mLocalPath);
        if (!file.exists()) {
            return true;
        }
        if (file.lastModified() != mLocalDate) {
            return true;
        }
        return false;
    }
    public boolean isDir()
    {
        Log.e("IS_DIR", "id: "+mId);
        Log.e("IS_DIR", mPath);
        return mPath.endsWith("/");
    }
    public boolean isFavourite() { return mFavourite; }

}
