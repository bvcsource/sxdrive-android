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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.util.Log;

import com.skylable.sx.app.SxApp;
import com.skylable.sx.providers.Contract;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by tangarr on 21.09.15.
 */
public class SxDatabaseHelper extends SQLiteOpenHelper {

    static final String DB_NAME = "sxfiles";
    static final int DB_VERSION = 112; // TODO set value to 5+

    static final String TABLE_ACCOUNTS = "accounts";
    static final String TABLE_VOLUMES = "volumes";
    public static final String TABLE_FILES = "files";

    private static SQLiteDatabase sDatabase = null;

    static final String TRIGGER_INSERT_VOLUME = "trigger_insert_volume";
    static final String TRIGGER_UPDATE_FILE = "trigger_update_file";
    static final String TRIGGER_REMOVE_FILE = "trigger_remove_file";
    static final String TRIGGER_UPDATE_VOLUME = "trigger_update_volume";

    private static final String CREATE_TABLE_ACCOUNTS = "CREATE TABLE " + TABLE_ACCOUNTS + "(" +
            Contract.COLUMN_ID + " INTEGER PRIMARY KEY, " +
            Contract.COLUMN_ACCOUNT + " TEXT NOT NULL, " +
            "UNIQUE (" + Contract.COLUMN_ACCOUNT + ") ON CONFLICT REPLACE);";


    // COLUMN_ENCRYPTED VALUES:
    // 0 - volumeName without encryption
    // 1 - encrypted volumeName - locked
    // 2 - encrypted volumeName - unlocked

    static final String CREATE_TABLE_VOLUMES = "CREATE TABLE " + TABLE_VOLUMES + "(" +
            Contract.COLUMN_ID + " INTEGER PRIMARY KEY, " +
            String.format("%s INTEGER NOT NULL, ", Contract.COLUMN_ACCOUNT_ID) +
            String.format("%s TEXT NOT NULL, ", Contract.COLUMN_VOLUME) +
            String.format("%s INTEGER NOT NULL, ", Contract.COLUMN_ENCRYPTED) +
            String.format("%s TEXT NOT NULL DEFAULT '', ", Contract.COLUMN_AES_FINGERPRINT) +
            String.format("%s INTEGER NOT NULL, ", Contract.COLUMN_USED) +
            String.format("%s INTEGER NOT NULL, ", Contract.COLUMN_SIZE) +

            "FOREIGN KEY(" + Contract.COLUMN_ACCOUNT_ID + ") REFERENCES " + TABLE_ACCOUNTS + "(" + Contract.COLUMN_ID + "), " +
            "UNIQUE (" + Contract.COLUMN_ACCOUNT_ID + ", "+ Contract.COLUMN_VOLUME + ") ON CONFLICT REPLACE);";

    static final String CREATE_TABLE_FILES = "CREATE TABLE " + TABLE_FILES + "(" +
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

    private static final String CREATE_TRIGGER_UPDATE_FILE =
            String.format("create trigger if not exists %s ", TRIGGER_UPDATE_FILE) +
                    String.format("after update of %s on %s ", Contract.COLUMN_STARRED, TABLE_FILES) +
                    "for each row BEGIN " +
                    String.format("update %s set %s = NEW.%s ", TABLE_FILES, Contract.COLUMN_STARRED, Contract.COLUMN_STARRED) +
                    String.format("where %s = OLD.%s ; END;", Contract.COLUMN_PARENT_ID, Contract.COLUMN_ID);

    private static final String CREATE_TRIGGER_REMOVE_FILE =
            String.format("create trigger if not exists %s ", TRIGGER_REMOVE_FILE) +
                    String.format("before delete on %s ", TABLE_FILES) +
                    String.format("for each row when OLD.%s like '%s' BEGIN ", Contract.COLUMN_REMOTE_PATH, "%/" ) +
                    String.format("delete from %s ", TABLE_FILES) +
                    String.format("where %s = OLD.%s ; END;", Contract.COLUMN_PARENT_ID, Contract.COLUMN_ID);

    private static final String CREATE_TRIGGER_INSERT_VOLUME =
            String.format("create trigger if not exists %s ", TRIGGER_INSERT_VOLUME) +
                    String.format("after insert on %s ", TABLE_VOLUMES) +
                    "for each row begin " +
                    String.format("insert into %s (%s, %s, %s, %s) ", TABLE_FILES,
                            Contract.COLUMN_VOLUME_ID, Contract.COLUMN_REMOTE_PATH, Contract.COLUMN_REMOTE_REV, Contract.COLUMN_PARENT_ID) +
                    String.format("values (NEW.%s, '/', '', NULL); END;", Contract.COLUMN_ID);

    private static final String CREATE_TRIGGER_UPDATE_VOLUME =
            String.format("create trigger if not exists %s ", TRIGGER_UPDATE_VOLUME) +
                    String.format("after update of %s on %s ", Contract.COLUMN_AES_FINGERPRINT, TABLE_VOLUMES) +
                    "for each row when " +
                    String.format("OLD.%s = NEW.%s ", Contract.COLUMN_ENCRYPTED, Contract.COLUMN_ENCRYPTED) +
                    "BEGIN " +
                    String.format("update %s set %s = 1 ", TABLE_VOLUMES, Contract.COLUMN_ENCRYPTED) +
                    String.format("where %s = OLD.%s AND %s = 2 ; ", Contract.COLUMN_ID, Contract.COLUMN_ID, Contract.COLUMN_ENCRYPTED) +
                    String.format("update %s set %s = 3 ", TABLE_VOLUMES, Contract.COLUMN_ENCRYPTED) +
                    String.format("where %s = OLD.%s AND %s = 4 ; ", Contract.COLUMN_ID, Contract.COLUMN_ID, Contract.COLUMN_ENCRYPTED) +
                    "END;";

    ArrayList<File> etags (File configDir)
    {
        Log.e("GET_ETAGS", "start");
        ArrayList<File> result = new ArrayList<>();
        String clusters[] = configDir.list();
        if (clusters == null) {
            Log.e("GET_ETAGS", "null");
            return result;
        }
        for (String cluster_str : clusters)
        {
            File cluster = new File(configDir.getAbsolutePath()+"/"+cluster_str+"/volumes/");
            String volumes[] = cluster.list();
            if (volumes == null)
                continue;
            for (String volume_str : volumes)
            {
                File etagDir = new File(cluster.getAbsolutePath()+"/"+volume_str+"/etag/");
                File list[] = etagDir.listFiles();
                if (list == null)
                    continue;
                for (File e : list)
                    result.add(e);
            }
        }
        return result;
    }

    void removeEtags()
    {
        File dir = SxApp.sConfigDir;
        ArrayList<File> list = etags(dir);
        for (File f : list)
            f.delete();
    }


    public SxDatabaseHelper(Context context)
    {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db)
    {
        //CREATE TABLES
        db.execSQL(CREATE_TABLE_ACCOUNTS);
        db.execSQL(CREATE_TABLE_VOLUMES);
        db.execSQL(CREATE_TABLE_FILES);
        // CREATE TRIGGERS
        db.execSQL(CREATE_TRIGGER_UPDATE_FILE);
        db.execSQL(CREATE_TRIGGER_REMOVE_FILE);
        db.execSQL(CREATE_TRIGGER_INSERT_VOLUME);
        db.execSQL(CREATE_TRIGGER_UPDATE_VOLUME);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        if (oldVersion != DB_VERSION)
        {
            removeEtags();
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ACCOUNTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_VOLUMES);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_FILES);
            this.onCreate(db);
        }
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (Build.VERSION.SDK_INT >= 11)
            db.enableWriteAheadLogging();
        db.execSQL("PRAGMA synchronous=NORMAL");
        db.execSQL("PRAGMA recursive_triggers=ON");
    }

    synchronized public static SQLiteDatabase database()
    {
        if (sDatabase == null) {
            SxDatabaseHelper dbHelper = new SxDatabaseHelper(SxApp.sInstance);
            sDatabase = dbHelper.getWritableDatabase();
        }
        return sDatabase;
    }
}


