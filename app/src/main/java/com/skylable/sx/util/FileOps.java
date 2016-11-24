package com.skylable.sx.util;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Stack;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import com.skylable.sx.R;
import com.skylable.sx.app.SxApp;
import com.skylable.sx.providers.Contract;
import com.skylable.sx.ui.fragments.MessageFragment;
import com.skylable.sx.sxdrive2.SxFileInfo;


public class FileOps
{
    private static final String TAG = FileOps.class.getSimpleName();
    public static final String MIME_ANY = "*/*";

    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

    private static final int MAX_PATH = 256;
    private static final String INVALID_CHARS = "\"#$%&*+,/:;<=>?@[\\]^`{|}";

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    public interface ProgressListener
    {
        void onProgress(long size, long progress);
    }


    public static long copy(InputStream input, OutputStream output) throws IOException
    {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        long count = 0;
        int n = 0;
        while (-1 != (n = input.read(buffer)))
        {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    public static long copy(File _input, File _output, ProgressListener listener) throws IOException
    {
        long size = _input.length();
        InputStream input = new FileInputStream(_input);
        OutputStream output = new FileOutputStream(_output);

        long count = 0;
        try {
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int n;
            while (-1 != (n = input.read(buffer))) {
                output.write(buffer, 0, n);
                count += n;
                if (listener != null)
                    listener.onProgress(size, count);
            }
        }
        catch (Exception ex)
        {
            input.close();
            output.close();
            throw ex;
        }
        return count;
    }

    public static void closeQuietly(Closeable c)
    {
        try
        {
            if (c != null)
                c.close();
        } catch (IOException ignored)
        {}
    }

    public static String humanReadableByteCount(long bytes, boolean si)
    {
        int unit = si ? 1000 : 1024;
        if (bytes < unit)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    private static boolean recursiveDelete(File file)
    {
        if (!file.exists())
            return true;

        if (file.isDirectory())
        {
            for (File child: file.listFiles())
                if (!recursiveDelete(child))
                    return false;
        }
        return file.delete();
    }

    public static boolean recursiveDelete(String id)
    {
        return recursiveDelete(new File(SxApp.sFilesDir, id));
    }

    private static String basename(String name)
    {
        return name.substring(name.lastIndexOf('/') + 1);
    }

    private static byte replaceInvaldChar(byte ch)
    {
        if (INVALID_CHARS.indexOf(ch) != -1)
            return '_';
        return ch;
    }

    private static String sanitizeFilename(String name)
    {
        String encoded = basename(name);
        byte[] bytes = encoded.getBytes(UTF_8);

        for (int i = 0; i < bytes.length; i++)
            bytes[i] = replaceInvaldChar(bytes[i]);

        encoded = new String(bytes);

        if (encoded.length() <= MAX_PATH)
            return encoded;

        final int idx = name.lastIndexOf('.');
        final String ext = (idx == -1) ? "" : name.substring(idx);

        return Integer.toHexString(name.hashCode()) + ext;
    }

    public static File getLocalFile(String fullpath, String id, boolean create) throws IOException
    {
        File parent = new File(SxApp.sFilesDir, id);
        File file = new File(parent, sanitizeFilename(fullpath));

        if (create)
        {
            if (!parent.isDirectory())
            {
                if (!parent.mkdir())
                    throw new IOException("Unable to create parent directory " + parent.getAbsolutePath());
                parent.setExecutable(true, false);
            }
        }
        else if (!file.exists() || !file.isFile())
            throw new IOException("Unable to open filePath");

        return file;
    }

    public static boolean checkForModification(Context context, String id)
    {
        final Uri uri = Uri.withAppendedPath(Contract.CONTENT_URI_FILE, id);
        final Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);

        try
        {
            if (!cursor.moveToFirst())
                return false;

            final String fullpath = cursor.getString(cursor.getColumnIndex(Contract.COLUMN_FULLPATH));
            try
            {
                final File file = getLocalFile(fullpath, id, false);
                if (file.lastModified() != cursor.getLong(cursor.getColumnIndex(Contract.COLUMN_LOCALTIME)))
                    return true;
            } catch (IOException e)
            {
                e.printStackTrace();
                return false;
            }
        }
        finally
        {
            cursor.close();
        }
        return false;
    }

    public static boolean cleanCached(Cursor cursor)
    {
        if (!cursor.moveToFirst())
            return true;

        boolean result = true;

        final int idx_id = cursor.getColumnIndex(Contract.COLUMN_ID);
        do
        {
            result &= recursiveDelete(cursor.getString(idx_id));
        } while (cursor.moveToNext());

        Log.i(TAG, "Deleted " + cursor.getCount() + " files from filesystem");
        return result;
    }

    public static String guessContentTypeFromName(String url)
    {
        // guessContentTypeFromName() does a mess with url containings e.g. #
        final int idx = url.lastIndexOf('.');
        if (idx == -1)
            return null;
        return URLConnection.guessContentTypeFromName(url.substring(idx));
    }

    public static void forget(ContentResolver cr, String id)
    {
        FileOps.recursiveDelete(id);
        final Uri.Builder builder = Uri.withAppendedPath(Contract.CONTENT_URI_FILE, id).buildUpon();
        final Uri uri = builder.appendQueryParameter(Contract.PARAM_NOTIFY, Contract.TRUE).build(); // to reflect update "cached" value
        final ContentValues values = new ContentValues(1);
        values.put(Contract.COLUMN_CACHED, Contract.FALSE);
        cr.update(uri, values, null, null);
    }

    public static void forget(Context context, String id)
    {
        forget(context.getContentResolver(), id);
    }

    private static boolean thereIsEnoughSpace(long size)
    {
        return (Environment.getExternalStorageDirectory().getUsableSpace() > size);
    }

    public static boolean reclaimSpace(Context context, long size)
    {
        if (thereIsEnoughSpace(size))
            return true;

        final String[] projection = new String[] { Contract.COLUMN_ID, Contract.COLUMN_LASTCHECK };
        final String selection = Contract.COLUMN_STARRED + "=? AND " + Contract.COLUMN_CACHED + "=?";
        final String[] selectionArgs = new String[] { Contract.FALSE, Contract.TRUE };

        final ContentResolver cr = context.getContentResolver();
        final Cursor cursor = cr.query(Contract.CONTENT_URI_FILE, projection, selection, selectionArgs, Contract.COLUMN_LASTCHECK);
        try
        {
            if (!cursor.moveToFirst())
                return false;

            final int idx_id = cursor.getColumnIndex(Contract.COLUMN_ID);

            do
            {
                forget(cr, cursor.getString(idx_id));
                if (thereIsEnoughSpace(size))
                    return true;
            } while (cursor.moveToNext());
        }
        finally
        {
            cursor.close();
        }
        return thereIsEnoughSpace(size);
    }

    public static boolean removeDirectory(String dirname)
    {
        File rootDir = new File(dirname);
        if (!rootDir.isDirectory())
            return false;

        Stack<File> directories = new Stack<File>();
        Boolean failed = false;
        if (rootDir.isDirectory())
            directories.push(rootDir);
        while (!directories.empty())
        {
            File f = directories.peek();

            File fileList[] = f.listFiles();
            if (fileList == null)
            {
                failed = true;
                break;
            }
            if (fileList.length == 0)
            {
                if (!f.delete())
                {
                    failed = true;
                    break;
                }
                directories.remove(f);
                continue;
            }
            for (File a : fileList)
            {
                if (a.isDirectory())
                    directories.push(a);
                else {
                    if (!a.delete())
                    {
                        failed = true;
                        break;
                    }
                }
            }
            if (failed)
                return false;
        }
        return true;
    }

    public static void shareFile(long id, Context context)
    {
        _openFile(id, context, Intent.ACTION_SEND, null);
    }

    public static void openFile(long id, Context context)
    {
        _openFile(id, context, Intent.ACTION_VIEW, null);
    }

    public static void openFileWith(long id, Context context)
    {
        _openFile(id, context, Intent.ACTION_VIEW,"*/*");
    }

    private static void _openFile(long id, Context context, String action, String mime)
    {
        SxFileInfo fileInfo;
        try {
            fileInfo = new SxFileInfo(id);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            return;
        }

        if (mime == null)
            mime = FileOps.guessContentTypeFromName(fileInfo.filename());
        Intent intent = new Intent(action);

        Uri uri = Uri.fromFile(new File(fileInfo.localPath()));

        if (TextUtils.equals(action,Intent.ACTION_VIEW))
            intent.setDataAndType(uri, mime);
        else if (TextUtils.equals(action,Intent.ACTION_SEND))
            intent.setType(mime).putExtra(Intent.EXTRA_STREAM, uri);

        Log.e("FileOps", "Opening: " + uri.toString());

        try
        {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e)
        {
            Log.e("OPEN FILE", "no application");
            MessageFragment.showMessage(R.string.noApplications);
        } catch (SecurityException e)
        {
            Log.e("OPEN FILE", "application failed");
            MessageFragment.showMessage(R.string.on_security_exception);
        } catch (Exception e)
        {
            Log.e("OPEN FILE", "error: "+e.getMessage());
            MessageFragment.showMessage(e.getMessage());
        }
    }

    public static void sharePublicLink(String link, Context context)
    {
        String action = Intent.ACTION_SEND;
        Intent intent = new Intent(action);

        intent.setType("text/plain").putExtra(Intent.EXTRA_TEXT, link);
        try
        {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e)
        {
            Log.e("OPEN FILE", "no application");
            MessageFragment.showMessage(R.string.noApplications);
        } catch (SecurityException e)
        {
            Log.e("OPEN FILE", "application failed");
            MessageFragment.showMessage(R.string.on_security_exception);
        } catch (Exception e)
        {
            Log.e("OPEN FILE", "error: "+e.getMessage());
            MessageFragment.showMessage(e.getMessage());
        }
    }

    static class SxMediaScannerClient implements MediaScannerConnection.MediaScannerConnectionClient
    {
        String mFile = null;
        MediaScannerConnection mMediaScannerConnection = null;

        public SxMediaScannerClient(String file)
        {
            mFile = file;
        }

        @Override
        public void onMediaScannerConnected() {
            if (mMediaScannerConnection != null && mFile != null)
            {
                mMediaScannerConnection.scanFile(mFile, null);
            }
            else
                mMediaScannerConnection.disconnect();
        }

        @Override
        public void onScanCompleted(String path, Uri uri) {
            mMediaScannerConnection.disconnect();
        }

        void setConnection(MediaScannerConnection connection)
        {
            mMediaScannerConnection = connection;
        }
    }

    static public void exportFile(File src, File dest, ProgressListener progressListener)
    {
        try {
            copy(src, dest, progressListener);

            SxMediaScannerClient client = new SxMediaScannerClient(dest.getAbsolutePath());
            MediaScannerConnection scannerConnection = new MediaScannerConnection(SxApp.sInstance, client);
            client.setConnection(scannerConnection);
            scannerConnection.connect();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    public static String filenameFromUri(Uri uri, ContentResolver cr)
    {
        final String scheme = uri.getScheme();
        String filename = uri.getLastPathSegment();

        if (TextUtils.equals("content",scheme))
        {
            Cursor cursor = null;
            try
            {
                cursor = cr.query(uri, null, null, null, null);
                if (cursor.moveToFirst())
                {
                    final int idx_filename = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                    if (idx_filename != -1)
                        filename = cursor.getString(idx_filename);
                }
            } catch (SecurityException e)
            {
                return null;
            }
            finally
            {
                if (cursor != null)
                    cursor.close();
            }
        }
        return filename;
    }

    public static String prepareFileToUpload(Uri uri, long id, Context context)  {
        final ContentResolver cr = context.getContentResolver();
        String filename = filenameFromUri(uri, cr);

        File sxtemp;
        sxtemp = new File(SxApp.sCacheDir+"/"+id+"/"+filename);

        File parent = sxtemp.getParentFile();
        if (!parent.exists())
            parent.mkdirs();
        sxtemp.setReadable(true, false);

        try {
            sxtemp.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        BufferedOutputStream os = null;
        long size = 0;
        try
        {
            os = new BufferedOutputStream(new FileOutputStream(sxtemp));
            size = FileOps.copy(cr.openInputStream(uri), os);
        } catch (IOException e)
        {
            e.printStackTrace();
            sxtemp.delete();
            FileOps.closeQuietly(os);
            return null;
        }
        FileOps.closeQuietly(os);
        return sxtemp.getAbsolutePath();
    }
}
