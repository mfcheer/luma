package com.jianping.tv.platform;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Lightweight native ContentProvider for sharing downloaded APK files with system installer.
 * Strictly limits file access to the app's internal cache update directory to prevent traversal.
 */
public final class StillFileProvider extends ContentProvider {
  public static final String AUTHORITY_SUFFIX = ".fileprovider";

  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    File file = getFileForUri(uri);
    if (file == null || !file.exists()) {
      throw new FileNotFoundException("File not found for uri: " + uri);
    }
    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
  }

  @Override
  public String getType(Uri uri) {
    return "application/vnd.android.package-archive";
  }

  @Override
  public Cursor query(
      Uri uri,
      String[] projection,
      String selection,
      String[] selectionArgs,
      String sortOrder) {
    File file = getFileForUri(uri);
    if (file == null || !file.exists()) return null;
    if (projection == null) {
      projection = new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
    }
    MatrixCursor cursor = new MatrixCursor(projection, 1);
    MatrixCursor.RowBuilder row = cursor.newRow();
    for (String col : projection) {
      if (OpenableColumns.DISPLAY_NAME.equals(col)) {
        row.add(file.getName());
      } else if (OpenableColumns.SIZE.equals(col)) {
        row.add(file.length());
      } else {
        row.add(null);
      }
    }
    return cursor;
  }

  private File getFileForUri(Uri uri) {
    Context context = getContext();
    if (context == null || uri == null) return null;
    File baseDir = getUpdateDir(context);
    return resolveFileForUri(baseDir, uri.getPath());
  }

  public static File resolveFileForUri(File baseDir, String uriPath) {
    if (baseDir == null || uriPath == null) return null;
    String fileName = new File(uriPath).getName();
    if (fileName.isEmpty() || fileName.equals(".") || fileName.equals("..")) {
      return null;
    }
    File target = new File(baseDir, fileName);
    try {
      String baseCanonical = baseDir.getCanonicalPath();
      String targetCanonical = target.getCanonicalPath();
      if (!targetCanonical.startsWith(baseCanonical + File.separator) && !targetCanonical.equals(baseCanonical)) {
        return null;
      }
    } catch (IOException e) {
      return null;
    }
    return target;
  }

  public static File getUpdateDir(Context context) {
    File dir = new File(context.getCacheDir(), "updates");
    if (!dir.exists()) {
      dir.mkdirs();
    }
    return dir;
  }

  public static Uri getUriForFile(Context context, File file) {
    return Uri.parse(
        "content://" + context.getPackageName() + AUTHORITY_SUFFIX + "/" + file.getName());
  }

  @Override
  public int delete(Uri uri, String s, String[] sa) {
    return 0;
  }

  @Override
  public Uri insert(Uri uri, ContentValues cv) {
    return null;
  }

  @Override
  public int update(Uri uri, ContentValues cv, String s, String[] sa) {
    return 0;
  }
}
