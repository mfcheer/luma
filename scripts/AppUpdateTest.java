import com.jianping.tv.platform.AppUpdate;
import com.jianping.tv.platform.StillFileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Standalone automated test suite for Still TV online updates:
 * - Real JSON parser verification (valid, escaped quotes/newlines, malformed, missing fields, type errors)
 * - JSON entry-point strict validation (versionCode overflow/decimal/string/null/boolean, size/minSdk type coercion prevention)
 * - Numeric boundaries & safety (versionCode <= 0, size < 0 or > 50MB, minSdk boundaries)
 * - versionName character safety (rejects traversal, slashes, spaces)
 * - Strict HTTPS enforcement
 * - System minSdk compatibility
 * - SHA-256 calculation and tampering detection
 * - Storage space threshold checks
 * - StillFileProvider path boundary & directory traversal prevention
 */
public final class AppUpdateTest {
  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  public static void main(String[] args) throws Exception {
    testValidManifestWithEscapes();
    testMalformedJson();
    testJsonEntrypointValidation();
    testNumericBoundariesAndInvalidSize();
    testVersionNameSafety();
    testHttpsStrictEnforcement();
    testSystemCompatibilityMinSdk();
    testCacheFileNameGeneration();
    testSha256Checksum();
    testStorageRequirement();
    testStillFileProviderPathBoundaries();
    System.out.println(
        "PASS: valid JSON with escapes, malformed/boundary JSON rejection, JSON entry-point strict type/range checks, size/version/minSdk boundaries, strict HTTPS, SHA-256 verification, and FileProvider path security verified.");
  }

  private static void testValidManifestWithEscapes() {
    String json =
        "{\n"
            + "  \"versionCode\": 26,\n"
            + "  \"versionName\": \"0.9.8\",\n"
            + "  \"downloadUrl\": \"https://mfcheer.github.io/luma/downloads/Still-0.9.8.apk\",\n"
            + "  \"sha256\": \"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\",\n"
            + "  \"size\": 643966,\n"
            + "  \"releaseNotes\": \"1. \\\"新增在线更新\\\" 与进度显示\\n2. 修复遥控器焦点\\r\\n3. 特殊符号: \\\\ & < >\",\n"
            + "  \"minSdk\": 23\n"
            + "}";

    AppUpdate.UpdateInfo info = AppUpdate.UpdateInfo.fromJson(json);
    check(info.versionCode == 26, "versionCode must be 26");
    check("0.9.8".equals(info.versionName), "versionName must be 0.9.8");
    check(
        info.downloadUrl.equals("https://mfcheer.github.io/luma/downloads/Still-0.9.8.apk"),
        "downloadUrl must match");
    check(
        info.sha256.equals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
        "sha256 must match");
    check(info.size == 643966, "size must match");
    check(info.releaseNotes.contains("\"新增在线更新\""), "releaseNotes must preserve escaped quotes");
    check(info.releaseNotes.contains("\n2. 修复遥控器焦点"), "releaseNotes must preserve newlines");
    check(info.minSdk == 23, "minSdk must be 23");

    // Roundtrip test with real JSON serializer
    String generatedJson = info.toJson();
    AppUpdate.UpdateInfo roundtrip = AppUpdate.UpdateInfo.fromJson(generatedJson);
    check(roundtrip.versionCode == info.versionCode, "roundtrip versionCode match");
    check(roundtrip.downloadUrl.equals(info.downloadUrl), "roundtrip downloadUrl match");
    check(roundtrip.releaseNotes.equals(info.releaseNotes), "roundtrip releaseNotes match");
  }

  private static void testMalformedJson() {
    // 1. Unclosed braces
    expectIllegalArgument(() -> AppUpdate.UpdateInfo.fromJson("{\"versionCode\": 26, \"versionName\": \"0.9.8\""), "Unclosed JSON");
    // 2. Syntax error
    expectIllegalArgument(() -> AppUpdate.UpdateInfo.fromJson("{\"versionCode\": , \"versionName\": \"0.9.8\"}"), "Malformed syntax");
    // 3. Null or empty
    expectIllegalArgument(() -> AppUpdate.UpdateInfo.fromJson(null), "Null JSON");
    expectIllegalArgument(() -> AppUpdate.UpdateInfo.fromJson(""), "Empty JSON");
    expectIllegalArgument(() -> AppUpdate.UpdateInfo.fromJson("   "), "Whitespace JSON");
    // 4. Missing required keys
    expectIllegalArgument(() -> AppUpdate.UpdateInfo.fromJson("{\"versionName\":\"0.9.8\",\"downloadUrl\":\"https://a.com/a.apk\",\"sha256\":\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"}"), "Missing versionCode");
    expectIllegalArgument(() -> AppUpdate.UpdateInfo.fromJson("{\"versionCode\":26,\"downloadUrl\":\"https://a.com/a.apk\",\"sha256\":\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"}"), "Missing versionName");
    expectIllegalArgument(() -> AppUpdate.UpdateInfo.fromJson("{\"versionCode\":26,\"versionName\":\"0.9.8\",\"sha256\":\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"}"), "Missing downloadUrl");
    expectIllegalArgument(() -> AppUpdate.UpdateInfo.fromJson("{\"versionCode\":26,\"versionName\":\"0.9.8\",\"downloadUrl\":\"https://a.com/a.apk\"}"), "Missing sha256");
    // 5. Type mismatch
    expectIllegalArgument(() -> AppUpdate.UpdateInfo.fromJson("{\"versionCode\":\"not-a-number\",\"versionName\":\"0.9.8\",\"downloadUrl\":\"https://a.com/a.apk\",\"sha256\":\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"}"), "String versionCode");
  }

  private static void testJsonEntrypointValidation() {
    String validBase =
        "\"versionCode\": 26, "
            + "\"versionName\": \"0.9.8\", "
            + "\"downloadUrl\": \"https://mfcheer.github.io/luma/downloads/Still-0.9.8.apk\", "
            + "\"sha256\": \"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"";

    // 1. Exact reproduction cases from QA feedback
    // versionCode=4294967322 (overflows 32-bit int, must be rejected, not truncated to 26)
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase.replace("\"versionCode\": 26", "\"versionCode\": 4294967322") + "}"),
        "fromJson versionCode=4294967322 overflow");

    // versionCode=26.9 (decimal, must be rejected, not truncated to 26)
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase.replace("\"versionCode\": 26", "\"versionCode\": 26.9") + "}"),
        "fromJson versionCode=26.9 decimal");

    // versionCode as string "26"
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase.replace("\"versionCode\": 26", "\"versionCode\": \"26\"") + "}"),
        "fromJson versionCode as string");

    // versionCode as boolean true
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase.replace("\"versionCode\": 26", "\"versionCode\": true") + "}"),
        "fromJson versionCode boolean");

    // versionCode as null
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase.replace("\"versionCode\": 26", "\"versionCode\": null") + "}"),
        "fromJson versionCode null");

    // versionCode = 0
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase.replace("\"versionCode\": 26", "\"versionCode\": 0") + "}"),
        "fromJson versionCode=0");

    // versionCode = -1
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase.replace("\"versionCode\": 26", "\"versionCode\": -1") + "}"),
        "fromJson versionCode=-1");

    // versionCode = 2147483648 (Integer.MAX_VALUE + 1)
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase.replace("\"versionCode\": 26", "\"versionCode\": 2147483648") + "}"),
        "fromJson versionCode=2147483648 (Integer.MAX_VALUE + 1)");

    // versionCode = 2147483647 (Integer.MAX_VALUE boundary - must succeed)
    AppUpdate.UpdateInfo maxCode =
        AppUpdate.UpdateInfo.fromJson("{" + validBase.replace("\"versionCode\": 26", "\"versionCode\": 2147483647") + "}");
    check(maxCode.versionCode == Integer.MAX_VALUE, "versionCode Integer.MAX_VALUE accepted");

    // 2. size validation
    // size="oops" (must be rejected, not fallback to 0)
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase + ", \"size\": \"oops\"}"),
        "fromJson size=\"oops\"");

    // size=true
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase + ", \"size\": true}"),
        "fromJson size=true");

    // size=null
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase + ", \"size\": null}"),
        "fromJson size=null");

    // size=26.9 decimal
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase + ", \"size\": 26.9}"),
        "fromJson size=26.9 decimal");

    // size=-1 negative
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase + ", \"size\": -1}"),
        "fromJson size=-1");

    // size=52428801 (> 50MB)
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase + ", \"size\": 52428801}"),
        "fromJson size=52428801 (> 50MB)");

    // size=0 boundary
    AppUpdate.UpdateInfo sizeZero =
        AppUpdate.UpdateInfo.fromJson("{" + validBase + ", \"size\": 0}");
    check(sizeZero.size == 0, "size=0 accepted");

    // size=52428800 boundary (50MB)
    AppUpdate.UpdateInfo size50M =
        AppUpdate.UpdateInfo.fromJson("{" + validBase + ", \"size\": 52428800}");
    check(size50M.size == 52428800L, "size=52428800 accepted");

    // size omitted (truly missing) -> defaults to 0
    AppUpdate.UpdateInfo sizeOmitted =
        AppUpdate.UpdateInfo.fromJson("{" + validBase + "}");
    check(sizeOmitted.size == 0, "size omitted defaults to 0");
    check(sizeOmitted.minSdk == 23, "minSdk omitted defaults to 23");
    check("".equals(sizeOmitted.releaseNotes), "releaseNotes omitted defaults to empty string");

    // 3. minSdk validation
    // minSdk="oops" (must be rejected, not fallback to 23)
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase + ", \"minSdk\": \"oops\"}"),
        "fromJson minSdk=\"oops\"");

    // minSdk=4294967319 (overflow / > 100, must be rejected, not fallback to 23)
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase + ", \"minSdk\": 4294967319}"),
        "fromJson minSdk=4294967319");

    // minSdk=true
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase + ", \"minSdk\": true}"),
        "fromJson minSdk=true");

    // minSdk=null
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase + ", \"minSdk\": null}"),
        "fromJson minSdk=null");

    // minSdk=26.9 decimal
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase + ", \"minSdk\": 26.9}"),
        "fromJson minSdk=26.9 decimal");

    // minSdk=0 (< 1)
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase + ", \"minSdk\": 0}"),
        "fromJson minSdk=0");

    // minSdk=101 (> 100)
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase + ", \"minSdk\": 101}"),
        "fromJson minSdk=101");

    // minSdk=1 boundary
    AppUpdate.UpdateInfo minSdk1 =
        AppUpdate.UpdateInfo.fromJson("{" + validBase + ", \"minSdk\": 1}");
    check(minSdk1.minSdk == 1, "minSdk=1 accepted");

    // minSdk=100 boundary
    AppUpdate.UpdateInfo minSdk100 =
        AppUpdate.UpdateInfo.fromJson("{" + validBase + ", \"minSdk\": 100}");
    check(minSdk100.minSdk == 100, "minSdk=100 accepted");

    // 4. releaseNotes validation
    // releaseNotes=123 (number, must be rejected)
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase + ", \"releaseNotes\": 123}"),
        "fromJson releaseNotes=123");

    // releaseNotes=true (boolean, must be rejected)
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase + ", \"releaseNotes\": true}"),
        "fromJson releaseNotes=true");

    // releaseNotes=null (null, must be rejected)
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase + ", \"releaseNotes\": null}"),
        "fromJson releaseNotes=null");

    // 5. versionName, downloadUrl, sha256 type validation
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase.replace("\"versionName\": \"0.9.8\"", "\"versionName\": 123") + "}"),
        "fromJson versionName=123");
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase.replace("\"versionName\": \"0.9.8\"", "\"versionName\": null") + "}"),
        "fromJson versionName=null");
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase.replace("\"versionName\": \"0.9.8\"", "\"versionName\": \"../evil\"") + "}"),
        "fromJson versionName traversal");

    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase.replace("\"downloadUrl\": \"https://mfcheer.github.io/luma/downloads/Still-0.9.8.apk\"", "\"downloadUrl\": 123") + "}"),
        "fromJson downloadUrl=123");
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase.replace("\"downloadUrl\": \"https://mfcheer.github.io/luma/downloads/Still-0.9.8.apk\"", "\"downloadUrl\": null") + "}"),
        "fromJson downloadUrl=null");
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase.replace("\"downloadUrl\": \"https://mfcheer.github.io/luma/downloads/Still-0.9.8.apk\"", "\"downloadUrl\": \"http://mfcheer.github.io/a.apk\"") + "}"),
        "fromJson downloadUrl http");

    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase.replace("\"sha256\": \"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"", "\"sha256\": 123") + "}"),
        "fromJson sha256=123");
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase.replace("\"sha256\": \"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"", "\"sha256\": null") + "}"),
        "fromJson sha256=null");
    expectIllegalArgument(
        () -> AppUpdate.UpdateInfo.fromJson("{" + validBase.replace("\"sha256\": \"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"", "\"sha256\": \"short_hash\"") + "}"),
        "fromJson sha256 short");
  }

  private static void testNumericBoundariesAndInvalidSize() {
    String validHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    // versionCode <= 0
    expectIllegalArgument(() -> new AppUpdate.UpdateInfo(0, "0.9.8", "https://a.com/a.apk", validHash, 1000, "", 23), "versionCode 0");
    expectIllegalArgument(() -> new AppUpdate.UpdateInfo(-1, "0.9.8", "https://a.com/a.apk", validHash, 1000, "", 23), "versionCode -1");

    // size < 0
    expectIllegalArgument(() -> new AppUpdate.UpdateInfo(26, "0.9.8", "https://a.com/a.apk", validHash, -5, "", 23), "size -5");
    // size > MAX_APK_SIZE (50MB)
    expectIllegalArgument(() -> new AppUpdate.UpdateInfo(26, "0.9.8", "https://a.com/a.apk", validHash, AppUpdate.MAX_APK_SIZE + 1, "", 23), "size > 50MB");

    // minSdk boundaries
    expectIllegalArgument(() -> new AppUpdate.UpdateInfo(26, "0.9.8", "https://a.com/a.apk", validHash, 1000, "", 0), "minSdk 0");
    expectIllegalArgument(() -> new AppUpdate.UpdateInfo(26, "0.9.8", "https://a.com/a.apk", validHash, 1000, "", 101), "minSdk 101");
  }

  private static void testVersionNameSafety() {
    String validHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    // Valid names
    new AppUpdate.UpdateInfo(26, "0.9.8", "https://a.com/a.apk", validHash, 1000, "", 23);
    new AppUpdate.UpdateInfo(26, "1.0.0-beta1", "https://a.com/a.apk", validHash, 1000, "", 23);
    new AppUpdate.UpdateInfo(26, "Still_0.9.8-r2", "https://a.com/a.apk", validHash, 1000, "", 23);

    // Unsafe / traversal names must be rejected
    expectIllegalArgument(() -> new AppUpdate.UpdateInfo(26, "../../evil", "https://a.com/a.apk", validHash, 1000, "", 23), "Path traversal versionName");
    expectIllegalArgument(() -> new AppUpdate.UpdateInfo(26, "app/sub", "https://a.com/a.apk", validHash, 1000, "", 23), "Slash in versionName");
    expectIllegalArgument(() -> new AppUpdate.UpdateInfo(26, "app\\sub", "https://a.com/a.apk", validHash, 1000, "", 23), "Backslash in versionName");
    expectIllegalArgument(() -> new AppUpdate.UpdateInfo(26, "name with spaces", "https://a.com/a.apk", validHash, 1000, "", 23), "Spaces in versionName");
    expectIllegalArgument(() -> new AppUpdate.UpdateInfo(26, "name\nnewline", "https://a.com/a.apk", validHash, 1000, "", 23), "Newlines in versionName");
  }

  private static void testHttpsStrictEnforcement() {
    check(AppUpdate.isHttpsUrl("https://mfcheer.github.io/luma/downloads/Still-0.9.8.apk"), "https URL must be accepted");
    check(AppUpdate.isHttpsUrl("HTTPS://EXAMPLE.COM/APK"), "case-insensitive https must be accepted");
    check(!AppUpdate.isHttpsUrl("http://mfcheer.github.io/luma/downloads/Still-0.9.8.apk"), "http URL must be rejected");
    check(!AppUpdate.isHttpsUrl("ftp://mfcheer.github.io/luma/downloads/Still-0.9.8.apk"), "ftp URL must be rejected");
    check(!AppUpdate.isHttpsUrl("file:///data/local/tmp/app.apk"), "file URL must be rejected");
    check(!AppUpdate.isHttpsUrl("javascript:alert(1)"), "javascript URL must be rejected");
    check(!AppUpdate.isHttpsUrl(""), "empty URL must be rejected");
    check(!AppUpdate.isHttpsUrl(null), "null URL must be rejected");
  }

  private static void testSystemCompatibilityMinSdk() {
    check(AppUpdate.isSystemCompatible(28, 23), "API 28 device is compatible with minSdk 23");
    check(AppUpdate.isSystemCompatible(23, 23), "API 23 device is compatible with minSdk 23");
    check(!AppUpdate.isSystemCompatible(21, 23), "API 21 device is incompatible with minSdk 23");
    check(!AppUpdate.isSystemCompatible(28, 35), "API 28 device is incompatible with minSdk 35");
    check(!AppUpdate.isSystemCompatible(999), "API 999 must be incompatible on current runtime");
  }

  private static void testCacheFileNameGeneration() {
    check("still_update_26.apk".equals(AppUpdate.getUpdateApkFileName(26)), "Update APK filename format");
    check("still_update_26.tmp".equals(AppUpdate.getUpdateTmpFileName(26)), "Update TMP filename format");
  }

  private static void testSha256Checksum() throws Exception {
    File temp = File.createTempFile("still-test", ".bin");
    temp.deleteOnExit();
    byte[] testData = "Still launcher TV update verification payload".getBytes(StandardCharsets.UTF_8);
    try (FileOutputStream fos = new FileOutputStream(temp)) {
      fos.write(testData);
    }

    String computed = AppUpdate.calculateSha256(temp);
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    byte[] expectedDigest = md.digest(testData);
    StringBuilder sb = new StringBuilder();
    for (byte b : expectedDigest) {
      sb.append(String.format("%02x", b));
    }
    String expected = sb.toString();

    check(computed.equalsIgnoreCase(expected), "Computed SHA-256 must match MessageDigest directly: " + computed + " vs " + expected);

    // Tampering test
    try (FileOutputStream fos = new FileOutputStream(temp)) {
      fos.write("Tampered payload".getBytes(StandardCharsets.UTF_8));
    }
    String tamperedHash = AppUpdate.calculateSha256(temp);
    check(!tamperedHash.equalsIgnoreCase(expected), "Tampered file must produce different SHA-256");

    temp.delete();
  }

  private static void testStorageRequirement() {
    File currentDir = new File(".");
    check(AppUpdate.hasEnoughSpace(currentDir, 1024 * 1024L), "Current disk should have at least 11MB free space");
    check(!AppUpdate.hasEnoughSpace(currentDir, 100_000_000_000_000_000L), "Excessive space requirement must be rejected");
  }

  private static void testStillFileProviderPathBoundaries() throws Exception {
    File baseDir = File.createTempFile("still-updates-test", "");
    baseDir.delete();
    baseDir.mkdirs();
    baseDir.deleteOnExit();

    File targetApk = new File(baseDir, "still_update_26.apk");
    targetApk.createNewFile();
    targetApk.deleteOnExit();

    // 1. Valid resolution
    File resolved = StillFileProvider.resolveFileForUri(baseDir, "/still_update_26.apk");
    check(resolved != null, "Valid file must resolve");
    check(resolved.getCanonicalPath().equals(targetApk.getCanonicalPath()), "Resolved path must match target canonical path");

    // 2. Traversal attempt via /../
    File escaped = StillFileProvider.resolveFileForUri(baseDir, "/../../../../etc/passwd");
    check(escaped != null, "getName strips directory components");
    check(escaped.getParentFile().getCanonicalPath().equals(baseDir.getCanonicalPath()), "Escaped path must stay strictly inside baseDir");
    check(escaped.getName().equals("passwd"), "Resolved file name must be passwd inside baseDir");

    // 3. Null / empty path handling
    check(StillFileProvider.resolveFileForUri(baseDir, null) == null, "Null path returns null");
    check(StillFileProvider.resolveFileForUri(baseDir, "") == null, "Empty path returns null");
    check(StillFileProvider.resolveFileForUri(baseDir, ".") == null, "Dot path returns null");
    check(StillFileProvider.resolveFileForUri(baseDir, "..") == null, "Dot dot path returns null");
    check(StillFileProvider.resolveFileForUri(null, "/file.apk") == null, "Null baseDir returns null");

    targetApk.delete();
    baseDir.delete();
  }

  private static void expectIllegalArgument(Runnable r, String testName) {
    try {
      r.run();
      throw new AssertionError("Expected IllegalArgumentException for: " + testName);
    } catch (IllegalArgumentException expected) {
      // Success
    }
  }
}
