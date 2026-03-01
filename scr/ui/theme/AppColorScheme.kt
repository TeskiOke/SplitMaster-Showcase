package com.yourname.splitmastersimple.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.yourname.splitmastersimple.data.ThemeManager

object AppColorScheme {
    // Функция для создания цветовой схемы на основе базовой темы и акцентного цвета
    fun createScheme(
        isDark: Boolean,
        baseTheme: ThemeManager.BaseTheme,
        accentColor: Color
    ): ColorScheme {
        val (background, onBackground, surface, onSurface) = when (baseTheme) {
            ThemeManager.BaseTheme.BLACK -> {
                if (isDark) {
                    // Черный фон, белый текст
                    Quad(Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFF121212), Color(0xFFFFFFFF))
                } else {
                    // Светлая версия: очень темно-серый фон, белый текст
                    Quad(Color(0xFF1A1A1A), Color(0xFFFFFFFF), Color(0xFF2C2C2C), Color(0xFFFFFFFF))
                }
            }
            ThemeManager.BaseTheme.WHITE -> {
                // Всегда возвращаем белую тему, даже если в системе включен Dark Mode
                // Это требование пользователя: "выбрано белое - должно быть белое"
                Quad(Color(0xFFFFFFFF), Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFF000000))
            }
        }
        
        // Акцентный цвет используется для primary, secondary, tertiary
        val primary = accentColor
        val onPrimary = if (isDark) Color.Black else Color.White
        
        // Создаем варианты акцентного цвета
        val primaryContainer = if (isDark) {
            Color(accentColor.red * 0.2f, accentColor.green * 0.2f, accentColor.blue * 0.2f)
        } else {
            Color(accentColor.red * 0.1f + 0.9f, accentColor.green * 0.1f + 0.9f, accentColor.blue * 0.1f + 0.9f)
        }
        val onPrimaryContainer = accentColor
        
        val secondary = if (isDark) {
            Color(accentColor.red * 0.8f, accentColor.green * 0.8f, accentColor.blue * 0.8f)
        } else {
            Color(accentColor.red * 0.7f, accentColor.green * 0.7f, accentColor.blue * 0.7f)
        }
        val onSecondary = if (isDark) Color.Black else Color.White
        
        val tertiary = if (isDark) {
            Color(accentColor.red * 0.6f, accentColor.green * 0.6f, accentColor.blue * 0.6f)
        } else {
            Color(accentColor.red * 0.5f, accentColor.green * 0.5f, accentColor.blue * 0.5f)
        }
        val onTertiary = if (isDark) Color.Black else Color.White
        
        // Ошибки
        val error = if (isDark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A)
        val onError = if (isDark) Color(0xFF690005) else Color(0xFFFFFFFF)
        val errorContainer = if (isDark) Color(0xFF93000A) else Color(0xFFFFDAD6)
        val onErrorContainer = if (isDark) Color(0xFFFFDAD6) else Color(0xFF410002)
        
        // Поверхности - вычисляем правильно для каждой базовой темы
        val surfaceVariant = when (baseTheme) {
            ThemeManager.BaseTheme.BLACK -> {
                if (isDark) Color(0xFF1A1A1A) else Color(0xFF2C2C2C)
            }
            ThemeManager.BaseTheme.WHITE -> {
                Color(0xFFFFFFFF)
            }
        }
        
        val onSurfaceVariant = when (baseTheme) {
            ThemeManager.BaseTheme.BLACK -> {
                if (isDark) Color(0xFFB0B0B0) else Color(0xFFB0B0B0)
            }
            ThemeManager.BaseTheme.WHITE -> {
                Color(0xFF616161)
            }
        }
        
        // Обводка на основе акцентного цвета
        // Для темной темы: чуть белее белого чем акцентный цвет
        // Для светлой темы: чуть темнее чем акцентный цвет
        val outline = if (isDark) {
            // Темная тема: делаем цвет светлее акцентного (ближе к белому)
            // Смешиваем акцентный цвет с белым (70% белого, 30% акцентного)
            Color(
                red = (accentColor.red * 0.3f + 1.0f * 0.7f).coerceIn(0f, 1f),
                green = (accentColor.green * 0.3f + 1.0f * 0.7f).coerceIn(0f, 1f),
                blue = (accentColor.blue * 0.3f + 1.0f * 0.7f).coerceIn(0f, 1f)
            )
        } else {
            // Светлая тема: делаем цвет темнее акцентного
            // Смешиваем акцентный цвет с черным (70% акцентного, 30% черного)
            Color(
                red = (accentColor.red * 0.7f).coerceIn(0f, 1f),
                green = (accentColor.green * 0.7f).coerceIn(0f, 1f),
                blue = (accentColor.blue * 0.7f).coerceIn(0f, 1f)
            )
        }
        
        val outlineVariant = when (baseTheme) {
            ThemeManager.BaseTheme.BLACK -> {
                if (isDark) Color(0xFF333333) else Color(0xFF333333)
            }
            ThemeManager.BaseTheme.WHITE -> {
                Color(0xFFE0E0E0)
            }
        }
        
        val inverseSurface = onBackground
        val inverseOnSurface = background
        val inversePrimary = if (isDark) accentColor else accentColor
        
        return if (isDark) {
            darkColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                secondary = secondary,
                onSecondary = onSecondary,
                tertiary = tertiary,
                onTertiary = onTertiary,
                error = error,
                onError = onError,
                errorContainer = errorContainer,
                onErrorContainer = onErrorContainer,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = onSurfaceVariant,
                outline = outline,
                outlineVariant = outlineVariant,
                scrim = Color.Transparent,
                inverseSurface = inverseSurface,
                inverseOnSurface = inverseOnSurface,
                inversePrimary = inversePrimary,
                surfaceTint = primary
            )
        } else {
            lightColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                secondary = secondary,
                onSecondary = onSecondary,
                tertiary = tertiary,
                onTertiary = onTertiary,
                error = error,
                onError = onError,
                errorContainer = errorContainer,
                onErrorContainer = onErrorContainer,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = onSurfaceVariant,
                outline = outline,
                outlineVariant = outlineVariant,
                scrim = Color.Transparent,
                inverseSurface = inverseSurface,
                inverseOnSurface = inverseOnSurface,
                inversePrimary = inversePrimary,
                surfaceTint = primary
            )
        }
    }
    
    private data class Quad(
        val first: Color,
        val second: Color,
        val third: Color,
        val fourth: Color
    )
}
