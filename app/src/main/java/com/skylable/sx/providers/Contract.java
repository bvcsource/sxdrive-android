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

import android.net.Uri;

public final class Contract
{
    public static final String AUTHORITY = "com.skylable.sx.provider";

    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/");
    public static final Uri CONTENT_URI_FILE = Uri.parse("content://" + AUTHORITY + "/filePath");
    public static final Uri CONTENT_URI_DIR = Uri.parse("content://" + AUTHORITY + "/dir");
    public static final Uri CONTENT_URI_PRUNE = Uri.parse("content://" + AUTHORITY + "/prune");
    public static final Uri CONTENT_URI_CACHED = Uri.parse("content://" + AUTHORITY + "/cached");
    public static final Uri CONTENT_URI_ACCOUNT = Uri.parse("content://" + AUTHORITY + "/account");
    public static final Uri CONTENT_URI_VOLUME = Uri.parse("content://" + AUTHORITY + "/volume");


    public static final String ID_ROOT_DIR = "0";
    public static final String URI_ROOT_DIR = "/";

    public static final String PARAM_NOTIFY = "notify";
    public static final String PARAM_RECURSE = "recurse";

    public static final String TRUE = String.valueOf(1);
    public static final String FALSE = String.valueOf(0);

    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_ACCOUNT = "account";
    public static final String COLUMN_ACCOUNT_ID = "account_id";
    public static final String COLUMN_FULLPATH = "fullpath";
    public static final String COLUMN_FILENAME = "filename";
    public static final String COLUMN_ISDIR = "isdir";
    public static final String COLUMN_COUNT = "count";
    public static final String COLUMN_LASTCHECK = "lastcheck";
    public static final String COLUMN_REMOTETIME = "remotetime";
    public static final String COLUMN_LOCALTIME = "localtime";
    public static final String COLUMN_SIZE = "size";
    public static final String COLUMN_USED = "used";
    public static final String COLUMN_CACHED = "cached";
    public static final String COLUMN_ENCRYPTED = "encrypted";
    public static final String COLUMN_AES_FINGERPRINT = "aes_fingerprint";
    public static final String COLUMN_STARRED = "starred";
    public static final String COLUMN_VOLUME = "volume";
    public static final String COLUMN_VOLUME_ID = "volume_id";
    public static final String COLUMN_LASTSYNC = "lastsync";


    public static final String COLUMN_FILE_ID = "file_id";
    public static final String COLUMN_REMOTE_PATH = "remote_path";
    public static final String COLUMN_REMOTE_REV = "remote_rev";
    public static final String COLUMN_LOCAL_PATH = "local_path";
    public static final String COLUMN_LOCAL_REV = "local_rev";
    public static final String COLUMN_PARENT_ID = "parent_id";

    public static final int ID_OP_NONE = 0;
    public static final int ID_OP_DELETE_LOCAL = 1;
    public static final int ID_OP_DELETE_REMOTE = 2;

    public static final String OP_NONE = String.valueOf(ID_OP_NONE);
    public static final String OP_DELETE_LOCAL = String.valueOf(ID_OP_DELETE_LOCAL);
    public static final String OP_DELETE_REMOTE = String.valueOf(ID_OP_DELETE_REMOTE);

    public static final String EXTRA_ACCOUNT = "sx.account";
    public static final String EXTRA_URI = "sx.uri";
    public static final String EXTRA_ID = "sx.id";
}
