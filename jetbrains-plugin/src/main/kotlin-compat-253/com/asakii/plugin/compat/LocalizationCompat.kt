package com.asakii.plugin.compat

import com.intellij.l10n.LocalizationUtil
import java.util.Locale

/**
 * 本地化兼容层 - 适用于 2025.3+
 *
 * 使用 IntelliJ 平台的 LocalizationUtil API
 */
object LocalizationCompat {

    /**
     * 获取当前语言环境
     * @return Locale 对象
     */
    fun getLocale(): Locale {
        return try {
            LocalizationUtil.getLocale(true)
        } catch (e: Exception) {
            Locale.getDefault()
        }
    }
}
