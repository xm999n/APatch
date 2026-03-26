package me.bmax.apatch.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.MutableLiveData
import me.bmax.apatch.APApplication
import me.bmax.apatch.ui.webui.MonetColorsProvider

@Composable
private fun SystemBarStyle(
    darkMode: Boolean,
    statusBarScrim: Color = Color.Transparent,
    navigationBarScrim: Color = Color.Transparent
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    SideEffect {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                statusBarScrim.toArgb(),
                statusBarScrim.toArgb(),
            ) { darkMode }, navigationBarStyle = when {
                darkMode -> SystemBarStyle.dark(
                    navigationBarScrim.toArgb()
                )

                else -> SystemBarStyle.light(
                    navigationBarScrim.toArgb(),
                    navigationBarScrim.toArgb(),
                )
            }
        )
    }
}

val refreshTheme = MutableLiveData(false)

@Composable
fun APatchTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = APApplication.sharedPreferences

    var darkThemeFollowSys by remember {
        mutableStateOf(
            prefs.getBoolean(
                "night_mode_follow_sys",
                true
            )
        )
    }
    var nightModeEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                "night_mode_enabled",
                false
            )
        )
    }
    // Dynamic color is available on Android 12+, and custom 1t!
    var dynamicColor by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) prefs.getBoolean(
                "use_system_color_theme",
                false
            ) else false
        )
    }
    var customColorScheme by remember { mutableStateOf(prefs.getString("custom_color", "blue")) }
    var amoledMode by remember { mutableStateOf(prefs.getBoolean("amoled_mode", false)) }

    val refreshThemeObserver by refreshTheme.observeAsState(false)
    if (refreshThemeObserver == true) {
        darkThemeFollowSys = prefs.getBoolean("night_mode_follow_sys", true)
        nightModeEnabled = prefs.getBoolean("night_mode_enabled", false)
        dynamicColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) prefs.getBoolean(
            "use_system_color_theme",
            false
        ) else false
        customColorScheme = prefs.getString("custom_color", "blue")
        amoledMode = prefs.getBoolean("amoled_mode", false)
        refreshTheme.postValue(false)
    }

    val darkTheme = if (darkThemeFollowSys) {
        isSystemInDarkTheme()
    } else {
        nightModeEnabled
    }

    val colorScheme = if (!dynamicColor) {
        if (darkTheme) {
            when (customColorScheme) {
                "amber" -> DarkAmberTheme
                "blue_grey" -> DarkBlueGreyTheme
                "blue" -> DarkBlueTheme
                "brown" -> DarkBrownTheme
                "cyan" -> DarkCyanTheme
                "deep_orange" -> DarkDeepOrangeTheme
                "deep_purple" -> DarkDeepPurpleTheme
                "green" -> DarkGreenTheme
                "indigo" -> DarkIndigoTheme
                "light_blue" -> DarkLightBlueTheme
                "light_green" -> DarkLightGreenTheme
                "lime" -> DarkLimeTheme
                "orange" -> DarkOrangeTheme
                "pink" -> DarkPinkTheme
                "purple" -> DarkPurpleTheme
                "red" -> DarkRedTheme
                "sakura" -> DarkSakuraTheme
                "teal" -> DarkTealTheme
                "yellow" -> DarkYellowTheme
                else -> DarkBlueTheme
            }
        } else {
            when (customColorScheme) {
                "amber" -> LightAmberTheme
                "blue_grey" -> LightBlueGreyTheme
                "blue" -> LightBlueTheme
                "brown" -> LightBrownTheme
                "cyan" -> LightCyanTheme
                "deep_orange" -> LightDeepOrangeTheme
                "deep_purple" -> LightDeepPurpleTheme
                "green" -> LightGreenTheme
                "indigo" -> LightIndigoTheme
                "light_blue" -> LightLightBlueTheme
                "light_green" -> LightLightGreenTheme
                "lime" -> LightLimeTheme
                "orange" -> LightOrangeTheme
                "pink" -> LightPinkTheme
                "purple" -> LightPurpleTheme
                "red" -> LightRedTheme
                "sakura" -> LightSakuraTheme
                "teal" -> LightTealTheme
                "yellow" -> LightYellowTheme
                else -> LightBlueTheme
            }
        }
    } else {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> DarkBlueTheme
            else -> LightBlueTheme
        }
    }

    val tunedColorScheme = colorScheme.withUnifiedSurfaces(darkTheme = darkTheme)

    // AMOLED: override surface/background family to pure black when dark mode is active
    val finalColorScheme = if (amoledMode && darkTheme) {
        tunedColorScheme.copy(
            background = Color.Black,
            onBackground = Color(0xFFE5E7EC),
            surface = Color.Black,
            onSurface = Color(0xFFE5E7EC),
            surfaceVariant = Color(0xFF1C1C1C),
            onSurfaceVariant = Color(0xFFBCC2CC),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0D0D0D),
            surfaceContainer = Color(0xFF141414),
            surfaceContainerHigh = Color(0xFF1C1C1C),
            surfaceContainerHighest = Color(0xFF242424),
        )
    } else {
        tunedColorScheme
    }

    SystemBarStyle(
        darkMode = darkTheme,
        statusBarScrim = finalColorScheme.surface,
        navigationBarScrim = finalColorScheme.surface,
    )

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = Typography,
        content = {
            MonetColorsProvider.UpdateCss()
            content()
        }
    )
}

private fun ColorScheme.withUnifiedSurfaces(darkTheme: Boolean): ColorScheme {
    return if (darkTheme) {
        copy(
            background = Color(0xFF11151C),
            onBackground = Color(0xFFE8ECF3),
            surface = Color(0xFF11151C),
            onSurface = Color(0xFFE8ECF3),
            surfaceVariant = Color(0xFF2A303A),
            onSurfaceVariant = Color(0xFFC0C7D2),
            outline = Color(0xFF8A93A0),
            outlineVariant = Color(0xFF3C4450),
            inverseSurface = Color(0xFFE8ECF3),
            inverseOnSurface = Color(0xFF1B2027),
            surfaceContainerLowest = Color(0xFF0B0E13),
            surfaceContainerLow = Color(0xFF141923),
            surfaceContainer = Color(0xFF1A212C),
            surfaceContainerHigh = Color(0xFF222A36),
            surfaceContainerHighest = Color(0xFF2A3240),
        )
    } else {
        copy(
            background = Color(0xFFF6F8FC),
            onBackground = Color(0xFF171C24),
            surface = Color(0xFFFBFCFF),
            onSurface = Color(0xFF171C24),
            surfaceVariant = Color(0xFFE1E7F1),
            onSurfaceVariant = Color(0xFF434C59),
            outline = Color(0xFF6A7380),
            outlineVariant = Color(0xFFC1C9D6),
            inverseSurface = Color(0xFF2A313B),
            inverseOnSurface = Color(0xFFF2F5FA),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF4F7FD),
            surfaceContainer = Color(0xFFEEF3FB),
            surfaceContainerHigh = Color(0xFFE8EEF8),
            surfaceContainerHighest = Color(0xFFE2E9F5),
        )
    }
}
