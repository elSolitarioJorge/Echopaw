package com.example.echopaw.monitor

import android.os.SystemClock
import android.util.Log
import com.example.echopaw.config.MapInteractionConfig
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * 性能监控管理器
 * 
 * 负责收集和分析地图交互功能的性能指标，包括：
 * 1. 请求响应时间监控
 * 2. 缓存命中率统计
 * 3. 内存使用情况跟踪
 * 4. 网络请求性能分析
 * 5. 用户交互响应时间
 */
class PerformanceMonitor(
    private val config: MapInteractionConfig
) {
    
    companion object {
        private const val TAG = "PerformanceMonitor"
        
        // 性能指标类型
        const val METRIC_REQUEST_DURATION = "request_duration"
        const val METRIC_CACHE_HIT_RATE = "cache_hit_rate"
        const val METRIC_MARKER_UPDATE_TIME = "marker_update_time"
        const val METRIC_NETWORK_LATENCY = "network_latency"
        const val METRIC_UI_RESPONSE_TIME = "ui_response_time"
        const val METRIC_MEMORY_USAGE = "memory_usage"
        const val METRIC_DEBOUNCE_EFFECTIVENESS = "debounce_effectiveness"
        
        // 性能阈值
        private const val SLOW_REQUEST_THRESHOLD_MS = 3000L
        private const val SLOW_UI_RESPONSE_THRESHOLD_MS = 100L
        private const val HIGH_MEMORY_THRESHOLD_MB = 50L
    }

    /**
     * 性能指标数据
     */
    data class PerformanceMetric(
        val name: String,
        val value: Double,
        val timestamp: Long,
        val metadata: Map<String, Any> = emptyMap()
    )

    /**
     * 性能统计摘要
     */
    data class PerformanceSummary(
        val totalRequests: Int,
        val averageRequestTime: Long,
        val slowRequestCount: Int,
        val cacheHitRate: Double,
        val averageMarkerUpdateTime: Long,
        val averageNetworkLatency: Long,
        val averageUIResponseTime: Long,
        val slowUIResponseCount: Int,
        val currentMemoryUsageMB: Long,
        val peakMemoryUsageMB: Long,
        val debounceEffectiveness: Double,
        val uptime: Long
    )

    /**
     * 计时器
     */
    class Timer(private val name: String) {
        private val startTime = SystemClock.elapsedRealtime()
        
        fun stop(): Long {
            val duration = SystemClock.elapsedRealtime() - startTime
            Log.d(TAG, "Timer '$name' completed in ${duration}ms")
            return duration
        }
        
        fun stopAndRecord(monitor: PerformanceMonitor, metadata: Map<String, Any> = emptyMap()): Long {
            val duration = stop()
            monitor.recordMetric(name, duration.toDouble(), metadata)
            return duration
        }
    }

    // 性能指标存储
    private val metrics = ConcurrentHashMap<String, MutableList<PerformanceMetric>>()
    
    // 计数器
    private val totalRequests = AtomicInteger(0)
    private val slowRequests = AtomicInteger(0)
    private val cacheHits = AtomicInteger(0)
    private val cacheMisses = AtomicInteger(0)
    private val slowUIResponses = AtomicInteger(0)
    private val debouncedRequests = AtomicInteger(0)
    private val actualRequests = AtomicInteger(0)
    
    // 内存监控
    private val peakMemoryUsage = AtomicLong(0L)
    private val startTime = System.currentTimeMillis()
    
    // 监控任务
    private val monitorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var memoryMonitorJob: Job? = null

    init {
        if (config.enablePerformanceMonitoring) {
            startMemoryMonitoring()
        }
    }

    /**
     * 开始计时
     */
    fun startTimer(name: String): Timer {
        return Timer(name)
    }

    /**
     * 记录性能指标
     */
    fun recordMetric(name: String, value: Double, metadata: Map<String, Any> = emptyMap()) {
        if (!config.enablePerformanceMonitoring) return
        
        val metric = PerformanceMetric(
            name = name,
            value = value,
            timestamp = System.currentTimeMillis(),
            metadata = metadata
        )
        
        metrics.computeIfAbsent(name) { mutableListOf() }.add(metric)
        
        // 处理特定指标的统计
        when (name) {
            METRIC_REQUEST_DURATION -> {
                totalRequests.incrementAndGet()
                if (value > SLOW_REQUEST_THRESHOLD_MS) {
                    slowRequests.incrementAndGet()
                }
            }
            METRIC_UI_RESPONSE_TIME -> {
                if (value > SLOW_UI_RESPONSE_THRESHOLD_MS) {
                    slowUIResponses.incrementAndGet()
                }
            }
        }
        
        Log.d(TAG, "Recorded metric: $name = $value")
    }

    /**
     * 记录缓存命中
     */
    fun recordCacheHit() {
        cacheHits.incrementAndGet()
        recordMetric(METRIC_CACHE_HIT_RATE, 1.0, mapOf("type" to "hit"))
    }

    /**
     * 记录缓存未命中
     */
    fun recordCacheMiss() {
        cacheMisses.incrementAndGet()
        recordMetric(METRIC_CACHE_HIT_RATE, 0.0, mapOf("type" to "miss"))
    }

    /**
     * 记录防抖效果
     */
    fun recordDebounceEffect(wasDebounced: Boolean) {
        if (wasDebounced) {
            debouncedRequests.incrementAndGet()
        } else {
            actualRequests.incrementAndGet()
        }
        
        val effectiveness = calculateDebounceEffectiveness()
        recordMetric(METRIC_DEBOUNCE_EFFECTIVENESS, effectiveness)
    }

    /**
     * 记录网络请求性能
     */
    fun recordNetworkRequest(duration: Long, success: Boolean, responseSize: Int = 0) {
        recordMetric(METRIC_NETWORK_LATENCY, duration.toDouble(), mapOf(
            "success" to success,
            "responseSize" to responseSize
        ))
    }

    /**
     * 记录标记更新性能
     */
    fun recordMarkerUpdate(duration: Long, markerCount: Int, updateType: String) {
        recordMetric(METRIC_MARKER_UPDATE_TIME, duration.toDouble(), mapOf(
            "markerCount" to markerCount,
            "updateType" to updateType
        ))
    }

    /**
     * 记录UI响应时间
     */
    fun recordUIResponse(duration: Long, action: String) {
        recordMetric(METRIC_UI_RESPONSE_TIME, duration.toDouble(), mapOf(
            "action" to action
        ))
    }

    /**
     * 获取指标的统计信息
     */
    fun getMetricStats(metricName: String): MetricStats? {
        val metricList = metrics[metricName] ?: return null
        
        if (metricList.isEmpty()) return null
        
        val values = metricList.map { it.value }
        val count = values.size
        val sum = values.sum()
        val average = sum / count
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 0.0
        
        // 计算百分位数
        val sortedValues = values.sorted()
        val p50 = percentile(sortedValues, 50.0)
        val p90 = percentile(sortedValues, 90.0)
        val p95 = percentile(sortedValues, 95.0)
        val p99 = percentile(sortedValues, 99.0)
        
        return MetricStats(
            name = metricName,
            count = count,
            sum = sum,
            average = average,
            min = min,
            max = max,
            p50 = p50,
            p90 = p90,
            p95 = p95,
            p99 = p99
        )
    }

    /**
     * 指标统计信息
     */
    data class MetricStats(
        val name: String,
        val count: Int,
        val sum: Double,
        val average: Double,
        val min: Double,
        val max: Double,
        val p50: Double,
        val p90: Double,
        val p95: Double,
        val p99: Double
    )

    /**
     * 计算百分位数
     */
    private fun percentile(sortedValues: List<Double>, percentile: Double): Double {
        if (sortedValues.isEmpty()) return 0.0
        
        val index = (percentile / 100.0) * (sortedValues.size - 1)
        val lower = index.toInt()
        val upper = min(lower + 1, sortedValues.size - 1)
        val weight = index - lower
        
        return sortedValues[lower] * (1 - weight) + sortedValues[upper] * weight
    }

    /**
     * 获取性能摘要
     */
    fun getPerformanceSummary(): PerformanceSummary {
        val requestStats = getMetricStats(METRIC_REQUEST_DURATION)
        val markerStats = getMetricStats(METRIC_MARKER_UPDATE_TIME)
        val networkStats = getMetricStats(METRIC_NETWORK_LATENCY)
        val uiStats = getMetricStats(METRIC_UI_RESPONSE_TIME)
        
        return PerformanceSummary(
            totalRequests = totalRequests.get(),
            averageRequestTime = requestStats?.average?.toLong() ?: 0L,
            slowRequestCount = slowRequests.get(),
            cacheHitRate = calculateCacheHitRate(),
            averageMarkerUpdateTime = markerStats?.average?.toLong() ?: 0L,
            averageNetworkLatency = networkStats?.average?.toLong() ?: 0L,
            averageUIResponseTime = uiStats?.average?.toLong() ?: 0L,
            slowUIResponseCount = slowUIResponses.get(),
            currentMemoryUsageMB = getCurrentMemoryUsage(),
            peakMemoryUsageMB = peakMemoryUsage.get(),
            debounceEffectiveness = calculateDebounceEffectiveness(),
            uptime = System.currentTimeMillis() - startTime
        )
    }

    /**
     * 计算缓存命中率
     */
    private fun calculateCacheHitRate(): Double {
        val hits = cacheHits.get()
        val misses = cacheMisses.get()
        val total = hits + misses
        
        return if (total > 0) {
            hits.toDouble() / total * 100
        } else 0.0
    }

    /**
     * 计算防抖效果
     */
    private fun calculateDebounceEffectiveness(): Double {
        val debounced = debouncedRequests.get()
        val actual = actualRequests.get()
        val total = debounced + actual
        
        return if (total > 0) {
            debounced.toDouble() / total * 100
        } else 0.0
    }

    /**
     * 获取当前内存使用量（MB）
     */
    private fun getCurrentMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        return usedMemory / (1024 * 1024)
    }

    /**
     * 开始内存监控
     */
    private fun startMemoryMonitoring() {
        memoryMonitorJob = monitorScope.launch {
            while (isActive) {
                val currentMemory = getCurrentMemoryUsage()
                peakMemoryUsage.set(max(peakMemoryUsage.get(), currentMemory))
                
                recordMetric(METRIC_MEMORY_USAGE, currentMemory.toDouble())
                
                if (currentMemory > HIGH_MEMORY_THRESHOLD_MB) {
                    Log.w(TAG, "High memory usage detected: ${currentMemory}MB")
                }
                
                delay(5000) // 每5秒检查一次内存使用
            }
        }
    }

    /**
     * 生成性能报告
     */
    fun generatePerformanceReport(): String {
        val summary = getPerformanceSummary()
        val sb = StringBuilder()
        
        sb.appendLine("=== 性能监控报告 ===")
        sb.appendLine("运行时间: ${summary.uptime / 1000}秒")
        sb.appendLine()
        
        sb.appendLine("请求性能:")
        sb.appendLine("  总请求数: ${summary.totalRequests}")
        sb.appendLine("  平均响应时间: ${summary.averageRequestTime}ms")
        sb.appendLine("  慢请求数: ${summary.slowRequestCount}")
        sb.appendLine("  缓存命中率: ${"%.2f".format(summary.cacheHitRate)}%")
        sb.appendLine()
        
        sb.appendLine("UI性能:")
        sb.appendLine("  平均UI响应时间: ${summary.averageUIResponseTime}ms")
        sb.appendLine("  慢UI响应数: ${summary.slowUIResponseCount}")
        sb.appendLine("  平均标记更新时间: ${summary.averageMarkerUpdateTime}ms")
        sb.appendLine()
        
        sb.appendLine("网络性能:")
        sb.appendLine("  平均网络延迟: ${summary.averageNetworkLatency}ms")
        sb.appendLine()
        
        sb.appendLine("内存使用:")
        sb.appendLine("  当前内存使用: ${summary.currentMemoryUsageMB}MB")
        sb.appendLine("  峰值内存使用: ${summary.peakMemoryUsageMB}MB")
        sb.appendLine()
        
        sb.appendLine("优化效果:")
        sb.appendLine("  防抖有效性: ${"%.2f".format(summary.debounceEffectiveness)}%")
        sb.appendLine()
        
        // 添加详细的指标统计
        metrics.keys.forEach { metricName ->
            val stats = getMetricStats(metricName)
            if (stats != null) {
                sb.appendLine("$metricName 统计:")
                sb.appendLine("  样本数: ${stats.count}")
                sb.appendLine("  平均值: ${"%.2f".format(stats.average)}")
                sb.appendLine("  最小值: ${"%.2f".format(stats.min)}")
                sb.appendLine("  最大值: ${"%.2f".format(stats.max)}")
                sb.appendLine("  P50: ${"%.2f".format(stats.p50)}")
                sb.appendLine("  P90: ${"%.2f".format(stats.p90)}")
                sb.appendLine("  P95: ${"%.2f".format(stats.p95)}")
                sb.appendLine("  P99: ${"%.2f".format(stats.p99)}")
                sb.appendLine()
            }
        }
        
        return sb.toString()
    }

    /**
     * 清除所有指标数据
     */
    fun clearMetrics() {
        metrics.clear()
        totalRequests.set(0)
        slowRequests.set(0)
        cacheHits.set(0)
        cacheMisses.set(0)
        slowUIResponses.set(0)
        debouncedRequests.set(0)
        actualRequests.set(0)
        peakMemoryUsage.set(0L)
        Log.d(TAG, "Cleared all performance metrics")
    }

    /**
     * 获取指定时间范围内的指标
     */
    fun getMetricsInTimeRange(metricName: String, startTime: Long, endTime: Long): List<PerformanceMetric> {
        return metrics[metricName]?.filter { metric ->
            metric.timestamp in startTime..endTime
        } ?: emptyList()
    }

    /**
     * 检查是否有性能问题
     */
    fun hasPerformanceIssues(): Boolean {
        val summary = getPerformanceSummary()
        
        return summary.averageRequestTime > SLOW_REQUEST_THRESHOLD_MS ||
               summary.averageUIResponseTime > SLOW_UI_RESPONSE_THRESHOLD_MS ||
               summary.currentMemoryUsageMB > HIGH_MEMORY_THRESHOLD_MB ||
               summary.cacheHitRate < 50.0 // 缓存命中率过低
    }

    /**
     * 获取性能建议
     */
    fun getPerformanceRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()
        val summary = getPerformanceSummary()
        
        if (summary.averageRequestTime > SLOW_REQUEST_THRESHOLD_MS) {
            recommendations.add("请求响应时间过长，建议优化网络请求或增加缓存")
        }
        
        if (summary.cacheHitRate < 50.0) {
            recommendations.add("缓存命中率较低，建议调整缓存策略或增加缓存时间")
        }
        
        if (summary.currentMemoryUsageMB > HIGH_MEMORY_THRESHOLD_MB) {
            recommendations.add("内存使用量较高，建议优化数据结构或增加内存回收")
        }
        
        if (summary.debounceEffectiveness < 30.0) {
            recommendations.add("防抖效果不明显，建议调整防抖延迟时间")
        }
        
        if (summary.slowUIResponseCount > summary.totalRequests * 0.1) {
            recommendations.add("UI响应较慢的情况较多，建议优化UI更新逻辑")
        }
        
        return recommendations
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        monitorScope.cancel()
        metrics.clear()
        Log.d(TAG, "Cleaned up performance monitor resources")
    }
}