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
package com.skylable.sx.providers;

import java.util.List;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

public class SxContentProvider extends ContentProvider
{
    static final String DB_NAME = "sxfiles";
    static final int DB_VERSION = 5;

    static final String TABLE_ACCOUNTS = "accounts";
    static final String TABLE_ACCOUNTS_CREATE = "CREATE TABLE " + TABLE_ACCOUNTS + "(" +
                    Contract.COLUMN_ID + " INTEGER PRIMARY KEY, " +
                    Contract.COLUMN_ACCOUNT + " TEXT NOT NULL, " +
                    "UNIQUE (" + Contract.COLUMN_ACCOUNT + ") ON CONFLICT REPLACE);";

    static final String TABLE_VOLUMES = "volumes";
    static final String TABLE_VOLUMES_CREATE = "CREATE TABLE " + TABLE_VOLUMES + "(" +
            Contract.COLUMN_ID + " INTEGER PRIMARY KEY, " +
            Contract.COLUMN_ACCOUNT_ID + " INTEGER NOT NULL, " +
            Contract.COLUMN_VOLUME + " TEXT NOT NULL, " +
            "FOREIGN KEY(" + Contract.COLUMN_ACCOUNT_ID + ") REFERENCES " + TABLE_ACCOUNTS + "(" + Contract.COLUMN_ID + "), " +
            "UNIQUE (" + Contract.COLUMN_ACCOUNT_ID + ", "+ Contract.COLUMN_VOLUME + ") ON CONFLICT REPLACE);";

    public static final String TABLE_FILES = "files";
    static final String TABLE_FILES_CREATE = "CREATE TABLE " + TABLE_FILES + "(" +
                    Contract.COLUMN_ID + " INTEGER PRIMARY KEY, " +
                    Contract.COLUMN_VOLUME_ID + " INTEGER NOT NULL, " +
                    Contract.COLUMN_REMOTE_PATH + " TEXT NOT NULL, " +
                    Contract.COLUMN_REMOTE_REV + " TEXT NOT NULL, " +
                    Contract.COLUMN_PARENT_ID + " INTEGER, " +
                    Contract.COLUMN_SIZE + " INTEGER NOT NULL DEFAULT 0, " +
                    Contract.COLUMN_LOCALTIME + " INTEGER NOT NULL DEFAULT 0, " +
                    Contract.COLUMN_LOCAL_PATH + " TEXT NOT NULL DEFAULT '', " +
                    Contract.COLUMN_LOCAL_REV + " TEXT NOT NULL DEFAULT '', " +
                    Contract.COLUMN_STARRED + " INTEGER NOT NULL DEFAULT 0, " +                    
                    "FOREIGN KEY(" + Contract.COLUMN_PARENT_ID + ") REFERENCES " + TABLE_FILES + "(" + Contract.COLUMN_ID + "), " +
                    "UNIQUE (" + Contract.COLUMN_VOLUME_ID + ", " + Contract.COLUMN_REMOTE_PATH +") ON CONFLICT REPLACE " +
                    String.format("CHECK ( %s is not null or %s = '/' )", Contract.COLUMN_PARENT_ID, Contract.COLUMN_REMOTE_PATH) +
                    ");";

    /*
    public static final String TABLE_VOLUME_ROOT = "volume_root";
    private static final String TABLE_VOLUME_ROOT_CREATE =
                    String.format("CREATE TABLE %s (", TABLE_VOLUME_ROOT) +
                    String.format("%s INTEGER REFERENCES %s(%s), ", Contract.COLUMN_VOLUME_ID, TABLE_VOLUMES, Contract.COLUMN_ID) +
                    String.format("%s INTEGER REFERENCES %s(%s), ", Contract.COLUMN_FILE_ID, TABLE_FILES, Contract.COLUMN_ID) +
                    String.format("PRIMARY KEY (%s, %s)", Contract.COLUMN_VOLUME_ID, Contract.COLUMN_FILE_ID) +
                    ");";
    */

    private static final String TRIGGER_INSERT_VOLUME = "trigger_insert_volume";
    private static final String TRIGGER_INSERT_VOLUME_CREATE =
                    String.format("create trigger if not exists %s ", TRIGGER_INSERT_VOLUME) +
                    String.format("after insert on %s ", TABLE_VOLUMES) +
                    "for each row begin " +
                    String.format("insert into %s (%s, %s, %s, %s) ", TABLE_FILES,
                            Contract.COLUMN_VOLUME_ID, Contract.COLUMN_REMOTE_PATH, Contract.COLUMN_LOCAL_REV, Contract.COLUMN_PARENT_ID) +
                    String.format("values (NEW.%s, '/', '', NULL); END;", Contract.COLUMN_ID);



    private static final String WHERE_PRUNE = Contract.COLUMN_ID + " IN (SELECT " + Contract.COLUMN_ID + " FROM " + TABLE_FILES +
                    " AS d WHERE d." + Contract.COLUMN_ISDIR + "=" + Contract.TRUE + " AND d." + Contract.COLUMN_COUNT +
                    "=0 AND (SELECT " + Contract.COLUMN_ID + " FROM " + TABLE_FILES + " WHERE " + Contract.COLUMN_ISDIR +
                    "=" + Contract.TRUE + " AND " + Contract.COLUMN_FULLPATH + " GLOB d." + Contract.COLUMN_FULLPATH + " || '/*') = 0)";

    private static final String WHERE_RECURSE = Contract.COLUMN_FULLPATH + " GLOB (SELECT " + Contract.COLUMN_FULLPATH +
                    " FROM " + TABLE_FILES + " WHERE " + Contract.COLUMN_ID + "=?) || '/*'";

    private static SXDbHelper mOpenHelper = null;

    private static final String MIME_DIR = "vnd.android.cursor.dir/com.skylable.sx.provider.files";
    private static final String MIME_FILE = "vnd.android.cursor.item/com.skylable.sx.provider.files";

    private static final int URI_ROOT = 0;
    private static final int URI_DIR = 1;
    private static final int URI_FILE = 2;
    private static final int URI_ACCOUNT = 10;
    private static final int URI_PRUNE = 20;
    private static final int URI_CACHED = 30;

    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static
    {
        sURIMatcher.addURI(Contract.AUTHORITY, null, URI_ROOT);
        sURIMatcher.addURI(Contract.AUTHORITY, "dir/", URI_DIR);
        sURIMatcher.addURI(Contract.AUTHORITY, "dir/#", URI_DIR);
        sURIMatcher.addURI(Contract.AUTHORITY, "filePath/", URI_FILE);
        sURIMatcher.addURI(Contract.AUTHORITY, "filePath/#", URI_FILE);
        sURIMatcher.addURI(Contract.AUTHORITY, "account/", URI_ACCOUNT);
        sURIMatcher.addURI(Contract.AUTHORITY, "account/*", URI_ACCOUNT);
        sURIMatcher.addURI(Contract.AUTHORITY, "prune/*", URI_PRUNE);
        sURIMatcher.addURI(Contract.AUTHORITY, "cached/*", URI_CACHED);
    }

    @Override
    public boolean onCreate()
    {
        Log.e("SxContentProvider", "onCreate");
        mOpenHelper = new SXDbHelper(getContext());
        return true;
    }

    public static SQLiteDatabase getWritableDatabase()
    {
        return mOpenHelper.getWritableDatabase();
    }

    private String getArgument(Uri uri)
    {
        List<String> segments = uri.getPathSegments();
        if (segments.size() < 2)
            return null;
        return segments.get(1);
    }

    private String getArgumentOrThrow(Uri uri) throws IllegalArgumentException
    {
        String id = getArgument(uri);
        if (id == null)
            throw new IllegalArgumentException("Invalid Uri " + uri.toString());
        return id;
    }


    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
    {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        SQLiteQueryBuilder query = new SQLiteQueryBuilder();

        final String param = getArgument(uri);
        final boolean recurse = (uri.getQueryParameter(Contract.PARAM_RECURSE) != null);

        int match = sURIMatcher.match(uri);
        query.setTables((match == URI_ACCOUNT) ? TABLE_ACCOUNTS : TABLE_FILES);
        switch (match)
        {
            case URI_ACCOUNT:
            {
                // TODO: avoid selection takeover
                if (param != null)
                {
                    selection = Contract.COLUMN_ACCOUNT + "=?";
                    selectionArgs = new String[] { param };
                }
                break;
            }
            case URI_ROOT:
                break;
            case URI_DIR:
                if (param != null)
                {
                    if (recurse)
                    {
                        selection = WHERE_RECURSE;
                        selectionArgs = new String[] { param };
                    }
                    else
                        query.appendWhere(Contract.COLUMN_PARENT_ID + "=" + param);
                }
                break;
            case URI_FILE:
                if (param != null)
                    query.appendWhere(Contract.COLUMN_ID + "=" + param);
                break;
            default:
                throw new IllegalArgumentException("Unknown Uri " + uri.toString());
        }
        Cursor cursor = query.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Override
    public String getType(Uri uri)
    {
        return null;
        /*
        int match = sURIMatcher.match(uri);
        switch (match)
        {
            case URI_ROOT:
            case URI_DIR:
                return MIME_DIR;
            case URI_FILE:
                return MIME_FILE;
            default:
                throw new IllegalArgumentException("Unknown Uri " + uri.toString());
        }
        */
    }

    // the UI will register the objserver on the parent directory
    private void sendNotify(SQLiteDatabase db, String id, Uri uri)
    {
        /*
        if (uri.getQueryParameter(Contract.PARAM_NOTIFY) == null)
            return;

        // get parent id (for notification)
        final String[] projection = new String[] { Contract.COLUMN_PARENT };
        final String selection = Contract.COLUMN_ID + "=?";
        final String[] selectionArgs = new String[] { id };
        final Cursor cursor = db.query(TABLE_FILES, projection, selection, selectionArgs, null, null, null, "1");
        try
        {
            if (cursor.moveToFirst())
            {
                final String parent = cursor.getString(cursor.getColumnIndex(Contract.COLUMN_PARENT));
                final Uri parent_uri = Uri.withAppendedPath(Contract.CONTENT_URI_DIR, parent);
                getContext().getContentResolver().notifyChange(parent_uri, null);
            }
            else
                throw new IllegalArgumentException("Invalid id " + id);
        }
        finally
        {
            cursor.close();
        }
        */
    }

    @Override
    public Uri insert(Uri uri, ContentValues values)
    {
        return null;
        /*
        Uri newUri;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        int match = sURIMatcher.match(uri);
        switch (match)
        {
            case URI_ACCOUNT:
            {
                final String account = getArgumentOrThrow(uri);
                values.put(Contract.COLUMN_ACCOUNT, account);
                long id = db.insertOrThrow(TABLE_ACCOUNTS, null, values);
                return Uri.withAppendedPath(Contract.CONTENT_URI_ACCOUNT, String.valueOf(id));
            }
            case URI_ROOT:
                throw new IllegalArgumentException("Cannot insert root directory");
            case URI_DIR:
                newUri = Contract.CONTENT_URI_DIR;
                break;
            case URI_FILE:
                newUri = Contract.CONTENT_URI_FILE;
                break;
            default:
                throw new IllegalArgumentException("Unknown Uri " + uri.toString());
        }

        newUri = Uri.withAppendedPath(newUri, String.valueOf(db.insertOrThrow(TABLE_FILES, null, values)));
        if (uri.getQueryParameter(Contract.PARAM_NOTIFY) != null)
        {
            final Uri parent_uri = Uri.withAppendedPath(Contract.CONTENT_URI_DIR, values.getAsString(Contract.COLUMN_PARENT));
            getContext().getContentResolver().notifyChange(parent_uri, null);
        }
        return newUri;
        */
    }

    @Override
    public int bulkInsert(Uri uri, @NonNull ContentValues[] values)
    {
        return 0;
        /*
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int match = sURIMatcher.match(uri);

        String id;
        switch (match)
        {
            case URI_ROOT:
                id = Contract.ID_ROOT_DIR;
                break;
            case URI_DIR:
                id = uri.getLastPathSegment();
                break;
            default:
                throw new IllegalArgumentException("Unknown Uri " + uri.toString());
        }

        db.beginTransaction();
        try
        {
            for (ContentValues cv: values)
            {
                cv.put(Contract.COLUMN_PARENT, id);
                db.insertOrThrow(TABLE_FILES, null, cv);
            }
            db.setTransactionSuccessful();
        }
        finally
        {
            db.endTransaction();
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return values.length;
        */
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs)
    {
        return 0;
        /*
        int rowsDeleted;
        String id;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        int match = sURIMatcher.match(uri);
        switch (match)
        {
            case URI_ACCOUNT:
            {
                String account = getArgument(uri);
                if (account != null)
                {
                    selection = Contract.COLUMN_ACCOUNT + "=?";
                    selectionArgs = new String[] { account };
                }
                return db.delete(TABLE_ACCOUNTS, selection, selectionArgs);
            }
            case URI_PRUNE:
            {
                String account = getArgumentOrThrow(uri);
                selection = Contract.COLUMN_ACCOUNT + "=? AND " + WHERE_PRUNE;
                selectionArgs = new String[] { account };
                return db.delete(TABLE_FILES, selection, selectionArgs);
            }
            case URI_ROOT:
                throw new IllegalArgumentException("Cannot delete root directory");
            case URI_DIR:
            {
                id = getArgumentOrThrow(uri);
                selection = Contract.COLUMN_FULLPATH + " GLOB (SELECT " + Contract.COLUMN_FULLPATH +
                                " FROM " + TABLE_FILES + " WHERE " + Contract.COLUMN_ID + "=?) || '/*'";
                selectionArgs = new String[] { id };
                rowsDeleted = db.delete(TABLE_FILES, selection, selectionArgs);

                selection = Contract.COLUMN_ID + "=?";
                rowsDeleted += db.delete(TABLE_FILES, selection, selectionArgs);
                break;
            }
            case URI_FILE:
            {
                id = getArgument(uri);
                if (id != null)
                {
                    selection = Contract.COLUMN_ID + "=?";
                    selectionArgs = new String[] { id };
                }
                rowsDeleted = db.delete(TABLE_FILES, selection, selectionArgs);
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown Uri " + uri.toString());
        }

        if (rowsDeleted > 0)
            sendNotify(db, id, uri);

        return rowsDeleted;
        */
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs)
    {
        return 0;
        /*
        int rowsUpdated;
        String id;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        int match = sURIMatcher.match(uri);
        switch (match)
        {
            case URI_ACCOUNT:
            {
                final String account = getArgumentOrThrow(uri);
                selection = Contract.COLUMN_ACCOUNT + "=?";
                selectionArgs = new String[] { account };
                return db.update(TABLE_ACCOUNTS, values, selection, selectionArgs);
            }
            case URI_ROOT:
                throw new IllegalArgumentException("Cannot update root directory");
            case URI_DIR:
            {
                id = getArgumentOrThrow(uri);
                selection = Contract.COLUMN_FULLPATH + " GLOB (SELECT " + Contract.COLUMN_FULLPATH +
                                " FROM " + TABLE_FILES + " WHERE " + Contract.COLUMN_ID + "=?) || '/*'";
                selectionArgs = new String[] { id };
                rowsUpdated = db.update(TABLE_FILES, values, selection, selectionArgs);
            }
            case URI_FILE:
            {
                id = getArgumentOrThrow(uri);
                selection = Contract.COLUMN_ID + "=?";
                selectionArgs = new String[] { id };
                rowsUpdated = db.update(TABLE_FILES, values, selection, selectionArgs);
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown Uri " + uri.toString());
        }

        if (rowsUpdated == 1)
            sendNotify(db, id, uri);

        return rowsUpdated;
        */
    }

    public class SXDbHelper extends SQLiteOpenHelper
    {
        public SXDbHelper(Context context)
        {
            super(context, DB_NAME, null, DB_VERSION);
            getWritableDatabase().execSQL("PRAGMA synchronous=NORMAL");
        }

        @Override
        public void onCreate(SQLiteDatabase db)
        {
            db.execSQL(TABLE_ACCOUNTS_CREATE);
            db.execSQL(TABLE_VOLUMES_CREATE);
            db.execSQL(TABLE_FILES_CREATE);
            db.execSQL(TRIGGER_INSERT_VOLUME_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
        {
            if (oldVersion != DB_VERSION)
            {
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_ACCOUNTS);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_FILES);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_VOLUMES);
                this.onCreate(db);
            }
        }
    }
}
