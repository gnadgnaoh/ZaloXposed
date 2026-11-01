package io.github.electroheavenvn.zaloxposed.hooks.functional;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.electroheavenvn.zaloxposed.hooks.BaseHook;
import io.github.electroheavenvn.zaloxposed.utilities.Config;
import io.github.electroheavenvn.zaloxposed.utilities.HookUtils;
import io.github.electroheavenvn.zaloxposed.utilities.Logger;
import io.github.electroheavenvn.zaloxposed.utilities.ZaloMicroGSupport;

/**
 * Runtime MicroG-RE redirection for Zalo's Google Drive backup and restore.
 *
 * <p>Stock Zalo hard-codes the Google account type and the Play Services package in
 * its Drive/backup plumbing. This hook replaces the Drive account-picker entry points
 * with Android's system account picker for the {@code app.revanced} account type,
 * checks microG-RE before opening it, and coalesces the account refresh after the
 * picker result. Account-manager and service-binding redirections remain as a
 * best-effort fallback for the rest of Zalo's Drive code.</p>
 */
public class MicroGSupportHook extends BaseHook
{
    public static final String MICROG_PACKAGE = "app.revanced.android.gms";
    public static final String MICROG_ACCOUNT_TYPE = "app.revanced";

    private static final String SYNC_GOOGLE_ACCOUNT_BASE_VIEW =
        "com.zing.zalo.ui.backuprestore.drive.SyncGoogleAccountBaseView";
    private static final String MANAGE_GOOGLE_ACCOUNT_VIEW =
        "com.zing.zalo.ui.backuprestore.drive.ManageGoogleAccountView";
    private static final String ZALO_LAUNCHER_ACTIVITY =
        "com.zing.zalo.ui.ZaloLauncherActivity";
    private static final int ACCOUNT_PICKER_REQUEST_CODE = 0x3eb;
    private static final String LEGACY_ACCOUNT_EXTRA = "authAccount";

    private static final AtomicBoolean LAUNCHER_PROMPT_SHOWN = new AtomicBoolean();

    @Override
    public void hook() throws Throwable
    {
        if (!Config.getEnableMicroGSupport())
        {
            Logger.i("MicroG support disabled");
            return;
        }

        if (isMicroGInstalled())
        {
            hookAccountManager();
            hookServiceBinding();
            hookAccountPickerIntents();
        }
        else
        {
            Logger.i("MicroG support enabled but " + MICROG_PACKAGE
                + " is not installed; the install prompt will be shown");
        }

        // These are installed even when microG is missing so checkGmsCore can
        // show the install prompt at the Drive account picker and launcher.
        hookDriveAccountPicker();
        hookDriveAccountResult();
        hookLauncherProviderCheck();
    }

    private boolean isMicroGInstalled()
    {
        Context context = HookUtils.currentContext();
        if (context == null)
            return false;
        try
        {
            context.getPackageManager().getPackageInfo(MICROG_PACKAGE, 0);
            return true;
        }
        catch (PackageManager.NameNotFoundException ignored)
        {
            return false;
        }
    }

    /**
     * Replaces Zalo's Drive account discovery with Android's system account
     * picker for the microG account type. The stock methods are
     * {@code SyncGoogleAccountBaseView.x6/M6(String)} and
     * {@code ManageGoogleAccountView.I6/X6(String)}.
     */
    private void hookDriveAccountPicker()
    {
        hookDriveAccountPicker(SYNC_GOOGLE_ACCOUNT_BASE_VIEW, "x6", "M6");
        hookDriveAccountPicker(MANAGE_GOOGLE_ACCOUNT_VIEW, "I6", "X6");
    }

    private void hookDriveAccountPicker(String className, String... methodNames)
    {
        try
        {
            Class<?> type = Class.forName(className, false, classLoader);
            int hooked = 0;
            for (Method method : type.getDeclaredMethods())
            {
                if (method.getReturnType() != void.class || !matches(method, methodNames, String.class))
                    continue;
                method.setAccessible(true);
                module.hook(method).intercept(chain ->
                {
                    if (!Config.getEnableMicroGSupport())
                        return chain.proceed();
                    Activity activity = findActivity(HookUtils.resolveContext(chain.getThisObject()));
                    if (activity == null)
                        return chain.proceed();
                    try
                    {
                        Intent picker = AccountManager.newChooseAccountIntent(
                            null, null, new String[]{MICROG_ACCOUNT_TYPE},
                            null, null, null, null);
                        if (picker == null)
                            return chain.proceed();
                        if (!ZaloMicroGSupport.checkGmsCore(activity))
                            return null;
                        activity.startActivityForResult(picker, ACCOUNT_PICKER_REQUEST_CODE);
                        Logger.i("MicroG account picker opened from " + method.getName());
                        return null;
                    }
                    catch (ActivityNotFoundException missing)
                    {
                        Logger.e("No system account picker available");
                        return chain.proceed();
                    }
                    catch (Throwable t)
                    {
                        Logger.e(t);
                        return chain.proceed();
                    }
                });
                Logger.i("Hooking MicroG account picker: " + method);
                hooked++;
            }
            Logger.i("MicroG account picker hooks installed for " + className + ": " + hooked);
        }
        catch (Throwable t)
        {
            Logger.e("MicroG account picker unavailable in " + className + ": " + t);
        }
    }

    /**
     * Makes Zalo's existing {@code onActivityResult} understand the system
     * picker result and schedules the delayed Drive account refresh.
     */
    private void hookDriveAccountResult()
    {
        try
        {
            Class<?> type = Class.forName(SYNC_GOOGLE_ACCOUNT_BASE_VIEW, false, classLoader);
            int hooked = 0;
            for (Method method : type.getDeclaredMethods())
            {
                if (!"onActivityResult".equals(method.getName()))
                    continue;
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length != 3 || parameters[0] != int.class
                    || parameters[1] != int.class || parameters[2] != Intent.class)
                    continue;
                method.setAccessible(true);
                module.hook(method).intercept(chain ->
                {
                    if (!Config.getEnableMicroGSupport())
                        return chain.proceed();
                    String accountName = accountNameFromResult(chain);
                    if (accountName == null)
                        return chain.proceed();
                    Object[] args = chain.getArgs().toArray();
                    Intent original = (Intent)args[2];
                    Intent copy = new Intent(original);
                    copy.putExtra(LEGACY_ACCOUNT_EXTRA, accountName);
                    args[2] = copy;
                    Object result = chain.proceedWith(args);
                    ZaloMicroGSupport.scheduleAccountRefresh(chain.getThisObject(), accountName);
                    return result;
                });
                Logger.i("Hooking MicroG account result: " + method);
                hooked++;
            }
            Logger.i("MicroG account result hooks installed: " + hooked);
        }
        catch (Throwable t)
        {
            Logger.e("MicroG account result unavailable: " + t);
        }
    }

    private static String accountNameFromResult(io.github.libxposed.api.XposedInterface.Chain chain)
    {
        try
        {
            Object requestCode = chain.getArg(0);
            Object dataArg = chain.getArg(2);
            if (!(requestCode instanceof Integer)
                || (Integer)requestCode != ACCOUNT_PICKER_REQUEST_CODE
                || !(dataArg instanceof Intent))
                return null;
            Intent data = (Intent)dataArg;
            String name = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            if (name == null || name.isEmpty())
                name = data.getStringExtra(LEGACY_ACCOUNT_EXTRA);
            return name == null || name.isEmpty() ? null : name;
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    /**
     * Mirrors the reference launcher provider check. It runs once per process so
     * an activity recreation does not repeatedly prompt.
     */
    private void hookLauncherProviderCheck()
    {
        try
        {
            Class<?> launcher = Class.forName(ZALO_LAUNCHER_ACTIVITY, false, classLoader);
            Method onCreate = launcher.getDeclaredMethod("onCreate", Bundle.class);
            onCreate.setAccessible(true);
            module.hook(onCreate).intercept(chain ->
            {
                Object result = chain.proceed();
                if (Config.getEnableMicroGSupport()
                    && LAUNCHER_PROMPT_SHOWN.compareAndSet(false, true))
                {
                    Object self = chain.getThisObject();
                    if (self instanceof Activity)
                        ZaloMicroGSupport.checkGmsCore((Activity)self);
                }
                return result;
            });
            Logger.i("MicroG launcher provider check installed");
        }
        catch (Throwable t)
        {
            Logger.e("MicroG launcher provider check unavailable: " + t);
        }
    }

    private static boolean matches(Method method, String[] names, Class<?>... parameters)
    {
        boolean nameMatches = false;
        for (String name : names)
        {
            if (name.equals(method.getName()))
            {
                nameMatches = true;
                break;
            }
        }
        if (!nameMatches)
            return false;
        Class<?>[] actual = method.getParameterTypes();
        if (actual.length != parameters.length)
            return false;
        for (int i = 0; i < parameters.length; i++)
        {
            if (actual[i] != parameters[i])
                return false;
        }
        return true;
    }

    private static Activity findActivity(Context context)
    {
        Context current = context;
        for (int depth = 0; current != null && depth < 16; depth++)
        {
            if (current instanceof Activity)
                return (Activity)current;
            if (!(current instanceof ContextWrapper))
                break;
            Context base = ((ContextWrapper)current).getBaseContext();
            if (base == current)
                break;
            current = base;
        }
        return null;
    }

    private void hookAccountManager() throws Exception
    {
        int count = 0;
        for (Method method : AccountManager.class.getDeclaredMethods())
        {
            String name = method.getName();
            if ("getAccountsByType".equals(name) && method.getParameterTypes().length == 1
                && method.getParameterTypes()[0] == String.class)
            {
                method.setAccessible(true);
                module.hook(method).intercept(chain ->
                {
                    Object type = chain.getArg(0);
                    if (!Config.getEnableMicroGSupport() || !"com.google".equals(type))
                        return chain.proceed();
                    Object[] args = chain.getArgs().toArray();
                    args[0] = MICROG_ACCOUNT_TYPE;
                    return chain.proceedWith(args);
                });
                count++;
            }
            else if ("getAccountsByTypeForPackage".equals(name)
                && method.getParameterTypes().length == 2
                && method.getParameterTypes()[0] == String.class)
            {
                method.setAccessible(true);
                module.hook(method).intercept(chain ->
                {
                    Object type = chain.getArg(0);
                    if (!Config.getEnableMicroGSupport() || !"com.google".equals(type))
                        return chain.proceed();
                    Object[] args = chain.getArgs().toArray();
                    args[0] = MICROG_ACCOUNT_TYPE;
                    return chain.proceedWith(args);
                });
                count++;
            }
            else if ("newChooseAccountIntent".equals(name) && Modifier.isStatic(method.getModifiers()))
            {
                method.setAccessible(true);
                module.hook(method).intercept(chain ->
                {
                    if (!Config.getEnableMicroGSupport())
                        return chain.proceed();
                    Object[] args = chain.getArgs().toArray();
                    for (int i = 0; i < args.length; i++)
                    {
                        if (args[i] instanceof String[])
                        {
                            args[i] = new String[]{MICROG_ACCOUNT_TYPE};
                            break;
                        }
                    }
                    return chain.proceedWith(args);
                });
                count++;
            }
            else if (("getAuthToken".equals(name) || "blockingGetAuthToken".equals(name))
                && method.getParameterTypes().length > 0
                && method.getParameterTypes()[0] == Account.class)
            {
                method.setAccessible(true);
                module.hook(method).intercept(chain ->
                {
                    Object account = chain.getArg(0);
                    if (!Config.getEnableMicroGSupport() || !(account instanceof Account)
                        || !"com.google".equals(((Account)account).type))
                        return chain.proceed();
                    Object[] args = chain.getArgs().toArray();
                    args[0] = new Account(((Account)account).name, MICROG_ACCOUNT_TYPE);
                    return chain.proceedWith(args);
                });
                count++;
            }
        }
        Logger.i("MicroG account hooks installed: " + count);
    }

    private void hookServiceBinding() throws Exception
    {
        Method bindService = ContextWrapper.class.getDeclaredMethod(
            "bindService", Intent.class, ServiceConnection.class, int.class);
        module.hook(bindService).intercept(chain ->
        {
            Object intent = chain.getArg(0);
            if (Config.getEnableMicroGSupport() && intent instanceof Intent)
            {
                Object[] args = chain.getArgs().toArray();
                args[0] = remapGooglePackage((Intent)intent);
                return chain.proceedWith(args);
            }
            return chain.proceed();
        });

        try
        {
            Method bindServiceAsUser = ContextWrapper.class.getDeclaredMethod(
                "bindServiceAsUser", Intent.class, ServiceConnection.class, int.class,
                android.os.UserHandle.class);
            module.hook(bindServiceAsUser).intercept(chain ->
            {
                Object intent = chain.getArg(0);
                if (Config.getEnableMicroGSupport() && intent instanceof Intent)
                {
                    Object[] args = chain.getArgs().toArray();
                    args[0] = remapGooglePackage((Intent)intent);
                    return chain.proceedWith(args);
                }
                return chain.proceed();
            });
        }
        catch (NoSuchMethodException ignored) { }

        try
        {
            Method startService = ContextWrapper.class.getDeclaredMethod(
                "startService", Intent.class);
            module.hook(startService).intercept(chain ->
            {
                Object intent = chain.getArg(0);
                if (Config.getEnableMicroGSupport() && intent instanceof Intent)
                {
                    Object[] args = chain.getArgs().toArray();
                    args[0] = remapGooglePackage((Intent)intent);
                    return chain.proceedWith(args);
                }
                return chain.proceed();
            });
        }
        catch (NoSuchMethodException ignored) { }
    }

    private static Intent remapGooglePackage(Intent source)
    {
        ComponentName component = source.getComponent();
        if (component != null && "com.google.android.gms".equals(component.getPackageName()))
        {
            Intent copy = new Intent(source);
            copy.setComponent(new ComponentName(MICROG_PACKAGE, component.getClassName()));
            return copy;
        }
        if ("com.google.android.gms".equals(source.getPackage()))
        {
            Intent copy = new Intent(source);
            copy.setPackage(MICROG_PACKAGE);
            return copy;
        }
        return source;
    }

    private void hookAccountPickerIntents() throws Exception
    {
        Method startForResult = Activity.class.getDeclaredMethod(
            "startActivityForResult", Intent.class, int.class);
        module.hook(startForResult).intercept(chain ->
        {
            Object intent = chain.getArg(0);
            if (Config.getEnableMicroGSupport() && intent instanceof Intent)
            {
                Object[] args = chain.getArgs().toArray();
                args[0] = remapAccountPicker((Intent)intent);
                return chain.proceedWith(args);
            }
            return chain.proceed();
        });

        Method startActivity = Activity.class.getDeclaredMethod("startActivity", Intent.class);
        module.hook(startActivity).intercept(chain ->
        {
            Object intent = chain.getArg(0);
            if (Config.getEnableMicroGSupport() && intent instanceof Intent)
            {
                Object[] args = chain.getArgs().toArray();
                args[0] = remapAccountPicker((Intent)intent);
                return chain.proceedWith(args);
            }
            return chain.proceed();
        });
    }

    private static Intent remapAccountPicker(Intent source)
    {
        if (!source.hasExtra("accountTypes")
            && !source.hasExtra(AccountManager.KEY_ACCOUNT_TYPE))
            return source;
        Intent copy = new Intent(source);
        String[] types = copy.getStringArrayExtra("accountTypes");
        if (types != null && types.length > 0)
        {
            for (String type : types)
            {
                if ("com.google".equals(type))
                {
                    copy.putExtra("accountTypes",
                        new String[]{MICROG_ACCOUNT_TYPE});
                    break;
                }
            }
        }
        if ("com.google".equals(copy.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)))
            copy.putExtra(AccountManager.KEY_ACCOUNT_TYPE, MICROG_ACCOUNT_TYPE);
        return copy;
    }
}
