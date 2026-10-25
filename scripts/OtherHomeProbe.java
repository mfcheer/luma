package com.jianping.qa;

import android.app.*;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.os.*;
import com.jianping.tv.data.Preferences;
import com.jianping.tv.platform.*;
import java.lang.reflect.Field;
import java.util.*;

/** Emulator-only integration checks; never included in Still. */
public final class OtherHomeProbe extends Instrumentation {
  private int checks;

  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  private void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }

  private static Field field(String name) throws Exception {
    Field f = WakeEntryService.class.getDeclaredField(name);
    f.setAccessible(true);
    return f;
  }

  public void onStart() {
    Bundle result = new Bundle();
    Context c = getTargetContext();
    SharedPreferences raw = c.getSharedPreferences("launcher", 0);
    boolean hadKey = raw.contains("otherHome");
    String original = new Preferences(c).otherHome();
    String originalHome = DeviceDiagnostics.homePackage(c);
    runOnMainSync(
        () -> {
          try {
            Preferences prefs = new Preferences(c);
            OtherHome homes = new OtherHome(c);
            List<ResolveInfo> list = homes.candidates();
            check(!list.isEmpty(), "Dedicated emulator has its original TV launcher");
            for (ResolveInfo info : list) {
              check(
                  !OtherHome.component(info).getPackageName().equals(c.getPackageName()),
                  "Still excluded");
              check(DeviceDiagnostics.usableHome(info), "Fallback/resolver excluded");
            }
            ComponentName target = OtherHome.component(list.get(0));
            prefs.otherHome("invalid");
            check(
                homes.preferred(Collections.emptyList()) == null && prefs.otherHome().isEmpty(),
                "No home clears stale choice");
            check(
                homes.preferred(Collections.singletonList(list.get(0))).equals(target),
                "Single home opens directly");
            List<ResolveInfo> two = new ArrayList<>(list);
            ResolveInfo dummy = new ResolveInfo();
            dummy.activityInfo = new android.content.pm.ActivityInfo();
            dummy.activityInfo.packageName = "example.missing";
            dummy.activityInfo.name = "example.missing.Home";
            two.add(dummy);
            check(homes.preferred(two) == null, "Multiple homes require first selection");
            prefs.otherHome(target.flattenToString());
            check(homes.preferred(two).equals(target), "Remembered exact component wins");
            prefs.otherHome(OtherHome.component(dummy).flattenToString());
            check(
                !homes.open(OtherHome.component(dummy)) && prefs.otherHome().isEmpty(),
                "Removed target cannot launch and choice clears");
            final Intent[] launched = new Intent[1];
            Context accepting =
                new ContextWrapper(c) {
                  public void startActivity(Intent i) {
                    launched[0] = i;
                  }
                };
            check(new OtherHome(accepting).open(target), "Accepted explicit launch succeeds");
            check(
                prefs.otherHome().equals(target.flattenToString()),
                "Successful launch remembers choice");
            check(
                launched[0].getComponent().equals(target)
                    && launched[0].hasCategory(Intent.CATEGORY_HOME)
                    && (launched[0].getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0,
                "Explicit Home launch flags");
            Context rejecting =
                new ContextWrapper(c) {
                  public void startActivity(Intent i) {
                    throw new SecurityException("QA rejected");
                  }
                };
            check(
                !new OtherHome(rejecting).open(target) && prefs.otherHome().isEmpty(),
                "Rejected launch stays in Still and clears stale choice");
            check(originalHome.equals(DeviceDiagnostics.homePackage(c)), "Default Home unchanged");
            WakeEntryService service = new WakeEntryService();
            Field instance = field("instance");
            check(instance.get(null) == null, "No real wake service on API33 emulator");
            Handler handler = (Handler) field("handler").get(service);
            Runnable pending = (Runnable) field("returnHome").get(service);
            Runnable confirm = (Runnable) field("confirmEntry").get(service);
            handler.postDelayed(pending, 60000);
            handler.postDelayed(confirm, 60000);
            field("pendingId").setLong(service, 123);
            instance.set(null, service);
            try {
              WakeCycle cycle = new WakeCycle(true);
              field("cycle").set(service, cycle);
              boolean bootEnabled = prefs.bootEnabled();
              WakeEntryService.cancelPendingReturn();
              check(
                  !handler.hasCallbacks(pending)
                      && !handler.hasCallbacks(confirm)
                      && field("pendingId").getLong(service) == 0,
                  "Deliberate switch cancels pending return and confirmation");
              cycle.screenOff();
              check(
                  cycle.screenOn() && !cycle.screenOn() && prefs.bootEnabled() == bootEnabled,
                  "Next wake cycle and automatic-entry preference preserved");
            } finally {
              handler.removeCallbacksAndMessages(null);
              instance.set(null, null);
            }
            result.putString(
                "result",
                "PASS: "
                    + checks
                    + " integration checks: candidates, single/multiple, remembered/stale, explicit"
                    + " launch, rejection, default Home and pending wake cancellation");
          } catch (Throwable e) {
            result.putString("failure", android.util.Log.getStackTraceString(e));
          } finally {
            if (hadKey) raw.edit().putString("otherHome", original).commit();
            else raw.edit().remove("otherHome").commit();
          }
        });
    setInTouchMode(false);
    finish(result.containsKey("failure") ? Activity.RESULT_CANCELED : Activity.RESULT_OK, result);
  }
}
