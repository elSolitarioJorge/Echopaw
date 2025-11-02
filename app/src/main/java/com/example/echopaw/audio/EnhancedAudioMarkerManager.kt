package com.example.echopaw.audio

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.ImageView
import com.amap.api.maps.AMap
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.example.echopaw.R
import com.example.echopaw.config.MapInteractionConfig
import com.example.echopaw.network.AudioRecord
import com.example.echopaw.network.AudioRecordWithLocation
import kotlinx.coroutines.*

/**
 * 增强版音频标记管理器
 * 
 * 在原有功能基础上增加：
 * 1. 增量更新机制 - 仅更新变化的标记
 * 2. 批量更新优化 - 合并多个更新操作
 * 3. 性能监控 - 记录关键性能指标
 * 4. 配置化管理 - 支持动态配置参数
 */
class EnhancedAudioMarkerManager(
    private val context: Context,
    private val aMap: AMap,
    private val config: MapInteractionConfig
) {

    companion object {
        private const val TAG = "EnhancedAudioMarkerManager"
    }

    /**
     * 标记点击监听器
     */
    interface MarkerClickListener {
        fun onMarkerClicked(audioRecord: AudioRecord, marker: Marker)
    }

    /**
     * 性能监控数据
     */
    data class PerformanceMetrics(
        var totalUpdates: Int = 0,
        var incrementalUpdates: Int = 0,
        var markersAdded: Int = 0,
        var markersRemoved: Int = 0,
        var markersUpdated: Int = 0,
        var lastUpdateDuration: Long = 0,
        var averageUpdateDuration: Long = 0
    )

    /**
     * 标记差异信息
     */
    data class MarkerDiff(
        val toAdd: List<AudioRecordWithLocation>,
        val toRemove: List<String>, // audioId列表
        val toUpdate: List<AudioRecordWithLocation>
    )

    private var markerClickListener: MarkerClickListener? = null
    
    // 存储标记与音频记录的映射关系
    private val markerToAudioMap = mutableMapOf<Marker, AudioRecord>()
    private val audioToMarkerMap = mutableMapOf<String, Marker>()
    
    // 当前数据快照，用于增量更新对比
    private val currentAudioData = mutableMapOf<String, AudioRecordWithLocation>()
    
    // 当前播放的音频ID
    private var currentPlayingAudioId: String? = null
    
    // 标记状态缓存，避免重复更新相同状态
    private val markerStateCache = mutableMapOf<String, Boolean>()
    
    // 性能监控
    private val performanceMetrics = PerformanceMetrics()
    
    // 批量更新协程作用域
    private val updateScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var batchUpdateJob: Job? = null
    private val pendingUpdates = mutableListOf<AudioRecordWithLocation>()
    
    // 预创建的图标资源，避免频繁创建ImageView
    private val playIconBitmap by lazy { 
        BitmapDescriptorFactory.fromView(createCachedMarkerView(false))
    }
    private val playingIconBitmap by lazy { 
        BitmapDescriptorFactory.fromView(createCachedMarkerView(true))
    }

    /**
     * 设置标记点击监听器
     */
    fun setMarkerClickListener(listener: MarkerClickListener?) {
        this.markerClickListener = listener
    }

    /**
     * 增量更新音频标记
     * 
     * @param newAudioRecordsWithLocation 新的音频记录列表
     */
    fun updateAudioMarkersIncremental(newAudioRecordsWithLocation: List<AudioRecordWithLocation>) {
        if (config.performanceMonitoringEnabled) {
            performanceMetrics.totalUpdates++
        }
        
        val startTime = System.currentTimeMillis()
        
        try {
            Log.d(TAG, "Starting incremental update with ${newAudioRecordsWithLocation.size} records")
            
            // 计算差异
            val diff = calculateMarkerDiff(newAudioRecordsWithLocation)
            
            // 如果没有变化，直接返回
            if (diff.toAdd.isEmpty() && diff.toRemove.isEmpty() && diff.toUpdate.isEmpty()) {
                Log.d(TAG, "No changes detected, skipping update")
                return
            }
            
            Log.d(TAG, "Marker diff: add=${diff.toAdd.size}, remove=${diff.toRemove.size}, update=${diff.toUpdate.size}")
            
            // 执行增量更新
            executeIncrementalUpdate(diff)
            
            // 更新当前数据快照
            updateCurrentDataSnapshot(newAudioRecordsWithLocation)
            
            if (config.performanceMonitoringEnabled) {
                performanceMetrics.incrementalUpdates++
                val duration = System.currentTimeMillis() - startTime
                performanceMetrics.lastUpdateDuration = duration
                updateAverageUpdateDuration(duration)
                
                Log.d(TAG, "Incremental update completed in ${duration}ms")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during incremental update: ${e.message}", e)
        }
    }

    /**
     * 计算标记差异
     */
    private fun calculateMarkerDiff(newData: List<AudioRecordWithLocation>): MarkerDiff {
        val newDataMap = newData.associateBy { it.audioRecord.audioId }
        val currentIds = currentAudioData.keys
        val newIds = newDataMap.keys
        
        // 需要添加的标记（新数据中有，当前数据中没有）
        val toAdd = newIds.minus(currentIds).mapNotNull { newDataMap[it] }
        
        // 需要移除的标记（当前数据中有，新数据中没有）
        val toRemove = currentIds.minus(newIds).toList()
        
        // 需要更新的标记（位置或其他属性发生变化）
        val toUpdate = mutableListOf<AudioRecordWithLocation>()
        for (id in currentIds.intersect(newIds)) {
            val current = currentAudioData[id]
            val new = newDataMap[id]
            if (current != null && new != null && hasLocationChanged(current, new)) {
                toUpdate.add(new)
            }
        }
        
        return MarkerDiff(toAdd, toRemove, toUpdate)
    }

    /**
     * 检查位置是否发生变化
     */
    private fun hasLocationChanged(
        current: AudioRecordWithLocation, 
        new: AudioRecordWithLocation
    ): Boolean {
        val threshold = 0.00001 // 约1米的精度
        return kotlin.math.abs(current.location.lat - new.location.lat) > threshold ||
               kotlin.math.abs(current.location.lon - new.location.lon) > threshold
    }

    /**
     * 执行增量更新
     */
    private fun executeIncrementalUpdate(diff: MarkerDiff) {
        // 移除标记
        diff.toRemove.forEach { audioId ->
            removeMarker(audioId)
            if (config.performanceMonitoringEnabled) {
                performanceMetrics.markersRemoved++
            }
        }
        
        // 添加新标记
        diff.toAdd.forEach { audioRecordWithLocation ->
            addSingleAudioMarkerWithLocation(audioRecordWithLocation)
            if (config.performanceMonitoringEnabled) {
                performanceMetrics.markersAdded++
            }
        }
        
        // 更新现有标记
        diff.toUpdate.forEach { audioRecordWithLocation ->
            updateMarkerLocation(audioRecordWithLocation)
            if (config.performanceMonitoringEnabled) {
                performanceMetrics.markersUpdated++
            }
        }
    }

    /**
     * 移除指定的标记
     */
    private fun removeMarker(audioId: String) {
        val marker = audioToMarkerMap[audioId]
        if (marker != null) {
            marker.remove()
            markerToAudioMap.remove(marker)
            audioToMarkerMap.remove(audioId)
            markerStateCache.remove(audioId)
            
            if (currentPlayingAudioId == audioId) {
                currentPlayingAudioId = null
            }
            
            Log.d(TAG, "Removed marker for audio: $audioId")
        }
    }

    /**
     * 更新标记位置
     */
    private fun updateMarkerLocation(audioRecordWithLocation: AudioRecordWithLocation) {
        val audioId = audioRecordWithLocation.audioRecord.audioId
        val marker = audioToMarkerMap[audioId]
        
        if (marker != null) {
            val newLatLng = LatLng(audioRecordWithLocation.location.lat, audioRecordWithLocation.location.lon)
            marker.position = newLatLng
            
            // 更新映射关系中的音频记录
            markerToAudioMap[marker] = audioRecordWithLocation.audioRecord
            
            Log.d(TAG, "Updated marker location for audio: $audioId")
        }
    }

    /**
     * 批量更新音频标记（带防抖）
     */
    fun updateAudioMarkersBatch(newAudioRecordsWithLocation: List<AudioRecordWithLocation>) {
        // 取消之前的批量更新任务
        batchUpdateJob?.cancel()
        
        // 添加到待更新列表
        pendingUpdates.clear()
        pendingUpdates.addAll(newAudioRecordsWithLocation)
        
        // 启动新的批量更新任务
        batchUpdateJob = updateScope.launch {
            delay(config.batchUpdateDelay)
            
            if (isActive) {
                updateAudioMarkersIncremental(pendingUpdates.toList())
                pendingUpdates.clear()
            }
        }
    }

    /**
     * 更新当前数据快照
     */
    private fun updateCurrentDataSnapshot(newData: List<AudioRecordWithLocation>) {
        currentAudioData.clear()
        newData.forEach { audioRecordWithLocation ->
            currentAudioData[audioRecordWithLocation.audioRecord.audioId] = audioRecordWithLocation
        }
    }

    /**
     * 添加单个带有位置信息的音频标记
     */
    private fun addSingleAudioMarkerWithLocation(audioRecordWithLocation: AudioRecordWithLocation) {
        try {
            val latLng = LatLng(audioRecordWithLocation.location.lat, audioRecordWithLocation.location.lon)
            
            // 创建自定义标记视图
            val markerView = createMarkerView(audioRecordWithLocation.audioRecord)
            
            val markerOptions = MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.fromView(markerView))
                .anchor(0.5f, 0.5f)
                .draggable(false)
                .title(audioRecordWithLocation.audioRecord.audioId)
            
            val marker = aMap.addMarker(markerOptions)
            marker?.let { m ->
                // 建立映射关系
                markerToAudioMap[m] = audioRecordWithLocation.audioRecord
                audioToMarkerMap[audioRecordWithLocation.audioRecord.audioId] = m
                
                // 初始化状态缓存为未播放状态
                markerStateCache[audioRecordWithLocation.audioRecord.audioId] = false
                
                Log.d(TAG, "Added marker for audio: ${audioRecordWithLocation.audioRecord.audioId}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error adding marker for audio ${audioRecordWithLocation.audioRecord.audioId}: ${e.message}", e)
        }
    }

    /**
     * 创建标记视图
     */
    private fun createMarkerView(audioRecord: AudioRecord): View {
        return createCachedMarkerView(false)
    }
    
    /**
     * 创建缓存的标记视图
     */
    private fun createCachedMarkerView(isPlaying: Boolean): View {
        val imageView = ImageView(context)
        
        val iconRes = if (isPlaying) R.drawable.ic_played_1 else R.drawable.ic_play_1
        imageView.setImageResource(iconRes)
        
        val size = (140 * context.resources.displayMetrics.density).toInt()
        imageView.layoutParams = android.view.ViewGroup.LayoutParams(size, size)
        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        
        return imageView
    }

    /**
     * 设置地图标记点击监听器
     */
    fun setupMapMarkerClickListener() {
        aMap.setOnMarkerClickListener { marker ->
            val audioRecord = markerToAudioMap[marker]
            if (audioRecord != null) {
                Log.d(TAG, "Marker clicked for audio: ${audioRecord.audioId}")
                markerClickListener?.onMarkerClicked(audioRecord, marker)
                true
            } else {
                Log.w(TAG, "Clicked marker not found in audio map")
                false
            }
        }
    }

    /**
     * 更新标记的播放状态
     */
    fun updateMarkerPlayingState(audioId: String, isPlaying: Boolean) {
        Log.d(TAG, "Updating marker playing state: audioId=$audioId, isPlaying=$isPlaying")
        
        // 检查状态是否真正改变
        val cachedState = markerStateCache[audioId]
        if (cachedState == isPlaying) {
            Log.d(TAG, "Marker state unchanged for audio $audioId, skipping update")
            return
        }
        
        // 如果有其他音频正在播放，先重置其状态
        currentPlayingAudioId?.let { playingId ->
            if (playingId != audioId) {
                resetMarkerToPlayState(playingId)
            }
        }
        
        // 更新当前音频的标记状态
        val marker = audioToMarkerMap[audioId]
        if (marker != null) {
            val iconBitmap = if (isPlaying) playingIconBitmap else playIconBitmap
            marker.setIcon(iconBitmap)
            
            markerStateCache[audioId] = isPlaying
            
            if (isPlaying) {
                currentPlayingAudioId = audioId
            } else if (currentPlayingAudioId == audioId) {
                currentPlayingAudioId = null
            }
            
            Log.d(TAG, "Updated marker state for audio $audioId: isPlaying=$isPlaying")
        } else {
            Log.w(TAG, "Marker not found for audio: $audioId")
        }
    }

    /**
     * 重置标记为播放状态
     */
    private fun resetMarkerToPlayState(audioId: String) {
        val cachedState = markerStateCache[audioId]
        if (cachedState == false) {
            return
        }
        
        val marker = audioToMarkerMap[audioId]
        if (marker != null) {
            marker.setIcon(playIconBitmap)
            markerStateCache[audioId] = false
            Log.d(TAG, "Reset marker to play state for audio: $audioId")
        }
    }

    /**
     * 清除所有标记
     */
    fun clearAllMarkers() {
        Log.d(TAG, "Clearing all audio markers")
        
        markerToAudioMap.keys.forEach { marker ->
            marker.remove()
        }
        
        markerToAudioMap.clear()
        audioToMarkerMap.clear()
        markerStateCache.clear()
        currentAudioData.clear()
        currentPlayingAudioId = null
        
        // 取消待处理的批量更新
        batchUpdateJob?.cancel()
        pendingUpdates.clear()
        
        Log.d(TAG, "Cleared all audio markers")
    }

    /**
     * 更新平均更新时长
     */
    private fun updateAverageUpdateDuration(duration: Long) {
        if (performanceMetrics.totalUpdates == 1) {
            performanceMetrics.averageUpdateDuration = duration
        } else {
            performanceMetrics.averageUpdateDuration = 
                (performanceMetrics.averageUpdateDuration * (performanceMetrics.totalUpdates - 1) + duration) / performanceMetrics.totalUpdates
        }
    }

    /**
     * 获取性能监控数据
     */
    fun getPerformanceMetrics(): PerformanceMetrics = performanceMetrics.copy()

    /**
     * 重置性能监控数据
     */
    fun resetPerformanceMetrics() {
        performanceMetrics.apply {
            totalUpdates = 0
            incrementalUpdates = 0
            markersAdded = 0
            markersRemoved = 0
            markersUpdated = 0
            lastUpdateDuration = 0
            averageUpdateDuration = 0
        }
    }

    // 兼容性方法，保持与原有API的兼容
    fun addAudioMarkersWithLocation(audioRecordsWithLocation: List<AudioRecordWithLocation>) {
        updateAudioMarkersIncremental(audioRecordsWithLocation)
    }

    fun getMarkerForAudio(audioId: String): Marker? = audioToMarkerMap[audioId]
    fun getAudioForMarker(marker: Marker): AudioRecord? = markerToAudioMap[marker]
    fun getCurrentPlayingAudioId(): String? = currentPlayingAudioId
    fun isAudioPlaying(audioId: String): Boolean = currentPlayingAudioId == audioId
    fun getMarkerCount(): Int = markerToAudioMap.size

    /**
     * 清理资源
     */
    fun cleanup() {
        updateScope.cancel()
        clearAllMarkers()
    }
}