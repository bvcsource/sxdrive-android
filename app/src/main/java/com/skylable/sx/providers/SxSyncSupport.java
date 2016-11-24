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

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import com.skylable.sx.jni.SXClient;
import com.skylable.sx.jni.SXDirEntry;
import com.skylable.sx.jni.SXNativeException;
import com.skylable.sx.jni.SXUri;

public class SxSyncSupport
{
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_PATH = "path";
    public static final String COLUMN_ISDIR = "isdir";
    public static final String COLUMN_TIME = "time";
    public static final String COLUMN_SIZE = "size";

    public static final String SORT_COLLATE = " COLLATE BINARY ASC";

    private static final int MAX_INMEM = 1000;
    private static final String DB_TEMP = "temp";
    private static final String TABLE_TEMP = "temp";
    private static final String TABLE_CREATE = "CREATE TABLE " + TABLE_TEMP + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY, " +
                    COLUMN_PATH + " TEXT NOT NULL, " +
                    COLUMN_ISDIR + " INTEGER NOT NULL, " +
                    COLUMN_TIME + " INTEGER NOT NULL, " +
                    COLUMN_SIZE + " INTEGER NOT NULL DEFAULT 0, " +
                    "UNIQUE (" + COLUMN_PATH+", "+ COLUMN_ISDIR + ") ON CONFLICT REPLACE);";

    private static final String SQL_INSERT = "INSERT INTO " + TABLE_TEMP + " (" +
                    COLUMN_PATH + ", " + COLUMN_ISDIR + ", " + COLUMN_TIME + ", " + COLUMN_SIZE + ") " +
                    "VALUES (?, ?, ?, ?)";

}
