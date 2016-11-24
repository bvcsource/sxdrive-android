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
import android.provider.SyncStateContract;
import android.text.TextUtils;
import android.util.Log;

import com.skylable.sx.app.SxApp;
import com.skylable.sx.providers.Contract;
import com.skylable.sx.util.FileOps;

import java.util.ArrayList;

/**
 * Created by tangarr on 21.09.15.
 */
public class SxVolume {

    private long mId;
    private String mVolume, mAesFingerprint;
    private int mEncrypted;
    private long mUsed, mSize;

    private SxVolume(long id, String volume, int encrypted, String fingerprint, long used, long size)
    {
        mId = id;
        mVolume = volume;
        mEncrypted = encrypted;
        mAesFingerprint = fingerprint;
        mUsed = used;
        mSize = size;
    }

    static public SxVolume loadVolume(long id)
    {
        SxVolume vol = null;
        SQLiteDatabase db = SxDatabaseHelper.database();
        String[] columns = new String[] { Contract.COLUMN_VOLUME, Contract.COLUMN_ENCRYPTED, Contract.COLUMN_USED, Contract.COLUMN_SIZE, Contract.COLUMN_AES_FINGERPRINT };
        String where = Contract.COLUMN_ID + "=" + id;
        Cursor cursor = db.query(SxDatabaseHelper.TABLE_VOLUMES, columns, where, null, null, null, null );
        if (cursor.moveToFirst())
        {
            String volume = cursor.getString(0);
            int encrypted = cursor.getInt(1);
            long used = cursor.getLong(2);
            long size = cursor.getLong(3);
            String fp = cursor.getString(4);
            vol = new SxVolume(id, volume, encrypted, fp, used, size);
        }
        cursor.close();
        return vol;
    }

    static public ArrayList<SxVolume> loadSxVolumes(long account_id)
    {
        SQLiteDatabase db = SxDatabaseHelper.database();

        ArrayList<SxVolume> volumes = new ArrayList<>();
        String[] columns = new String[] { Contract.COLUMN_ID, Contract.COLUMN_VOLUME, Contract.COLUMN_ENCRYPTED, Contract.COLUMN_USED, Contract.COLUMN_SIZE, Contract.COLUMN_AES_FINGERPRINT };
        String where = Contract.COLUMN_ACCOUNT_ID + "=" + account_id;
        Cursor cursor = db.query(SxDatabaseHelper.TABLE_VOLUMES, columns, where, null, null, null, null );
        while (cursor.moveToNext())
        {
            long id = cursor.getInt(0);
            String volume = cursor.getString(1);
            int encrypted = cursor.getInt(2);
            long used = cursor.getLong(3);
            long size = cursor.getLong(4);
            String fp = cursor.getString(5);
            volumes.add(new SxVolume(id, volume, encrypted, fp, used, size));
        }
        cursor.close();
        return volumes;
    }

    public String name()
    {
        return mVolume;
    }

    public String sizeFormated()
    {
        return FileOps.humanReadableByteCount(mSize, false);
    }

    public double used()
    {
        if (mSize > 0)
            return (double)mUsed/(double)mSize;
        else
            return 0;
    }

    public void updateDatabase(int encrypted, String aes_fingerprint, SQLiteDatabase db)
    {
        updateDatabase(encrypted, aes_fingerprint, mUsed, mSize, db);
    }

    public void updateDatabase(int encrypted, String aes_fingerprint, long used, long size, SQLiteDatabase db)
    {
        if (encrypted == 2 && aes_fingerprint.isEmpty())
            encrypted = 1;
        else if (encrypted == 4 && aes_fingerprint.isEmpty())
            encrypted = 3;

        ContentValues values = new ContentValues();
        if (mEncrypted != encrypted)
        {
            mEncrypted = encrypted;
            values.put(Contract.COLUMN_ENCRYPTED, mEncrypted);
        }
        if (!TextUtils.equals(mAesFingerprint, aes_fingerprint))
        {
            mAesFingerprint = aes_fingerprint;
            values.put(Contract.COLUMN_AES_FINGERPRINT, mAesFingerprint);
        }
        if (mUsed != used)
        {
            mUsed = used;
            values.put(Contract.COLUMN_USED, used);
        }
        if (mSize != size)
        {
            mSize = size;
            values.put(Contract.COLUMN_SIZE, size);
        }
        if (values.size() == 0)
            return;
        String where = Contract.COLUMN_ID+"="+mId;

        db.update(SxDatabaseHelper.TABLE_VOLUMES, values, where, null);
    }

    public static void insertToDatabase(long account_id, String volume, int encrypted, String aes_fingerprint, long used, long size, SQLiteDatabase db)
    {
        ContentValues values = new ContentValues();
        values.put(Contract.COLUMN_ACCOUNT_ID, account_id );
        values.put(Contract.COLUMN_VOLUME, volume);
        values.put(Contract.COLUMN_ENCRYPTED, encrypted);
        values.put(Contract.COLUMN_AES_FINGERPRINT, aes_fingerprint);
        values.put(Contract.COLUMN_USED, used );
        values.put(Contract.COLUMN_SIZE, size );
        db.insert(SxDatabaseHelper.TABLE_VOLUMES, null, values);

    }
    public void removeFromDatabase(SQLiteDatabase db)
    {
        String where = Contract.COLUMN_ID+"="+mId;
        db.delete(SxDatabaseHelper.TABLE_VOLUMES, where, null);
    }

    public int encrypted()
    {
        return mEncrypted;
    }

    public long volumeId()
    {
        return mId;
    }
    public SxDirectory rootDirectory()
    {
        return rootDirectory(mId);
    }

    static public SxDirectory rootDirectory(long volume_id)
    {
        SQLiteDatabase db = SxDatabaseHelper.database();
        String[] columns = new String[] { Contract.COLUMN_ID };
        String where = String.format("%s=%d AND %s='/'", Contract.COLUMN_VOLUME_ID, volume_id, Contract.COLUMN_REMOTE_PATH);

        Cursor c = db.query(SxDatabaseHelper.TABLE_FILES, columns, where, null, null, null, null);
        c.moveToFirst();
        long id = c.getLong(0);
        c.close();
        return new SxDirectory(id);
    }

    static public long rootDirectoryId(long volume_id)
    {
        SQLiteDatabase db = SxDatabaseHelper.database();
        String[] columns = new String[] { Contract.COLUMN_ID };
        String where = String.format("%s=%d AND %s='/'", Contract.COLUMN_VOLUME_ID, volume_id, Contract.COLUMN_REMOTE_PATH);

        Cursor c = db.query(SxDatabaseHelper.TABLE_FILES, columns, where, null, null, null, null);
        c.moveToFirst();
        long id = c.getLong(0);
        c.close();
        return id;
    }

    public String aesFingerprint()
    {
        return mAesFingerprint;
    }
}
