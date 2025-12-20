package com.asakii.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope

/**
 * 语言分析服务接口
 *
 * 提供跨语言的代码分析功能。使用 IntelliJ 的服务机制实现可选依赖：
 * - 默认实现 (NoopLanguageAnalysisService): 当 Java 插件不可用时使用，返回空结果
 * - Java 实现 (JavaLanguageAnalysisService): 当 Java 插件可用时自动覆盖，提供完整功能
 *
 * 通过 plugin.xml 和 plugin-withJava.xml 的 overrides="true" 机制自动切换实现。
 *
 * 使用方式：
 * ```kotlin
 * val service = LanguageAnalysisService.getInstance(project)
 * if (service.isAvailable()) {
 *     val inheritors = service.findClassInheritors(psiClass, scope)
 * }
 * ```
 */
interface LanguageAnalysisService {

    /**
     * 检查服务是否可用
     * @return true 如果语言分析功能可用（Java 插件已加载）
     */
    fun isAvailable(): Boolean

    /**
     * 检查元素是否是类（PsiClass）
     * @param element PSI 元素
     * @return true 如果是类
     */
    fun isClass(element: PsiElement): Boolean

    /**
     * 检查元素是否是方法（PsiMethod）
     * @param element PSI 元素
     * @return true 如果是方法
     */
    fun isMethod(element: PsiElement): Boolean

    /**
     * 搜索类的继承者
     * @param psiClass 类元素（必须是 PsiClass）
     * @param scope 搜索范围
     * @param deep 是否深度搜索（包含间接继承）
     * @return 继承者列表
     */
    fun findClassInheritors(
        psiClass: PsiElement,
        scope: GlobalSearchScope,
        deep: Boolean = true
    ): List<PsiElement>

    /**
     * 搜索覆盖的方法
     * @param psiMethod 方法元素（必须是 PsiMethod）
     * @param scope 搜索范围
     * @param deep 是否深度搜索
     * @return 覆盖方法列表
     */
    fun findOverridingMethods(
        psiMethod: PsiElement,
        scope: GlobalSearchScope,
        deep: Boolean = true
    ): List<PsiElement>

    /**
     * 获取 PsiShortNamesCache 实例
     * @return 缓存实例，不可用时返回 null
     */
    fun getShortNamesCache(): Any?

    /**
     * 获取所有类名
     * @param cache PsiShortNamesCache 实例
     * @return 类名数组
     */
    fun getAllClassNames(cache: Any?): Array<String>

    /**
     * 根据名称获取类
     * @param cache PsiShortNamesCache 实例
     * @param name 类名
     * @param scope 搜索范围
     * @return 匹配的类列表
     */
    fun getClassesByName(cache: Any?, name: String, scope: GlobalSearchScope): List<PsiElement>

    /**
     * 获取所有方法名
     * @param cache PsiShortNamesCache 实例
     * @return 方法名数组
     */
    fun getAllMethodNames(cache: Any?): Array<String>

    /**
     * 根据名称获取方法
     * @param cache PsiShortNamesCache 实例
     * @param name 方法名
     * @param scope 搜索范围
     * @return 匹配的方法列表
     */
    fun getMethodsByName(cache: Any?, name: String, scope: GlobalSearchScope): List<PsiElement>

    /**
     * 获取所有字段名
     * @param cache PsiShortNamesCache 实例
     * @return 字段名数组
     */
    fun getAllFieldNames(cache: Any?): Array<String>

    /**
     * 根据名称获取字段
     * @param cache PsiShortNamesCache 实例
     * @param name 字段名
     * @param scope 搜索范围
     * @return 匹配的字段列表
     */
    fun getFieldsByName(cache: Any?, name: String, scope: GlobalSearchScope): List<PsiElement>

    companion object {
        /**
         * 获取服务实例
         * IDE 会自动选择可用的实现（Java 或 Noop）
         */
        fun getInstance(project: Project): LanguageAnalysisService {
            return project.service<LanguageAnalysisService>()
        }
    }
}
