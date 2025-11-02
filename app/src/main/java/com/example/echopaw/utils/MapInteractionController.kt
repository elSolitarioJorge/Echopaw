package com.example.echopaw.utils

import android.util.Log
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.example.echopaw.config.MapInteractionConfig
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * 地图交互控制器
 * 
 * 负责管理地图交互的防抖机制和距离阈值控制，包括：
 * 1. 防抖机制 - 延迟触发数据请求
 * 2. 距离阈值控制 - 最小移动距离判断
 * 3. 相机位置变化监听
 * 4. 性能优化策略
 */
class MapInteractionController(
    private val config: MapInteractionConfig
) {
    
    companion object {
        private const val TAG = "MapInteractionController"
        
        // 地球半径（米）
        private const val EARTH_RADIUS = 6371000.0
    }

    /**
     * 地图交互监听器
     */
    interface MapInteractionListener {
        /**
         * 当地图位置发生有效变化时触发
         * @param newPosition 新的相机位置
         * @param oldPosition 旧的相机位置
         */
        fun onMapPositionChanged(newPosition: CameraPosition, oldPosition: CameraPosition?)
        
        /**
         * 当防抖延迟结束，需要加载数据时触发
         * @param position 当前相机位置
         */
        fun onDataLoadRequired(position: CameraPosition)
    }

    private var listener: MapInteractionListener? = null
    
    // 防抖相关
    private val debounceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var debounceJob: Job? = null
    
    // 位置跟踪
    private var lastCameraPosition: CameraPosition? = null
    private var lastValidPosition: CameraPosition? = null
    
    // 性能监控
    private var totalCameraChanges = 0
    private var validCameraChanges = 0
    private var debouncedRequests = 0

    /**
     * 设置地图交互监听器
     */
    fun setMapInteractionListener(listener: MapInteractionListener?) {
        this.listener = listener
    }

    /**
     * 处理相机位置变化
     * 
     * @param newPosition 新的相机位置
     */
    fun onCameraPositionChanged(newPosition: CameraPosition) {
        totalCameraChanges++
        
        Log.d(TAG, "Camera position changed: lat=${newPosition.target.latitude}, lng=${newPosition.target.longitude}, zoom=${newPosition.zoom}")
        
        val oldPosition = lastCameraPosition
        lastCameraPosition = newPosition
        
        // 检查是否满足距离阈值
        if (!shouldTriggerUpdate(newPosition, lastValidPosition)) {
            Log.d(TAG, "Camera change below threshold, ignoring")
            return
        }
        
        validCameraChanges++
        lastValidPosition = newPosition
        
        // 通知位置变化
        listener?.onMapPositionChanged(newPosition, oldPosition)
        
        // 启动防抖机制
        startDebounceTimer(newPosition)
    }

    /**
     * 检查是否应该触发更新
     * 
     * @param newPosition 新位置
     * @param lastPosition 上次有效位置
     * @return 是否应该触发更新
     */
    private fun shouldTriggerUpdate(newPosition: CameraPosition, lastPosition: CameraPosition?): Boolean {
        if (lastPosition == null) {
            return true
        }
        
        // 计算像素距离
        val pixelDistance = calculatePixelDistance(newPosition, lastPosition)
        
        // 检查缩放级别变化
        val zoomChanged = abs(newPosition.zoom - lastPosition.zoom) > 0.1f
        
        val shouldTrigger = pixelDistance >= config.distanceThresholdPx || zoomChanged
        
        Log.d(TAG, "Distance check: pixelDistance=$pixelDistance, threshold=${config.distanceThresholdPx}, zoomChanged=$zoomChanged, shouldTrigger=$shouldTrigger")
        
        return shouldTrigger
    }

    /**
     * 计算两个位置之间的像素距离（近似值）
     * 
     * @param pos1 位置1
     * @param pos2 位置2
     * @return 像素距离
     */
    private fun calculatePixelDistance(pos1: CameraPosition, pos2: CameraPosition): Double {
        // 计算地理距离（米）
        val geoDistance = calculateGeographicDistance(pos1.target, pos2.target)
        
        // 根据缩放级别转换为像素距离（近似）
        // 缩放级别每增加1，地图比例尺缩小一半
        val metersPerPixel = 156543.03392 * cos(pos1.target.latitude * PI / 180) / (2.0.pow(pos1.zoom.toDouble()))
        
        return geoDistance / metersPerPixel
    }

    /**
     * 计算两个地理位置之间的距离（米）
     * 使用Haversine公式
     */
    private fun calculateGeographicDistance(pos1: LatLng, pos2: LatLng): Double {
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
     * 启动防抖计时器
     * 
     * @param position 当前相机位置
     */
    private fun startDebounceTimer(position: CameraPosition) {
        // 取消之前的防抖任务
        debounceJob?.cancel()
        
        // 启动新的防抖任务
        debounceJob = debounceScope.launch {
            delay(config.debounceDelay)
            
            if (isActive) {
                debouncedRequests++
                Log.d(TAG, "Debounce timer completed, triggering data load")
                listener?.onDataLoadRequired(position)
            }
        }
        
        Log.d(TAG, "Started debounce timer with delay ${config.debounceDelay}ms")
    }

    /**
     * 立即触发数据加载（跳过防抖）
     * 
     * @param position 相机位置
     */
    fun triggerImmediateDataLoad(position: CameraPosition) {
        debounceJob?.cancel()
        listener?.onDataLoadRequired(position)
        Log.d(TAG, "Triggered immediate data load")
    }

    /**
     * 取消当前的防抖任务
     */
    fun cancelDebounce() {
        debounceJob?.cancel()
        Log.d(TAG, "Cancelled debounce timer")
    }

    /**
     * 重置控制器状态
     */
    fun reset() {
        debounceJob?.cancel()
        lastCameraPosition = null
        lastValidPosition = null
        totalCameraChanges = 0
        validCameraChanges = 0
        debouncedRequests = 0
        Log.d(TAG, "Reset controller state")
    }

    /**
     * 获取性能统计信息
     */
    fun getPerformanceStats(): PerformanceStats {
        return PerformanceStats(
            totalCameraChanges = totalCameraChanges,
            validCameraChanges = validCameraChanges,
            debouncedRequests = debouncedRequests,
            filterEfficiency = if (totalCameraChanges > 0) {
                (totalCameraChanges - validCameraChanges).toDouble() / totalCameraChanges * 100
            } else 0.0
        )
    }

    /**
     * 性能统计数据
     */
    data class PerformanceStats(
        val totalCameraChanges: Int,
        val validCameraChanges: Int,
        val debouncedRequests: Int,
        val filterEfficiency: Double // 过滤效率百分比
    )

    /**
     * 更新配置参数
     * 
     * @param newDebounceDelay 新的防抖延迟时间
     * @param newDistanceThreshold 新的距离阈值
     */
    fun updateConfig(newDebounceDelay: Long? = null, newDistanceThreshold: Int? = null) {
        newDebounceDelay?.let { config.debounceDelay = it }
        newDistanceThreshold?.let { config.distanceThresholdPx = it }
        
        Log.d(TAG, "Updated config: debounceDelay=${config.debounceDelay}, distanceThreshold=${config.distanceThresholdPx}")
    }

    /**
     * 检查当前是否有待处理的防抖任务
     */
    fun hasPendingDebounce(): Boolean {
        return debounceJob?.isActive == true
    }

    /**
     * 获取当前相机位置
     */
    fun getCurrentCameraPosition(): CameraPosition? = lastCameraPosition

    /**
     * 获取上次有效位置
     */
    fun getLastValidPosition(): CameraPosition? = lastValidPosition

    /**
     * 清理资源
     */
    fun cleanup() {
        debounceScope.cancel()
        listener = null
        Log.d(TAG, "Cleaned up resources")
    }
}