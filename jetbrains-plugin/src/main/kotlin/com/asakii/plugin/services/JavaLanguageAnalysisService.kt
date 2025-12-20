package com.asakii.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch

/**
 * 语言分析服务的 Java 实现
 *
 * 当 Java 插件可用时使用此实现，提供完整的 Java 代码分析功能。
 * 直接使用 Java 插件的 API，无需反射。
 *
 * 注册方式：在 plugin-withJava.xml 中注册，overrides="true"
 * 这样当 Java 插件可用时，IDE 会自动使用此实现覆盖默认的 NoopLanguageAnalysisService。
 */
@Service(Service.Level.PROJECT)
class JavaLanguageAnalysisService(private val project: Project) : LanguageAnalysisService {

    override fun isAvailable(): Boolean = true

    override fun isClass(element: PsiElement): Boolean = element is PsiClass

    override fun isMethod(element: PsiElement): Boolean = element is PsiMethod

    override fun findClassInheritors(
        psiClass: PsiElement,
        scope: GlobalSearchScope,
        deep: Boolean
    ): List<PsiElement> {
        if (psiClass !is PsiClass) return emptyList()
        return try {
            ClassInheritorsSearch.search(psiClass, scope, deep).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun findOverridingMethods(
        psiMethod: PsiElement,
        scope: GlobalSearchScope,
        deep: Boolean
    ): List<PsiElement> {
        if (psiMethod !is PsiMethod) return emptyList()
        return try {
            OverridingMethodsSearch.search(psiMethod, scope, deep).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun getShortNamesCache(): Any? {
        return try {
            PsiShortNamesCache.getInstance(project)
        } catch (e: Exception) {
            null
        }
    }

    override fun getAllClassNames(cache: Any?): Array<String> {
        return try {
            (cache as? PsiShortNamesCache)?.allClassNames ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }
    }

    override fun getClassesByName(cache: Any?, name: String, scope: GlobalSearchScope): List<PsiElement> {
        return try {
            (cache as? PsiShortNamesCache)?.getClassesByName(name, scope)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun getAllMethodNames(cache: Any?): Array<String> {
        return try {
            (cache as? PsiShortNamesCache)?.allMethodNames ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }
    }

    override fun getMethodsByName(cache: Any?, name: String, scope: GlobalSearchScope): List<PsiElement> {
        return try {
            (cache as? PsiShortNamesCache)?.getMethodsByName(name, scope)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun getAllFieldNames(cache: Any?): Array<String> {
        return try {
            (cache as? PsiShortNamesCache)?.allFieldNames ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }
    }

    override fun getFieldsByName(cache: Any?, name: String, scope: GlobalSearchScope): List<PsiElement> {
        return try {
            (cache as? PsiShortNamesCache)?.getFieldsByName(name, scope)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
