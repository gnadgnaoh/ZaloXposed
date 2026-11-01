package io.github.electroheavenvn.zaloxposed.utilities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;

/**
 * Runtime checks for the optional microG-RE provider used by Zalo Drive support.
 *
 * <p>Ported from {@code temp-readonly/ZaloMicroGSupport (1).java}: the provider
 * check prompts for microG-RE before account selection, and the account refresh
 * is coalesced onto the main thread after the picker returns.</p>
 */
public final class ZaloMicroGSupport
{
    private static final String PACKAGE = "app.revanced.android.gms";
    private static final String DOWNLOAD_URL = "https://morphe.software/microg";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ZaloMicroGSupport() { }

    /**
     * Checks the optional provider and prompts when it is missing or disabled.
     *
     * @return {@code false} when the provider is unavailable so the current
     * operation can stop; {@code true} when the provider is enabled, the
     * activity is null, or the check fails unexpectedly.
     */
    public static boolean checkGmsCore(Activity activity)
    {
        if (activity == null)
            return true;
        try
        {
            PackageInfo info = activity.getPackageManager().getPackageInfo(PACKAGE, 0);
            if (info.applicationInfo != null && info.applicationInfo.enabled)
                return true;
        }
        catch (PackageManager.NameNotFoundException ignored) { }
        catch (RuntimeException ignored)
        {
            return true;
        }

        if (!activity.isFinishing() && !activity.isDestroyed())
        {
            try
            {
                boolean english = Utils.IsEnglish();
                new AlertDialog.Builder(activity)
                    .setTitle(english ? "microG required" : "Cần microG")
                    .setMessage(english
                        ? "Google Drive backup and restore needs microG-RE."
                        : "Sao lưu và phục hồi Google Drive cần microG-RE.")
                    .setNegativeButton(english ? "Cancel" : "Hủy", null)
                    .setPositiveButton(english ? "Install" : "Cài đặt", (dialog, which) ->
                    {
                        try
                        {
                            activity.startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse(DOWNLOAD_URL)));
                        }
                        catch (ActivityNotFoundException ignored) { }
                        catch (RuntimeException ignored) { }
                    })
                    .show();
            }
            catch (RuntimeException ignored)
            {
                // Dialog creation is best effort; normal app use must remain unaffected.
            }
        }
        return false;
    }

    /**
     * Schedules a delayed Drive refresh for the selected account. Invalid
     * arguments are ignored. The view is held weakly so a closed screen is not
     * retained.
     */
    public static void scheduleAccountRefresh(Object view, String accountName)
    {
        if (view == null || accountName == null || accountName.isEmpty())
            return;
        WeakReference<Object> reference = new WeakReference<>(view);
        MAIN.postDelayed(() ->
        {
            Object target = reference.get();
            if (target == null)
                return;
            try
            {
                java.lang.reflect.Method refresh;
                try
                {
                    refresh = target.getClass().getMethod("P6", String.class);
                }
                catch (NoSuchMethodException ignored)
                {
                    refresh = target.getClass().getMethod("A6", String.class);
                }
                refresh.invoke(target, accountName);
            }
            catch (ReflectiveOperationException | RuntimeException ignored)
            {
                // A changed/hidden Zalo method must not crash the host app.
            }
        }, 250);
    }
}
