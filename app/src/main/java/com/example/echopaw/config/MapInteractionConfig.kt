package com.example.echopaw.config

import android.content.Context
import android.content.SharedPreferences

/**
 * 地图交互功能配置管理器
 * 
 * 统一管理地图交互相关的所有可配置参数，包括：
 * 1. 防抖机制配置
 * 2. 距离阈值配置
 * 3. 请求去重配置
 * 4. 自动重试配置
 * 5. 性能监控配置
 */
class MapInteractionConfig(private val context: Context? = null) {
    
    companion object {
        @Volatile
        private var INSTANCE: MapInteractionConfig? = null
        
        private const val PREFS_NAME = "map_interaction_config"
        
        // 默认配置值
        private const val DEFAULT_DEBOUNCE_DELAY = 400L
        private const val DEFAULT_DISTANCE_THRESHOLD_PX = 75
        private const val DEFAULT_CACHE_EXPIRE_TIME = 5 * 60 * 1000L // 5分钟
        private const val DEFAULT_MAX_RETRY_COUNT = 3
        private const val DEFAULT_INITIAL_RETRY_DELAY = 1000L
        private const val DEFAULT_RETRY_MULTIPLIER = 2.0
        private const val DEFAULT_MAX_RETRY_DELAY = 10000L
        private const val DEFAULT_PERFORMANCE_MONITORING_ENABLED = true
        private const val DEFAULT_REQUEST_TIMEOUT = 10000L
        private const val DEFAULT_BATCH_UPDATE_DELAY = 100L
        
        fun getInstance(context: Context): MapInteractionConfig {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MapInteractionConfig(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences? = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // 防抖机制配置
    /**
     * 防抖延迟时间（毫秒）
     * 地图操作结束后延迟多久触发数据请求
     */
    var debounceDelay: Long
        get() = prefs?.getLong("debounce_delay", DEFAULT_DEBOUNCE_DELAY) ?: DEFAULT_DEBOUNCE_DELAY
        set(value) = prefs?.edit()?.putLong("debounce_delay", value)?.apply() ?: Unit
    
    // 距离阈值配置
    /**
     * 最小移动距离阈值（像素）
     * 仅当移动距离超过此阈值时才触发更新
     */
    var distanceThresholdPx: Int
        get() = prefs?.getInt("distance_threshold_px", DEFAULT_DISTANCE_THRESHOLD_PX) ?: DEFAULT_DISTANCE_THRESHOLD_PX
        set(value) = prefs?.edit()?.putInt("distance_threshold_px", value)?.apply() ?: Unit
    
    // 请求去重配置
    /**
     * 请求缓存过期时间（毫秒）
     * 相同区域的请求在此时间内会被去重
     */
    var cacheExpireTime: Long
        get() = prefs?.getLong("cache_expire_time", DEFAULT_CACHE_EXPIRE_TIME) ?: DEFAULT_CACHE_EXPIRE_TIME
        set(value) = prefs?.edit()?.putLong("cache_expire_time", value)?.apply() ?: Unit
    
    // 自动重试配置
    /**
     * 最大重试次数
     */
    var maxRetryCount: Int
        get() = prefs?.getInt("max_retry_count", DEFAULT_MAX_RETRY_COUNT) ?: DEFAULT_MAX_RETRY_COUNT
        set(value) = prefs?.edit()?.putInt("max_retry_count", value)?.apply() ?: Unit
    
    /**
     * 初始重试延迟时间（毫秒）
     */
    var initialRetryDelay: Long
        get() = prefs?.getLong("initial_retry_delay", DEFAULT_INITIAL_RETRY_DELAY) ?: DEFAULT_INITIAL_RETRY_DELAY
        set(value) = prefs?.edit()?.putLong("initial_retry_delay", value)?.apply() ?: Unit
    
    /**
     * 重试延迟倍数
     * 每次重试的延迟时间 = 上次延迟时间 * 倍数
     */
    var retryMultiplier: Double
        get() = prefs?.getFloat("retry_multiplier", DEFAULT_RETRY_MULTIPLIER.toFloat())?.toDouble() ?: DEFAULT_RETRY_MULTIPLIER
        set(value) = prefs?.edit()?.putFloat("retry_multiplier", value.toFloat())?.apply() ?: Unit
    
    /**
     * 最大重试延迟时间（毫秒）
     */
    var maxRetryDelay: Long
        get() = prefs?.getLong("max_retry_delay", DEFAULT_MAX_RETRY_DELAY) ?: DEFAULT_MAX_RETRY_DELAY
        set(value) = prefs?.edit()?.putLong("max_retry_delay", value)?.apply() ?: Unit
    
    // 性能监控配置
    /**
     * 是否启用性能监控
     */
    var performanceMonitoringEnabled: Boolean
        get() = prefs?.getBoolean("performance_monitoring_enabled", DEFAULT_PERFORMANCE_MONITORING_ENABLED) ?: DEFAULT_PERFORMANCE_MONITORING_ENABLED
        set(value) = prefs?.edit()?.putBoolean("performance_monitoring_enabled", value)?.apply() ?: Unit
    
    // 网络请求配置
    /**
     * 请求超时时间（毫秒）
     */
    var requestTimeout: Long
        get() = prefs?.getLong("request_timeout", DEFAULT_REQUEST_TIMEOUT) ?: DEFAULT_REQUEST_TIMEOUT
        set(value) = prefs?.edit()?.putLong("request_timeout", value)?.apply() ?: Unit
    
    // 批量更新配置
    /**
     * 批量更新延迟时间（毫秒）
     * 多个更新操作在此时间内会被合并为一次操作
     */
    var batchUpdateDelay: Long
        get() = prefs?.getLong("batch_update_delay", DEFAULT_BATCH_UPDATE_DELAY) ?: DEFAULT_BATCH_UPDATE_DELAY
        set(value) = prefs?.edit()?.putLong("batch_update_delay", value)?.apply() ?: Unit
    
    /**
     * 重置所有配置为默认值
     */
    fun resetToDefaults() {
        prefs?.edit()?.clear()?.apply()
    }
    
    /**
     * 获取配置摘要信息
     */
    fun getConfigSummary(): String {
        return """
            地图交互配置摘要:
            - 防抖延迟: ${debounceDelay}ms
            - 距离阈值: ${distanceThresholdPx}px
            - 缓存过期: ${cacheExpireTime}ms
            - 最大重试: ${maxRetryCount}次
            - 初始重试延迟: ${initialRetryDelay}ms
            - 重试倍数: ${retryMultiplier}
            - 最大重试延迟: ${maxRetryDelay}ms
            - 性能监控: ${if (performanceMonitoringEnabled) "启用" else "禁用"}
            - 请求超时: ${requestTimeout}ms
            - 批量更新延迟: ${batchUpdateDelay}ms
        """.trimIndent()
    }
    
    /**
     * 验证配置参数的有效性
     */
    fun validateConfig(): List<String> {
        val errors = mutableListOf<String>()
        
        if (debounceDelay < 0) {
            errors.add("防抖延迟时间不能为负数")
        }
        
        if (distanceThresholdPx < 0) {
            errors.add("距离阈值不能为负数")
        }
        
        if (cacheExpireTime <= 0) {
            errors.add("缓存过期时间必须大于0")
        }
        
        if (maxRetryCount < 0) {
            errors.add("最大重试次数不能为负数")
        }
        
        if (initialRetryDelay < 0) {
            errors.add("初始重试延迟不能为负数")
        }
        
        if (retryMultiplier <= 0) {
            errors.add("重试倍数必须大于0")
        }
        
        if (maxRetryDelay < initialRetryDelay) {
            errors.add("最大重试延迟不能小于初始重试延迟")
        }
        
        if (requestTimeout <= 0) {
            errors.add("请求超时时间必须大于0")
        }
        
        if (batchUpdateDelay < 0) {
            errors.add("批量更新延迟不能为负数")
        }
        
        return errors
    }
    
    // 为兼容性添加的方法
    fun getMinMoveDistanceMeters(): Double = 100.0 // 默认100米
    
    // Setter方法（为了支持链式调用）
    fun setDebounceDelay(delay: Long): MapInteractionConfig {
        debounceDelay = delay
        return this
    }
    
    fun setMinMoveDistancePixels(pixels: Int): MapInteractionConfig {
        distanceThresholdPx = pixels
        return this
    }
    
    fun setMinMoveDistanceMeters(meters: Double): MapInteractionConfig {
        // 这里可以添加米到像素的转换逻辑，暂时存储为配置项
        prefs?.edit()?.putFloat("min_move_distance_meters", meters.toFloat())?.apply()
        return this
    }
    
    fun setCacheExpireTime(time: Long): MapInteractionConfig {
        cacheExpireTime = time
        return this
    }
    
    fun setMaxRetryAttempts(attempts: Int): MapInteractionConfig {
        maxRetryCount = attempts
        return this
    }
    
    fun setInitialRetryDelay(delay: Long): MapInteractionConfig {
        initialRetryDelay = delay
        return this
    }
    
    fun setRetryBackoffMultiplier(multiplier: Double): MapInteractionConfig {
        retryMultiplier = multiplier
        return this
    }
    
    fun setMaxRetryDelay(delay: Long): MapInteractionConfig {
        maxRetryDelay = delay
        return this
    }
    
    fun setEnablePerformanceMonitoring(enabled: Boolean): MapInteractionConfig {
        performanceMonitoringEnabled = enabled
        return this
    }
    
    fun setRequestTimeout(timeout: Long): MapInteractionConfig {
        requestTimeout = timeout
        return this
    }
    
    fun setBatchUpdateDelay(delay: Long): MapInteractionConfig {
        batchUpdateDelay = delay
        return this
    }
    
    // 为兼容性添加的别名属性
    val enablePerformanceMonitoring: Boolean
        get() = performanceMonitoringEnabled
    
    val maxRetryAttempts: Int
        get() = maxRetryCount
    
    val retryBackoffMultiplier: Double
        get() = retryMultiplier
}