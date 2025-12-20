package com.asakii.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope

/**
 * 语言分析服务的默认空实现
 *
 * 当 Java 插件不可用时使用此实现。所有方法返回空结果或 false。
 * 这确保了插件在 WebStorm、GoLand、PyCharm 等非 Java IDE 中也能正常运行，
 * 只是 Java 特有的功能（如类继承搜索）不可用。
 *
 * 注册方式：在主 plugin.xml 中注册，overrides="false"
 */
@Service(Service.Level.PROJECT)
class NoopLanguageAnalysisService(private val project: Project) : LanguageAnalysisService {

    override fun isAvailable(): Boolean = false

    override fun isClass(element: PsiElement): Boolean = false

    override fun isMethod(element: PsiElement): Boolean = false

    override fun findClassInheritors(
        psiClass: PsiElement,
        scope: GlobalSearchScope,
        deep: Boolean
    ): List<PsiElement> = emptyList()

    override fun findOverridingMethods(
        psiMethod: PsiElement,
        scope: GlobalSearchScope,
        deep: Boolean
    ): List<PsiElement> = emptyList()

    override fun getShortNamesCache(): Any? = null

    override fun getAllClassNames(cache: Any?): Array<String> = emptyArray()

    override fun getClassesByName(cache: Any?, name: String, scope: GlobalSearchScope): List<PsiElement> = emptyList()

    override fun getAllMethodNames(cache: Any?): Array<String> = emptyArray()

    override fun getMethodsByName(cache: Any?, name: String, scope: GlobalSearchScope): List<PsiElement> = emptyList()

    override fun getAllFieldNames(cache: Any?): Array<String> = emptyArray()

    override fun getFieldsByName(cache: Any?, name: String, scope: GlobalSearchScope): List<PsiElement> = emptyList()
}
