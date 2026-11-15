/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.util

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import com.example.MainActivity
import com.example.R

/**
 * The launcher icon choices and the component-enablement switch behind them.
 *
 * MainActivity is not a launcher component itself. Every option is an activity-alias targeting it,
 * so exactly one alias can own the launcher intent at a time. The alias definitions live in
 * AndroidManifest.xml; this object only flips their enabled state.
 */
enum class LauncherIconOption(
    val key: String,
    val aliasName: String,
    val label: String,
    val description: String,
    val previewRes: Int,
    val adaptiveIconRes: Int
) {
    DEFAULT(
        key = "default",
        aliasName = "LauncherDefault",
        label = "IronPulse",
        description = "The original green and cyan identity.",
        previewRes = R.drawable.ic_app_icon_preview_default,
        adaptiveIconRes = R.mipmap.ic_launcher
    ),
    CYAN(
        key = "cyan",
        aliasName = "LauncherCyan",
        label = "Cool Cyan",
        description = "The same mark in an electric blue-cyan.",
        previewRes = R.drawable.ic_app_icon_preview_cyan,
        adaptiveIconRes = R.mipmap.ic_launcher_cyan
    ),
    AMBER(
        key = "amber",
        aliasName = "LauncherAmber",
        label = "Signal Amber",
        description = "A warm amber variation with the same geometry.",
        previewRes = R.drawable.ic_app_icon_preview_amber,
        adaptiveIconRes = R.mipmap.ic_launcher_amber
    ),
    VIOLET(
        key = "violet",
        aliasName = "LauncherViolet",
        label = "Ultra Violet",
        description = "A violet take on the IronPulse mark.",
        previewRes = R.drawable.ic_app_icon_preview_violet,
        adaptiveIconRes = R.mipmap.ic_launcher_violet
    ),
    OCEAN(
        key = "ocean",
        aliasName = "LauncherOcean",
        label = "Ocean Blue",
        description = "A deep ocean blue with a cool highlight.",
        previewRes = R.drawable.ic_app_icon_preview_ocean,
        adaptiveIconRes = R.mipmap.ic_launcher_ocean
    ),
    CRIMSON(
        key = "crimson",
        aliasName = "LauncherCrimson",
        label = "Crimson Red",
        description = "A bold crimson take on the IronPulse mark.",
        previewRes = R.drawable.ic_app_icon_preview_crimson,
        adaptiveIconRes = R.mipmap.ic_launcher_crimson
    ),
    ROSE(
        key = "rose",
        aliasName = "LauncherRose",
        label = "Rose Quartz",
        description = "A soft rose-pink variation with the same geometry.",
        previewRes = R.drawable.ic_app_icon_preview_rose,
        adaptiveIconRes = R.mipmap.ic_launcher_rose
    ),
    ARCTIC(
        key = "arctic",
        aliasName = "LauncherArctic",
        label = "Arctic White",
        description = "A clean silver-white version for a minimal look.",
        previewRes = R.drawable.ic_app_icon_preview_arctic,
        adaptiveIconRes = R.mipmap.ic_launcher_arctic
    );

    companion object {
        val all: List<LauncherIconOption> = entries

        fun fromKey(key: String?): LauncherIconOption =
            entries.firstOrNull { it.key == key?.trim()?.lowercase() } ?: DEFAULT
    }
}

object LauncherIcons {

    /** The one-shot initial launcher entry; disabled as soon as the app starts. */
    private const val BOOTSTRAP_ALIAS = "LauncherBootstrap"

    /**
     * Activity aliases are declared in the manifest's `com.example` namespace, while the installed
     * package id is `com.aistudio.fitness.ironpulse`. ComponentName needs the package id plus the
     * actual class name; joining the package id with the short alias name does not resolve.
     */
    private fun aliasComponent(packageName: String, aliasName: String): ComponentName {
        val namespace = MainActivity::class.java.name.substringBeforeLast('.')
        return ComponentName(packageName, "$namespace.$aliasName")
    }

    private fun aliasComponent(packageName: String, option: LauncherIconOption): ComponentName =
        aliasComponent(packageName, option.aliasName)


    /**
     * Enables [option] first and then disables the other aliases.
     *
     * Enabling first means the launcher never sees a moment with no MAIN/LAUNCHER component.
     * DONT_KILL_APP keeps the running workout/session alive while the launcher entry changes.
     */
    fun apply(context: Context, option: LauncherIconOption) {
        val packageManager = context.packageManager
        val packageName = context.packageName
        val selected = aliasComponent(packageName, option)
        val enabled = runCatching {
            packageManager.setComponentEnabledSetting(
                selected,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            true
        }.getOrDefault(false)
        // Never disable the fallback entries if the requested icon could not be enabled: an icon
        // switch must not leave the app with no launcher entry at all.
        if (!enabled) return
        LauncherIconOption.entries
            .filter { it != option }
            .forEach { other ->
                runCatching {
                    packageManager.setComponentEnabledSetting(
                        aliasComponent(packageName, other),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        // This one is enabled by default only so a fresh install has an icon to open. It must not
        // remain visible once the saved icon alias is active.
        runCatching {
            packageManager.setComponentEnabledSetting(
                aliasComponent(packageName, BOOTSTRAP_ALIAS),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    /** The selectable aliases that are currently launcher components, for diagnostics/tests. */
    fun enabledAliases(context: Context): List<String> =
        LauncherIconOption.entries.mapNotNull { option ->
            val component = aliasComponent(context.packageName, option)
            val state = runCatching {
                context.packageManager.getComponentEnabledSetting(component)
            }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) option.aliasName else null
        }

    /**
     * Makes the recent-tasks card use the selected icon too.
     *
     * An activity alias only controls the launcher entry. Recents draws the task's own description
     * (falling back to the application icon), which is why the default icon kept appearing there
     * after a change. Setting the task description with the chosen artwork updates the existing
     * task without recreating it, so the change is visible immediately.
     */
    fun applyTaskIcon(context: Context, option: LauncherIconOption) {
        val activity = context.findActivity() ?: return
        val icon = BitmapFactory.decodeResource(activity.resources, option.previewRes) ?: return
        runCatching {
            activity.setTaskDescription(
                ActivityManager.TaskDescription(activity.getString(R.string.app_name), icon)
            )
        }
    }

    /** Finds the activity behind a Compose context, which may be a theme wrapper. */
    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    /** True until the first app start has migrated from the bootstrap launcher entry. */
    fun bootstrapEnabled(context: Context): Boolean {
        val component = aliasComponent(context.packageName, BOOTSTRAP_ALIAS)
        val state = runCatching {
            context.packageManager.getComponentEnabledSetting(component)
        }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    }
}
