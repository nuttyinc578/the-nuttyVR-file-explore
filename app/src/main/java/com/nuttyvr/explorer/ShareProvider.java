package com.nuttyvr.explorer;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import java.io.*;

/** Read-only, temporary URI grants for opening files in the headset's installed apps. */
public final class ShareProvider extends ContentProvider {
    static Uri uri(File file) {
        return new Uri.Builder().scheme("content").authority("com.nuttyvr.explorer.files")
                .appendPath("shared").appendPath(file.getAbsolutePath()).build();
    }
    static Uri apkUri(File file) {
        return new Uri.Builder().scheme("content").authority("com.nuttyvr.explorer.files")
                .appendPath("apk-installs").appendPath(file.getName()).build();
    }
    private File file(Uri uri) throws FileNotFoundException {
        if (!"com.nuttyvr.explorer.files".equals(uri.getAuthority()) || uri.getPathSegments().size() != 2) throw new FileNotFoundException();
        try {
            if ("apk-installs".equals(uri.getPathSegments().get(0))) {
                return ApkFiles.resolve(new File(getContext().getCacheDir(), "apk-installs"), uri.getPathSegments().get(1));
            }
            if (!"shared".equals(uri.getPathSegments().get(0))) throw new FileNotFoundException();
            File f = new File(uri.getPathSegments().get(1)).getCanonicalFile();
            // Only shared storage and this app's external workspace are exposed, never internal app data.
            File external = Environment.getExternalStorageDirectory().getCanonicalFile();
            if (!LocalFiles.inside(f, external) || !f.isFile()) throw new FileNotFoundException("File is unavailable");
            return f;
        } catch (IOException e) { throw new FileNotFoundException("File is unavailable"); }
    }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) {
        try { return Storage.mime(file(uri).getName()); } catch (FileNotFoundException e) { return "application/octet-stream"; }
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only access");
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            File f = file(uri);
            String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor c = new MatrixCursor(columns); Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = f.getName();
                else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = f.length();
            }
            c.addRow(row); return c;
        } catch (FileNotFoundException e) { return null; }
    }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
