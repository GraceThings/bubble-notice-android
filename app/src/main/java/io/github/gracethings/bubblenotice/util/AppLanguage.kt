/*
 * Copyright (C) 2026 Grace Chan <velviagris@outlook.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.gracethings.bubblenotice.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.StringRes
import io.github.gracethings.bubblenotice.R
import java.util.Locale

// 原生语言选项 / Native language options.
enum class AppLanguage(
    val tag: String,
    @StringRes val labelRes: Int
) {
    SYSTEM("", R.string.language_system),
    ENGLISH("en", R.string.language_english),
    JAPANESE("ja", R.string.language_japanese),
    KOREAN("ko", R.string.language_korean),
    SIMPLIFIED_CHINESE("zh-CN", R.string.language_chinese_simplified),
    TRADITIONAL_CHINESE("zh-TW", R.string.language_chinese_taiwan),
    HONG_KONG_CHINESE("zh-HK", R.string.language_chinese_hong_kong),
    MACAU_CHINESE("zh-MO", R.string.language_chinese_macau);

    companion object {
        fun fromTag(tag: String?): AppLanguage {
            return values().firstOrNull { it.tag == tag } ?: SYSTEM
        }
    }
}

// 帮助调用者在任何 API 版本上包装一个需要注入语言的 context / Helps callers wrap a Context with a chosen language.
object LanguageSettings {
    private const val PREFS_NAME = "bubble_prefs"
    private const val KEY_LANGUAGE = "app_language"

    fun getSelected(context: Context): AppLanguage {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppLanguage.fromTag(prefs.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.tag))
    }

    // 选择语言并让界面立即重建 / Resolve the chosen language and let the UI rebuild immediately.
    fun setSelected(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, language.tag).apply()
        wrap(context, language)
    }

    // 包装一个使用目标语言的 context / Wrap a context with the target language.
    fun wrap(context: Context, language: AppLanguage): Context {
        if (language.tag.isBlank()) return context
        val locale = Locale.forLanguageTag(language.tag)
        Locale.setDefault(locale)
        val configuration = Configuration()
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }

    // 应用语言并触发重建 / Apply the language and rebuild the UI.
    fun apply(context: Context, language: AppLanguage) {
        val preferred = context as? android.app.Activity
        wrap(context, language)
        preferred?.recreate()
    }
}
