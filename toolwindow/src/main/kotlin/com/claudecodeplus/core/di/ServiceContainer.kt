package com.claudecodeplus.core.di

import kotlin.reflect.KClass

/**
 * 简单的依赖注入容器
 * 用于管理服务实例的创建和依赖关系
 */
class ServiceContainer {
    
    private val singletons = mutableMapOf<KClass<*>, Any>()
    private val factories = mutableMapOf<KClass<*>, () -> Any>()
    
    /**
     * 注册单例服务
     */
    inline fun <reified T : Any> singleton(noinline factory: () -> T) {
        singleton(T::class, factory)
    }
    
    /**
     * 注册单例服务
     */
    fun <T : Any> singleton(clazz: KClass<T>, factory: () -> T) {
        factories[clazz] = factory
    }
    
    /**
     * 注册工厂服务（每次调用创建新实例）
     */
    inline fun <reified T : Any> factory(noinline factory: () -> T) {
        factory(T::class, factory)
    }
    
    /**
     * 注册工厂服务
     */
    fun <T : Any> factory(clazz: KClass<T>, factory: () -> T) {
        factories[clazz] = factory
    }
    
    /**
     * 获取服务实例
     */
    inline fun <reified T : Any> get(): T {
        return get(T::class)
    }
    
    /**
     * 获取服务实例
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(clazz: KClass<T>): T {
        // 先检查是否已有单例实例
        singletons[clazz]?.let { 
            return it as T 
        }
        
        // 从工厂创建实例
        val factory = factories[clazz] 
            ?: throw IllegalArgumentException("未找到 ${clazz.simpleName} 的注册信息")
        
        val instance = factory() as T
        
        // 如果是单例，缓存实例
        if (clazz in factories) {
            singletons[clazz] = instance
        }
        
        return instance
    }
    
    /**
     * 检查服务是否已注册
     */
    fun <T : Any> isRegistered(clazz: KClass<T>): Boolean {
        return clazz in factories
    }
    
    /**
     * 清理所有服务实例
     */
    fun clear() {
        singletons.clear()
        factories.clear()
    }
    
    companion object {
        /**
         * 全局服务容器实例
         */
        val instance = ServiceContainer()
    }
}

/**
 * 依赖注入DSL
 */
inline fun <reified T : Any> inject(): Lazy<T> {
    return lazy { ServiceContainer.instance.get<T>() }
}

/**
 * 立即获取服务实例
 */
inline fun <reified T : Any> resolve(): T {
    return ServiceContainer.instance.get<T>()
}