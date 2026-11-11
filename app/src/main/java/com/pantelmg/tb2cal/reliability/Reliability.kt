package com.pantelmg.tb2cal.reliability

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlarmManager
import android.accounts.Account
import android.content.ComponentName
import android.content.ContentResolver
import android.provider.CalendarContract
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as SysSettings
import androidx.core.app.NotificationManagerCompat
import com.pantelmg.tb2cal.calendar.CalendarWriter
import com.pantelmg.tb2cal.model.Settings
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Detects and opens the system/OEM settings that decide whether Android (and MIUI/HyperOS,
 * EMUI, ColorOS, Funtouch…) may kill TB2Cal while a TimeBlock runs.
 */
object Reliability {
    /** Bumped on every Activity resume so checklist states refresh after visiting system settings. */
    val resumeTick = MutableStateFlow(0)

    private val brand = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
    val isXiaomi: Boolean = listOf("xiaomi", "redmi", "poco").any { it in brand }
    val oemName: String = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }

    /** OEMs that ship their own "autostart / app launch" manager. */
    val hasAutostartManager: Boolean = listOf(
        "xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "realme", "oneplus", "vivo", "iqoo", "letv", "asus"
    ).any { it in brand }

    // ---------- auto-detected ----------
    fun notificationsOk(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun calendarOk(ctx: Context) = CalendarWriter.hasPermission(ctx)


    fun batteryUnrestricted(ctx: Context) =
        ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true

    fun backgroundRestricted(ctx: Context) =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            ctx.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true

    fun exactAlarmsOk(ctx: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ctx.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    /** Number of steps still open (manual ones count until confirmed). */
    fun openIssues(ctx: Context, s: Settings): Int {
        val gmail = s.gmailView // Account sync only matters when the Calendar tab shows Gmail events
        return listOf(
        !notificationsOk(ctx),
        !calendarOk(ctx),
        !batteryUnrestricted(ctx),
        backgroundRestricted(ctx),
        !exactAlarmsOk(ctx),
        hasAutostartManager && !s.ackAutostart,
        isXiaomi && !s.ackBatterySaver,
        gmail && !syncOk(ctx),
        ).count { it }
    }

    /**
     * Auto-sync is on for the phone, and Calendar sync is on for every Gmail account in it.
     * (Accounts are only visible with calendar access; Without it, just the main switch is checked.)
     */
    fun syncOk(ctx: Context): Boolean {
        if (!ContentResolver.getMasterSyncAutomatically()) return false
        if (!calendarOk(ctx)) return true
        return runCatching {
            val accounts = ctx.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars.ACCOUNT_NAME),
                "${CalendarContract.Calendars.ACCOUNT_TYPE}=?", arrayOf("com.google"), null
            )?.use { c -> buildSet { while (c.moveToNext()) c.getString(0)?.let { add(it) } } }.orEmpty()
            accounts.all { ContentResolver.getSyncAutomatically(Account(it, "com.google"), CalendarContract.AUTHORITY) }
        }.getOrDefault(true)
    }

    fun openSyncSettings(ctx: Context) = tryStart(
        ctx,
        Intent(SysSettings.ACTION_SYNC_SETTINGS),
        Intent(SysSettings.ACTION_SETTINGS),
    )


    // ---------- openers (return false if nothing could be opened) ----------
    fun openNotificationSettings(ctx: Context) = tryStart(
        ctx,
        Intent(SysSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(SysSettings.EXTRA_APP_PACKAGE, ctx.packageName),
        appDetails(ctx),
    )

    @SuppressLint("BatteryLife")
    fun requestBatteryUnrestricted(ctx: Context) = tryStart(
        ctx,
        Intent(SysSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
        Intent(SysSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        appDetails(ctx),
    )

    fun openExactAlarmSettings(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return tryStart(
            ctx,
            Intent(SysSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")),
            appDetails(ctx),
        )
    }

    fun openAppDetails(ctx: Context) = tryStart(ctx, appDetails(ctx))

    fun openAutostart(ctx: Context): Boolean {
        val candidates = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
            "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
            "com.asus.mobilemanager" to "com.asus.mobilemanager.entry.FunctionActivity",
        ).map { (pkg, cls) -> Intent().setComponent(ComponentName(pkg, cls)) }
        return tryStart(ctx, *candidates.toTypedArray(), appDetails(ctx))
    }

    /** MIUI/HyperOS per-app "Battery saver" page (set to "No restrictions"). */
    fun openXiaomiBatterySaver(ctx: Context, pkg: String = ctx.packageName, label: String = "TB2Cal") = tryStart(
        ctx,
        Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY")
            .setClassName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
            .putExtra("package_name", pkg)
            .putExtra("package_label", label),
        appDetails(ctx, pkg),
    )


    private fun appDetails(ctx: Context, pkg: String = ctx.packageName) =
        Intent(SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))

    private fun tryStart(ctx: Context, vararg intents: Intent): Boolean {
        for (i in intents) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: Exception) {
                // ActivityNotFound / SecurityException on ROMs without that screen → try next
            }
        }
        return false
    }
}
