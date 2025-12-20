package com.asakii.plugin.compat

import java.util.Locale

/**
 * 本地化兼容层 - 适用于 2024.1 ~ 2025.2
 *
 * 在这些版本中，com.intellij.l10n.LocalizationUtil 不存在
 * 使用 Java 标准 Locale 作为替代
 */
object LocalizationCompat {

    /**
     * 获取当前语言环境
     * @return Locale 对象
     */
    fun getLocale(): Locale {
        return Locale.getDefault()
    }
}
