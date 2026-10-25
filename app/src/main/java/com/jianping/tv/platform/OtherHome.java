package com.jianping.tv.platform;

import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import com.jianping.tv.data.Preferences;
import java.util.*;

/** Explicit, temporary desktop launch; never changes the system's preferred Home. */
public final class OtherHome {
  private final Context context;
  private final Preferences prefs;

  public OtherHome(Context context) {
    this.context = context;
    prefs = new Preferences(context);
  }

  public List<ResolveInfo> candidates() {
    List<ResolveInfo> result = new ArrayList<>();
    Set<ComponentName> seen = new HashSet<>();
    for (ResolveInfo info :
        context
            .getPackageManager()
            .queryIntentActivities(
                DeviceDiagnostics.homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)) {
      if (info.activityInfo == null || !DeviceDiagnostics.usableHome(info)) continue;
      ComponentName component = component(info);
      if (context.getPackageName().equals(component.getPackageName())) continue;
      if (!canLaunch(info.activityInfo)) info = publicAlias(info.activityInfo);
      if (info != null && seen.add(component(info))) result.add(info);
    }
    return result;
  }

  private boolean canLaunch(ActivityInfo activity) {
    return activity.enabled
        && activity.exported
        && (activity.permission == null
            || context.checkSelfPermission(activity.permission)
                == PackageManager.PERMISSION_GRANTED);
  }

  private ResolveInfo publicAlias(ActivityInfo home) {
    // Some OEMs protect the HOME activity but expose an alias to that exact activity.
    // Use only an exported, permitted alias declared by the same HOME package.
    try {
      Intent entry =
          new Intent(Intent.ACTION_MAIN)
              .addCategory(Intent.CATEGORY_DEFAULT)
              .setPackage(home.packageName);
      for (ResolveInfo result :
          context
              .getPackageManager()
              .queryIntentActivities(entry, PackageManager.MATCH_DEFAULT_ONLY)) {
        ActivityInfo alias = result.activityInfo;
        if (alias != null
            && home.packageName.equals(alias.packageName)
            && home.name.equals(alias.targetActivity)
            && canLaunch(alias)) {
          return result;
        }
      }
    } catch (RuntimeException ignored) {
      // A package can disappear while its entry is being checked.
    }
    return null;
  }

  public ComponentName preferred(List<ResolveInfo> candidates) {
    String saved = prefs.otherHome();
    ComponentName remembered = ComponentName.unflattenFromString(saved);
    for (ResolveInfo info : candidates) if (component(info).equals(remembered)) return remembered;
    if (!saved.isEmpty()) prefs.otherHome("");
    return candidates.size() == 1 ? component(candidates.get(0)) : null;
  }

  public boolean open(ComponentName target) {
    // Recheck after a picker: a desktop could have been removed or disabled meanwhile.
    for (ResolveInfo info : candidates()) {
      if (!component(info).equals(target)) continue;
      try {
        // Match an alias's public MAIN/DEFAULT filter, including on Android 13+.
        Intent entry =
            info.activityInfo.targetActivity == null
                ? DeviceDiagnostics.homeIntent()
                : new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_DEFAULT);
        context.startActivity(
            entry
                .setComponent(target)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        prefs.otherHome(target.flattenToString());
        WakeEntryService.cancelPendingReturn();
        return true;
      } catch (RuntimeException ignored) {
        break;
      }
    }
    if (target != null && target.flattenToString().equals(prefs.otherHome())) prefs.otherHome("");
    return false;
  }

  public static ComponentName component(ResolveInfo info) {
    return new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
  }
}
