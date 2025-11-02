package com.example.echopaw.cache

import android.util.Log
import com.amap.api.maps.model.LatLng
import com.example.echopaw.config.MapInteractionConfig
import com.example.echopaw.network.AudioRecordWithLocation
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * 请求去重管理器
 * 
 * 负责管理地理区域的请求缓存，避免重复请求相同区域的数据，包括：
 * 1. 地理区域缓存机制
 * 2. 请求去重逻辑
 * 3. 缓存过期管理
 * 4. 区域重叠检测
 */
class RequestDeduplicationManager(
    private val config: MapInteractionConfig
) {
    
    companion object {
        private const val TAG = "RequestDeduplicationManager"
        
        // 地球半径（米）
        private const val EARTH_RADIUS = 6371000.0
        
        // 默认搜索半径（米）
        private const val DEFAULT_SEARCH_RADIUS = 1000.0
    }

    /**
     * 缓存区域信息
     */
    data class CachedRegion(
        val center: LatLng,
        val radius: Double,
        val data: List<AudioRecordWithLocation>,
        val timestamp: Long,
        val requestId: String
    ) {
        /**
         * 检查缓存是否过期
         */
        fun isExpired(currentTime: Long, expireTime: Long): Boolean {
            return currentTime - timestamp > expireTime
        }
        
        /**
         * 检查指定位置是否在此缓存区域内
         */
        fun contains(position: LatLng, searchRadius: Double): Boolean {
            val distance = calculateDistance(center, position)
            // 如果新请求的区域完全包含在已缓存的区域内，则认为命中缓存
            return distance + searchRadius <= radius
        }
        
        /**
         * 检查与另一个区域是否有重叠
         */
        fun overlaps(other: LatLng, otherRadius: Double): Boolean {
            val distance = calculateDistance(center, other)
            return distance < (radius + otherRadius)
        }
        
        /**
         * 计算两点之间的距离
         */
        private fun calculateDistance(pos1: LatLng, pos2: LatLng): Double {
            val lat1Rad = pos1.latitude * PI / 180
            val lat2Rad = pos2.latitude * PI / 180
            val deltaLatRad = (pos2.latitude - pos1.latitude) * PI / 180
            val deltaLngRad = (pos2.longitude - pos1.longitude) * PI / 180

            val a = sin(deltaLatRad / 2) * sin(deltaLatRad / 2) +
                    cos(lat1Rad) * cos(lat2Rad) *
                    sin(deltaLngRad / 2) * sin(deltaLngRad / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))

            return EARTH_RADIUS * c
        }
    }

    /**
     * 请求结果
     */
    sealed class RequestResult {
        /**
         * 缓存命中，返回缓存数据
         */
        data class CacheHit(
            val data: List<AudioRecordWithLocation>,
            val cachedRegion: CachedRegion
        ) : RequestResult()
        
        /**
         * 缓存未命中，需要发起新请求
         */
        data class CacheMiss(
            val requestId: String
        ) : RequestResult()
        
        /**
         * 部分缓存命中，可以合并缓存数据和新数据
         */
        data class PartialHit(
            val cachedData: List<AudioRecordWithLocation>,
            val requestId: String,
            val overlappingRegions: List<CachedRegion>
        ) : RequestResult()
    }

    // 缓存存储
    private val regionCache = ConcurrentHashMap<String, CachedRegion>()
    
    // 清理任务
    private val cleanupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var cleanupJob: Job? = null
    
    // 统计信息
    private var totalRequests = 0
    private var cacheHits = 0
    private var cacheMisses = 0
    private var partialHits = 0

    init {
        startPeriodicCleanup()
    }

    /**
     * 检查请求是否可以从缓存中获取数据
     * 
     * @param center 搜索中心点
     * @param radius 搜索半径（米）
     * @return 请求结果
     */
    fun checkRequest(center: LatLng, radius: Double = DEFAULT_SEARCH_RADIUS): RequestResult {
        totalRequests++
        val currentTime = System.currentTimeMillis()
        
        Log.d(TAG, "Checking request for center: (${center.latitude}, ${center.longitude}), radius: $radius")
        
        // 清理过期缓存
        cleanupExpiredCache(currentTime)
        
        // 查找完全匹配的缓存
        val exactMatch = findExactMatch(center, radius, currentTime)
        if (exactMatch != null) {
            cacheHits++
            Log.d(TAG, "Cache hit found for request")
            return RequestResult.CacheHit(exactMatch.data, exactMatch)
        }
        
        // 查找部分重叠的缓存
        val overlappingRegions = findOverlappingRegions(center, radius, currentTime)
        if (overlappingRegions.isNotEmpty()) {
            partialHits++
            val mergedData = mergeOverlappingData(overlappingRegions, center, radius)
            Log.d(TAG, "Partial cache hit found with ${overlappingRegions.size} overlapping regions")
            return RequestResult.PartialHit(
                cachedData = mergedData,
                requestId = generateRequestId(),
                overlappingRegions = overlappingRegions
            )
        }
        
        // 缓存未命中
        cacheMisses++
        Log.d(TAG, "Cache miss for request")
        return RequestResult.CacheMiss(generateRequestId())
    }

    /**
     * 缓存请求结果
     * 
     * @param requestId 请求ID
     * @param center 搜索中心点
     * @param radius 搜索半径
     * @param data 请求结果数据
     */
    fun cacheResult(
        requestId: String,
        center: LatLng,
        radius: Double,
        data: List<AudioRecordWithLocation>
    ) {
        val cachedRegion = CachedRegion(
            center = center,
            radius = radius,
            data = data,
            timestamp = System.currentTimeMillis(),
            requestId = requestId
        )
        
        regionCache[requestId] = cachedRegion
        
        Log.d(TAG, "Cached result for request $requestId with ${data.size} records")
        
        // 检查是否需要合并或替换现有缓存
        optimizeCache(cachedRegion)
    }

    /**
     * 查找完全匹配的缓存
     */
    private fun findExactMatch(center: LatLng, radius: Double, currentTime: Long): CachedRegion? {
        return regionCache.values.find { cachedRegion ->
            !cachedRegion.isExpired(currentTime, config.cacheExpireTime) &&
            cachedRegion.contains(center, radius)
        }
    }

    /**
     * 查找重叠的缓存区域
     */
    private fun findOverlappingRegions(center: LatLng, radius: Double, currentTime: Long): List<CachedRegion> {
        return regionCache.values.filter { cachedRegion ->
            !cachedRegion.isExpired(currentTime, config.cacheExpireTime) &&
            cachedRegion.overlaps(center, radius)
        }
    }

    /**
     * 合并重叠区域的数据
     */
    private fun mergeOverlappingData(
        overlappingRegions: List<CachedRegion>,
        center: LatLng,
        radius: Double
    ): List<AudioRecordWithLocation> {
        val mergedData = mutableSetOf<AudioRecordWithLocation>()
        
        overlappingRegions.forEach { region ->
            // 只添加在请求范围内的数据
            region.data.forEach { audioRecord ->
                val distance = calculateDistance(center, LatLng(audioRecord.location.lat, audioRecord.location.lon))
                if (distance <= radius) {
                    mergedData.add(audioRecord)
                }
            }
        }
        
        return mergedData.toList()
    }

    /**
     * 优化缓存，合并相邻区域或移除冗余缓存
     */
    private fun optimizeCache(newRegion: CachedRegion) {
        val toRemove = mutableListOf<String>()
        
        regionCache.forEach { (key, existingRegion) ->
            if (key != newRegion.requestId) {
                // 如果新区域完全包含现有区域，移除现有区域
                if (newRegion.contains(existingRegion.center, existingRegion.radius)) {
                    toRemove.add(key)
                    Log.d(TAG, "Removing redundant cache region: $key")
                }
            }
        }
        
        toRemove.forEach { regionCache.remove(it) }
    }

    /**
     * 清理过期缓存
     */
    private fun cleanupExpiredCache(currentTime: Long) {
        val expiredKeys = regionCache.filter { (_, region) ->
            region.isExpired(currentTime, config.cacheExpireTime)
        }.keys
        
        expiredKeys.forEach { key ->
            regionCache.remove(key)
            Log.d(TAG, "Removed expired cache region: $key")
        }
    }

    /**
     * 启动定期清理任务
     */
    private fun startPeriodicCleanup() {
        cleanupJob = cleanupScope.launch {
            while (isActive) {
                delay(config.cacheExpireTime / 4) // 每1/4过期时间清理一次
                
                if (isActive) {
                    val currentTime = System.currentTimeMillis()
                    cleanupExpiredCache(currentTime)
                }
            }
        }
    }

    /**
     * 计算两点之间的距离
     */
    private fun calculateDistance(pos1: LatLng, pos2: LatLng): Double {
        val lat1Rad = pos1.latitude * PI / 180
        val lat2Rad = pos2.latitude * PI / 180
        val deltaLatRad = (pos2.latitude - pos1.latitude) * PI / 180
        val deltaLngRad = (pos2.longitude - pos1.longitude) * PI / 180

        val a = sin(deltaLatRad / 2) * sin(deltaLatRad / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLngRad / 2) * sin(deltaLngRad / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS * c
    }

    /**
     * 生成请求ID
     */
    private fun generateRequestId(): String {
        return "req_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}"
    }

    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(): CacheStats {
        val hitRate = if (totalRequests > 0) {
            (cacheHits + partialHits).toDouble() / totalRequests * 100
        } else 0.0
        
        return CacheStats(
            totalRequests = totalRequests,
            cacheHits = cacheHits,
            cacheMisses = cacheMisses,
            partialHits = partialHits,
            hitRate = hitRate,
            cachedRegions = regionCache.size
        )
    }

    /**
     * 缓存统计信息
     */
    data class CacheStats(
        val totalRequests: Int,
        val cacheHits: Int,
        val cacheMisses: Int,
        val partialHits: Int,
        val hitRate: Double,
        val cachedRegions: Int
    )

    /**
     * 清空所有缓存
     */
    fun clearCache() {
        regionCache.clear()
        Log.d(TAG, "Cleared all cache")
    }

    /**
     * 重置统计信息
     */
    fun resetStats() {
        totalRequests = 0
        cacheHits = 0
        cacheMisses = 0
        partialHits = 0
        Log.d(TAG, "Reset cache statistics")
    }

    /**
     * 获取缓存大小
     */
    fun getCacheSize(): Int = regionCache.size

    /**
     * 检查指定区域是否已缓存
     */
    fun isRegionCached(center: LatLng, radius: Double): Boolean {
        val currentTime = System.currentTimeMillis()
        return findExactMatch(center, radius, currentTime) != null
    }

    /**
     * 获取指定区域的缓存数据
     */
    fun getCachedData(center: LatLng, radius: Double): List<AudioRecordWithLocation>? {
        val currentTime = System.currentTimeMillis()
        return findExactMatch(center, radius, currentTime)?.data
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        cleanupScope.cancel()
        regionCache.clear()
        Log.d(TAG, "Cleaned up resources")
    }
}