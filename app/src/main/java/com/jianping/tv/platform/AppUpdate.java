package com.jianping.tv.platform;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Update manifest model, security verifications (HTTPS, SHA-256, package name, signatures,
 * minSdk system compatibility), and storage safety rules for Still TV launcher.
 */
public final class AppUpdate {
  public static final String DEFAULT_UPDATE_URL =
      "https://mfcheer.github.io/luma/downloads/update.json";
  public static final String DEFAULT_MIRROR_URL =
      "https://mfcheer.github.io/luma/update.json";
  public static final long MAX_APK_SIZE = 50 * 1024 * 1024L; // 50 MB
  public static final long SAFETY_MARGIN_BYTES = 10 * 1024 * 1024L; // 10 MB
  public static final int CONNECT_TIMEOUT_MS = 15000;
  public static final int READ_TIMEOUT_MS = 30000;

  private AppUpdate() {}

  public static final class UpdateInfo {
    public final int versionCode;
    public final String versionName;
    public final String downloadUrl;
    public final String sha256;
    public final long size;
    public final String releaseNotes;
    public final int minSdk;

    public UpdateInfo(
        int versionCode,
        String versionName,
        String downloadUrl,
        String sha256,
        long size,
        String releaseNotes,
        int minSdk) {
      if (versionCode <= 0) {
        throw new IllegalArgumentException("Invalid versionCode: " + versionCode);
      }
      if (versionName == null || !versionName.matches("^[0-9a-zA-Z._-]+$")) {
        throw new IllegalArgumentException("Invalid or unsafe versionName: " + versionName);
      }
      if (!isHttpsUrl(downloadUrl)) {
        throw new IllegalArgumentException("Only HTTPS download URLs are permitted: " + downloadUrl);
      }
      if (sha256 == null || !sha256.matches("^[a-fA-F0-9]{64}$")) {
        throw new IllegalArgumentException("Invalid SHA-256 hash");
      }
      if (size < 0 || size > MAX_APK_SIZE) {
        throw new IllegalArgumentException("Invalid size: " + size);
      }
      if (minSdk < 1 || minSdk > 100) {
        throw new IllegalArgumentException("Invalid minSdk: " + minSdk);
      }
      this.versionCode = versionCode;
      this.versionName = versionName;
      this.downloadUrl = downloadUrl.trim();
      this.sha256 = sha256.trim().toLowerCase(Locale.US);
      this.size = size;
      this.releaseNotes = releaseNotes != null ? releaseNotes : "";
      this.minSdk = minSdk;
    }

    private static int parseStrictInt(
        JSONObject obj, String key, boolean required, int defaultValue, int min, int max) {
      if (!obj.has(key)) {
        if (required) {
          throw new IllegalArgumentException("Missing required field: " + key);
        }
        return defaultValue;
      }
      Object val;
      try {
        val = obj.get(key);
      } catch (JSONException e) {
        throw new IllegalArgumentException("Cannot read field " + key + ": " + e.getMessage(), e);
      }
      if (val == null || val == JSONObject.NULL) {
        throw new IllegalArgumentException("Field " + key + " cannot be null");
      }
      if (!(val instanceof Number)
          || val instanceof Double
          || val instanceof Float
          || val instanceof java.math.BigDecimal) {
        throw new IllegalArgumentException(
            "Field " + key + " must be an integer, got: " + val.getClass().getSimpleName());
      }
      Number num = (Number) val;
      double d = num.doubleValue();
      if (d != Math.floor(d) || Double.isInfinite(d) || Double.isNaN(d)) {
        throw new IllegalArgumentException(
            "Field " + key + " must be an integer without decimal fraction, got: " + val);
      }
      if (num instanceof java.math.BigInteger) {
        java.math.BigInteger bi = (java.math.BigInteger) num;
        if (bi.compareTo(java.math.BigInteger.valueOf(max)) > 0
            || bi.compareTo(java.math.BigInteger.valueOf(min)) < 0) {
          throw new IllegalArgumentException(
              "Field " + key + " out of range [" + min + ", " + max + "]: " + bi);
        }
      }
      long l = num.longValue();
      if (l < min || l > max) {
        throw new IllegalArgumentException(
            "Field " + key + " out of range [" + min + ", " + max + "]: " + l);
      }
      return (int) l;
    }

    private static long parseStrictLong(
        JSONObject obj, String key, boolean required, long defaultValue, long min, long max) {
      if (!obj.has(key)) {
        if (required) {
          throw new IllegalArgumentException("Missing required field: " + key);
        }
        return defaultValue;
      }
      Object val;
      try {
        val = obj.get(key);
      } catch (JSONException e) {
        throw new IllegalArgumentException("Cannot read field " + key + ": " + e.getMessage(), e);
      }
      if (val == null || val == JSONObject.NULL) {
        throw new IllegalArgumentException("Field " + key + " cannot be null");
      }
      if (!(val instanceof Number)
          || val instanceof Double
          || val instanceof Float
          || val instanceof java.math.BigDecimal) {
        throw new IllegalArgumentException(
            "Field " + key + " must be an integer, got: " + val.getClass().getSimpleName());
      }
      Number num = (Number) val;
      double d = num.doubleValue();
      if (d != Math.floor(d) || Double.isInfinite(d) || Double.isNaN(d)) {
        throw new IllegalArgumentException(
            "Field " + key + " must be an integer without decimal fraction, got: " + val);
      }
      if (num instanceof java.math.BigInteger) {
        java.math.BigInteger bi = (java.math.BigInteger) num;
        if (bi.compareTo(java.math.BigInteger.valueOf(max)) > 0
            || bi.compareTo(java.math.BigInteger.valueOf(min)) < 0) {
          throw new IllegalArgumentException(
              "Field " + key + " out of range [" + min + ", " + max + "]: " + bi);
        }
      }
      long l = num.longValue();
      if (l < min || l > max) {
        throw new IllegalArgumentException(
            "Field " + key + " out of range [" + min + ", " + max + "]: " + l);
      }
      return l;
    }

    private static String parseStrictString(
        JSONObject obj, String key, boolean required, String defaultValue) {
      if (!obj.has(key)) {
        if (required) {
          throw new IllegalArgumentException("Missing required field: " + key);
        }
        return defaultValue;
      }
      Object val;
      try {
        val = obj.get(key);
      } catch (JSONException e) {
        throw new IllegalArgumentException("Cannot read field " + key + ": " + e.getMessage(), e);
      }
      if (val == null || val == JSONObject.NULL) {
        throw new IllegalArgumentException("Field " + key + " cannot be null");
      }
      if (!(val instanceof String)) {
        throw new IllegalArgumentException(
            "Field " + key + " must be a string, got: " + val.getClass().getSimpleName());
      }
      return (String) val;
    }

    public static UpdateInfo fromJson(String json) {
      if (json == null) {
        throw new IllegalArgumentException("JSON string cannot be null");
      }
      try {
        JSONObject obj = new JSONObject(json);
        int code =
            parseStrictInt(obj, "versionCode", true, 0, 1, Integer.MAX_VALUE);
        String name = parseStrictString(obj, "versionName", true, "");
        String url = parseStrictString(obj, "downloadUrl", true, "");
        String hash = parseStrictString(obj, "sha256", true, "");
        long sz = parseStrictLong(obj, "size", false, 0L, 0L, MAX_APK_SIZE);
        String notes = parseStrictString(obj, "releaseNotes", false, "");
        int minSdk = parseStrictInt(obj, "minSdk", false, 23, 1, 100);
        return new UpdateInfo(code, name, url, hash, sz, notes, minSdk);
      } catch (JSONException e) {
        throw new IllegalArgumentException("Invalid update JSON: " + e.getMessage(), e);
      }
    }

    public String toJson() {
      try {
        JSONObject obj = new JSONObject();
        obj.put("versionCode", versionCode);
        obj.put("versionName", versionName);
        obj.put("downloadUrl", downloadUrl);
        obj.put("sha256", sha256);
        obj.put("size", size);
        obj.put("releaseNotes", releaseNotes);
        obj.put("minSdk", minSdk);
        return obj.toString(2);
      } catch (JSONException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static final class VerificationResult {
    public final boolean success;
    public final String message;

    private VerificationResult(boolean success, String message) {
      this.success = success;
      this.message = message;
    }

    public static VerificationResult success() {
      return new VerificationResult(true, "");
    }

    public static VerificationResult failure(String message) {
      return new VerificationResult(false, message);
    }
  }

  public static boolean isHttpsUrl(String url) {
    return url != null && url.trim().toLowerCase(Locale.US).startsWith("https://");
  }

  public static boolean isSystemCompatible(int currentSdk, int minSdk) {
    return currentSdk >= minSdk;
  }

  public static boolean isSystemCompatible(int minSdk) {
    return isSystemCompatible(Build.VERSION.SDK_INT, minSdk);
  }

  public static String getUpdateApkFileName(int versionCode) {
    return "still_update_" + versionCode + ".apk";
  }

  public static String getUpdateTmpFileName(int versionCode) {
    return "still_update_" + versionCode + ".tmp";
  }

  public static boolean hasEnoughSpace(File dir, long requiredBytes) {
    if (dir == null) return false;
    long usable = dir.getUsableSpace();
    long needed = Math.max(1024 * 1024L, requiredBytes) + SAFETY_MARGIN_BYTES;
    return usable >= needed;
  }

  public static String calculateSha256(File file) throws IOException {
    if (file == null || !file.exists()) {
      throw new IOException("File does not exist");
    }
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      try (InputStream is = new FileInputStream(file)) {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = is.read(buffer)) != -1) {
          md.update(buffer, 0, read);
        }
      }
      byte[] digest = md.digest();
      StringBuilder sb = new StringBuilder(64);
      for (byte b : digest) {
        sb.append(String.format(Locale.US, "%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public static byte[] getSigningCertDigest(PackageInfo packageInfo) {
    if (packageInfo == null) return null;
    Signature[] signatures = null;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      if (packageInfo.signingInfo != null) {
        signatures = packageInfo.signingInfo.getApkContentsSigners();
      }
    }
    if (signatures == null || signatures.length == 0) {
      @SuppressWarnings("deprecation")
      Signature[] legacy = packageInfo.signatures;
      signatures = legacy;
    }
    if (signatures == null || signatures.length == 0) return null;
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return md.digest(signatures[0].toByteArray());
    } catch (NoSuchAlgorithmException e) {
      return null;
    }
  }

  public static VerificationResult verifyApk(
      Context context, File apkFile, UpdateInfo info) {
    if (apkFile == null || !apkFile.exists() || apkFile.length() == 0) {
      return VerificationResult.failure("安装包文件不存在或为空");
    }
    if (apkFile.length() > MAX_APK_SIZE) {
      return VerificationResult.failure("安装包体积超出安全限制");
    }
    if (info.size > 0 && apkFile.length() != info.size) {
      return VerificationResult.failure(
          "安装包大小与清单不符 (实际: " + apkFile.length() + " 字节, 预期: " + info.size + " 字节)");
    }

    // 0. Verify system compatibility (minSdk)
    if (!isSystemCompatible(info.minSdk)) {
      return VerificationResult.failure(
          "系统版本低于更新要求的最低版本 (API " + info.minSdk + ", 当前设备: API " + Build.VERSION.SDK_INT + ")");
    }

    // 1. Verify SHA-256 file checksum
    try {
      String actualHash = calculateSha256(apkFile);
      if (!actualHash.equalsIgnoreCase(info.sha256)) {
        return VerificationResult.failure("安装包 SHA-256 校验失败，文件已损坏或被修改");
      }
    } catch (IOException e) {
      return VerificationResult.failure("读取安装包校验和失败: " + e.getMessage());
    }

    // 2. Parse package archive with PackageManager
    PackageManager pm = context.getPackageManager();
    int flags =
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            ? (PackageManager.GET_SIGNING_CERTIFICATES | PackageManager.GET_SIGNATURES)
            : PackageManager.GET_SIGNATURES;
    PackageInfo apkInfo = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), flags);
    if (apkInfo == null) {
      @SuppressWarnings("deprecation")
      PackageInfo fallback = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), PackageManager.GET_SIGNATURES);
      apkInfo = fallback;
    }
    if (apkInfo == null) {
      return VerificationResult.failure("无法解析安装包结构，文件损坏或并非合法 APK");
    }

    // Check minSdk embedded inside package if accessible on API 24+
    if (Build.VERSION.SDK_INT >= 24 && apkInfo.applicationInfo != null) {
      if (Build.VERSION.SDK_INT < apkInfo.applicationInfo.minSdkVersion) {
        return VerificationResult.failure(
            "当前系统版本 (API "
                + Build.VERSION.SDK_INT
                + ") 低于安装包要求的最低版本 (API "
                + apkInfo.applicationInfo.minSdkVersion
                + ")");
      }
    }

    // 3. Verify package name
    if (!context.getPackageName().equals(apkInfo.packageName)) {
      return VerificationResult.failure(
          "安装包应用包名不匹配（当前: "
              + context.getPackageName()
              + ", 安装包: "
              + apkInfo.packageName
              + "）");
    }

    // 4. Verify version code
    if (apkInfo.versionCode != info.versionCode) {
      return VerificationResult.failure(
          "安装包版本代号与清单不符（清单: "
              + info.versionCode
              + ", 安装包: "
              + apkInfo.versionCode
              + "）");
    }
    int currentCode = DeviceDiagnostics.appVersionCode(context);
    if (currentCode > 0 && apkInfo.versionCode < currentCode) {
      return VerificationResult.failure(
          "安装包版本低于当前已安装版本（当前: "
              + currentCode
              + ", 安装包: "
              + apkInfo.versionCode
              + "），不支持降级安装");
    }

    // 5. Verify certificate digest continuity
    try {
      PackageInfo currentInfo;
      try {
        currentInfo = pm.getPackageInfo(context.getPackageName(), flags);
      } catch (Exception e) {
        @SuppressWarnings("deprecation")
        PackageInfo legacy =
            pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
        currentInfo = legacy;
      }
      byte[] currentCert = getSigningCertDigest(currentInfo);
      byte[] apkCert = getSigningCertDigest(apkInfo);
      if (currentCert == null || apkCert == null) {
        return VerificationResult.failure("无法提取应用签名证书");
      }
      if (!Arrays.equals(currentCert, apkCert)) {
        return VerificationResult.failure("安装包数字签名与当前应用不一致，无法覆盖升级");
      }
    } catch (Exception e) {
      return VerificationResult.failure("核验应用签名失败: " + e.getMessage());
    }

    return VerificationResult.success();
  }

  public static String formatSize(long bytes) {
    if (bytes <= 0) return "未知大小";
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) {
      return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
    }
    return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
  }
}
