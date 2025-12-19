package com.asakii.plugin.mcp.tools

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import cn.hutool.core.util.ClassUtil
import cn.hutool.core.util.ReflectUtil

/**
 * Java 插件反射帮助类
 *
 * 用于在 Java 插件可选的情况下，通过反射调用 Java 特有的 API。
 * 避免编译时直接引用 Java 插件的类，从而通过 JetBrains 插件审核。
 *
 * 使用示例:
 * ```kotlin
 * // 检查 Java 插件是否可用
 * if (JavaPluginHelper.isAvailable) {
 *     // 检查元素类型
 *     if (JavaPluginHelper.isPsiClass(element)) {
 *         // 搜索继承者
 *         val inheritors = JavaPluginHelper.searchClassInheritors(element, scope)
 *     }
 * }
 * ```
 */
object JavaPluginHelper {

    // ===== 类名常量 =====
    private object ClassNames {
        const val PSI_CLASS = "com.intellij.psi.PsiClass"
        const val PSI_METHOD = "com.intellij.psi.PsiMethod"
        const val PSI_FIELD = "com.intellij.psi.PsiField"
        const val PSI_SHORT_NAMES_CACHE = "com.intellij.psi.search.PsiShortNamesCache"
        const val CLASS_INHERITORS_SEARCH = "com.intellij.psi.search.searches.ClassInheritorsSearch"
        const val OVERRIDING_METHODS_SEARCH = "com.intellij.psi.search.searches.OverridingMethodsSearch"
    }

    // ===== 缓存 =====
    private val classCache = mutableMapOf<String, Class<*>?>()
    private val methodCache = mutableMapOf<String, java.lang.reflect.Method?>()

    /**
     * Java 插件是否可用
     *
     * 通过尝试加载 Java 插件特有的类 (PsiClass) 来检测。
     * 这种方式不依赖任何 PluginManagerCore 或 PluginId API，
     * 确保与所有 IntelliJ 版本兼容。
     */
    val isAvailable: Boolean by lazy {
        // 直接尝试加载 PsiClass，如果能加载成功则说明 Java 插件可用
        loadClass(ClassNames.PSI_CLASS) != null
    }

    // ===== 类型检查 API =====

    /**
     * 检查元素是否是 PsiClass
     */
    fun isPsiClass(element: PsiElement): Boolean {
        if (!isAvailable) return false
        val clazz = loadClass(ClassNames.PSI_CLASS) ?: return false
        return clazz.isInstance(element)
    }

    /**
     * 检查元素是否是 PsiMethod
     */
    fun isPsiMethod(element: PsiElement): Boolean {
        if (!isAvailable) return false
        val clazz = loadClass(ClassNames.PSI_METHOD) ?: return false
        return clazz.isInstance(element)
    }

    // ===== 搜索 API =====

    /**
     * 搜索类的继承者
     *
     * @param psiClass PsiClass 实例
     * @param scope 搜索范围
     * @param deep 是否深度搜索（默认 true）
     * @return 继承者列表，Java 插件不可用时返回空列表
     */
    fun searchClassInheritors(
        psiClass: PsiElement,
        scope: GlobalSearchScope,
        deep: Boolean = true
    ): List<PsiElement> {
        if (!isAvailable || !isPsiClass(psiClass)) return emptyList()

        return try {
            val searchClass = loadClass(ClassNames.CLASS_INHERITORS_SEARCH) ?: return emptyList()
            val psiClassClass = loadClass(ClassNames.PSI_CLASS) ?: return emptyList()

            val method = getMethod(
                searchClass,
                "search",
                psiClassClass,
                com.intellij.psi.search.SearchScope::class.java,
                Boolean::class.javaPrimitiveType!!
            ) ?: return emptyList()

            val query = method.invoke(null, psiClass, scope, deep)
            iterableToList(query)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 搜索覆盖的方法
     *
     * @param psiMethod PsiMethod 实例
     * @param scope 搜索范围
     * @param deep 是否深度搜索（默认 true）
     * @return 覆盖方法列表，Java 插件不可用时返回空列表
     */
    fun searchOverridingMethods(
        psiMethod: PsiElement,
        scope: GlobalSearchScope,
        deep: Boolean = true
    ): List<PsiElement> {
        if (!isAvailable || !isPsiMethod(psiMethod)) return emptyList()

        return try {
            val searchClass = loadClass(ClassNames.OVERRIDING_METHODS_SEARCH) ?: return emptyList()
            val psiMethodClass = loadClass(ClassNames.PSI_METHOD) ?: return emptyList()

            val method = getMethod(
                searchClass,
                "search",
                psiMethodClass,
                com.intellij.psi.search.SearchScope::class.java,
                Boolean::class.javaPrimitiveType!!
            ) ?: return emptyList()

            val query = method.invoke(null, psiMethod, scope, deep)
            iterableToList(query)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ===== PsiShortNamesCache API =====

    /**
     * 获取 PsiShortNamesCache 实例（反射调用）
     *
     * @param project 项目
     * @return PsiShortNamesCache 实例，Java 插件不可用时返回 null
     */
    fun getShortNamesCache(project: Project): Any? {
        if (!isAvailable) return null
        return try {
            val cacheClass = loadClass(ClassNames.PSI_SHORT_NAMES_CACHE) ?: return null
            val getInstance = cacheClass.getMethod("getInstance", Project::class.java)
            getInstance.invoke(null, project)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取所有类名（反射调用）
     */
    fun getAllClassNames(cache: Any?): Array<String> {
        if (cache == null) return emptyArray()
        return try {
            val method = cache.javaClass.getMethod("getAllClassNames")
            @Suppress("UNCHECKED_CAST")
            method.invoke(cache) as? Array<String> ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }
    }

    /**
     * 根据名称获取类（反射调用）
     */
    fun getClassesByName(cache: Any?, name: String, scope: GlobalSearchScope): List<PsiElement> {
        if (cache == null) return emptyList()
        return try {
            val method = cache.javaClass.getMethod("getClassesByName", String::class.java, GlobalSearchScope::class.java)
            val result = method.invoke(cache, name, scope)
            @Suppress("UNCHECKED_CAST")
            (result as? Array<*>)?.filterIsInstance<PsiElement>() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 获取所有方法名（反射调用）
     */
    fun getAllMethodNames(cache: Any?): Array<String> {
        if (cache == null) return emptyArray()
        return try {
            val method = cache.javaClass.getMethod("getAllMethodNames")
            @Suppress("UNCHECKED_CAST")
            method.invoke(cache) as? Array<String> ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }
    }

    /**
     * 根据名称获取方法（反射调用）
     */
    fun getMethodsByName(cache: Any?, name: String, scope: GlobalSearchScope): List<PsiElement> {
        if (cache == null) return emptyList()
        return try {
            val method = cache.javaClass.getMethod("getMethodsByName", String::class.java, GlobalSearchScope::class.java)
            val result = method.invoke(cache, name, scope)
            @Suppress("UNCHECKED_CAST")
            (result as? Array<*>)?.filterIsInstance<PsiElement>() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 获取所有字段名（反射调用）
     */
    fun getAllFieldNames(cache: Any?): Array<String> {
        if (cache == null) return emptyArray()
        return try {
            val method = cache.javaClass.getMethod("getAllFieldNames")
            @Suppress("UNCHECKED_CAST")
            method.invoke(cache) as? Array<String> ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }
    }

    /**
     * 根据名称获取字段（反射调用）
     */
    fun getFieldsByName(cache: Any?, name: String, scope: GlobalSearchScope): List<PsiElement> {
        if (cache == null) return emptyList()
        return try {
            val method = cache.javaClass.getMethod("getFieldsByName", String::class.java, GlobalSearchScope::class.java)
            val result = method.invoke(cache, name, scope)
            @Suppress("UNCHECKED_CAST")
            (result as? Array<*>)?.filterIsInstance<PsiElement>() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ===== 工具方法 =====

    /**
     * 加载类（带缓存）
     */
    private fun loadClass(className: String): Class<*>? {
        return classCache.getOrPut(className) {
            try {
                ClassUtil.loadClass<Any>(className)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 获取方法（带缓存）
     */
    private fun getMethod(
        clazz: Class<*>,
        methodName: String,
        vararg paramTypes: Class<*>
    ): java.lang.reflect.Method? {
        val cacheKey = "${clazz.name}.$methodName(${paramTypes.joinToString(",") { it.name }})"
        return methodCache.getOrPut(cacheKey) {
            try {
                ReflectUtil.getMethod(clazz, methodName, *paramTypes)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 将 Iterable 转换为 PsiElement 列表
     */
    private fun iterableToList(query: Any?): List<PsiElement> {
        if (query == null) return emptyList()
        val result = mutableListOf<PsiElement>()
        if (query is Iterable<*>) {
            for (item in query) {
                if (item is PsiElement) {
                    result.add(item)
                }
            }
        }
        return result
    }
}
