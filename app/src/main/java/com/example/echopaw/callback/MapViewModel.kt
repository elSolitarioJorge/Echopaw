package com.example.echopaw.callback

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.echopaw.network.ApiResult
import com.example.echopaw.network.AudioRecord
import com.example.echopaw.network.AudioRecordWithLocation
import com.example.echopaw.network.AutoRetryManager
import com.example.echopaw.network.RetrofitClient
import com.example.echopaw.service.LocationService
import com.example.echopaw.monitor.PerformanceMonitor
import com.example.echopaw.cache.RequestDeduplicationManager
import com.example.echopaw.config.MapInteractionConfig
import com.amap.api.maps.model.LatLng
import kotlinx.coroutines.launch

/**
 * 地图页面ViewModel
 * 
 * 负责管理地图相关的数据和状态，包括：
 * 1. 附近音频记录的获取
 * 2. 位置信息管理
 * 3. 加载状态管理
 */
class MapViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "MapViewModel"
        private const val DEFAULT_RADIUS = 5000.0 // 默认搜索半径5公里
    }
    
    // 附近音频记录
    private val _nearbyAudioRecords = MutableLiveData<List<AudioRecord>>()
    val nearbyAudioRecords: LiveData<List<AudioRecord>> = _nearbyAudioRecords
    
    // LiveData for audio records with location information
    private val _audioRecordsWithLocation = MutableLiveData<List<AudioRecordWithLocation>>()
    val audioRecordsWithLocation: LiveData<List<AudioRecordWithLocation>> = _audioRecordsWithLocation
    
    // 加载状态
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    // 错误信息
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    // 当前位置
    private val _currentLocation = MutableLiveData<LocationService.LocationResult?>()
    val currentLocation: LiveData<LocationService.LocationResult?> = _currentLocation
    
    // 搜索半径
    private val _searchRadius = MutableLiveData<Double>()
    val searchRadius: LiveData<Double> = _searchRadius
    
    // 自动重试管理器
    private val autoRetryManager = AutoRetryManager(MapInteractionConfig())
    
    // 性能监控器
    private val performanceMonitor = PerformanceMonitor(MapInteractionConfig())
    
    // 请求去重管理器
    private val requestDeduplicationManager = RequestDeduplicationManager(MapInteractionConfig())
    
    init {
        _searchRadius.value = DEFAULT_RADIUS
        _nearbyAudioRecords.value = emptyList()
        _isLoading.value = false
        _errorMessage.value = null
    }
    
    /**
     * 设置当前位置
     * 
     * @param location 位置信息
     */
    fun setCurrentLocation(location: LocationService.LocationResult) {
        _currentLocation.value = location
    }
    
    /**
     * 设置搜索半径
     * 
     * @param radius 搜索半径（米）
     */
    fun setSearchRadius(radius: Double) {
        _searchRadius.value = radius
    }
    
    /**
     * 加载附近的音频记录
     * 
     * @param latitude 纬度
     * @param longitude 经度
     * @param radius 搜索半径（米），默认使用当前设置的半径
     * @param forceRefresh 是否强制刷新，默认false
     */
    fun loadNearbyAudioRecords(
        latitude: Double, 
        longitude: Double, 
        radius: Double? = null,
        forceRefresh: Boolean = false
    ) {
        Log.d(TAG, "loadNearbyAudioRecords called with lat: $latitude, lng: $longitude, radius: $radius, forceRefresh: $forceRefresh")
        
        // 如果不是强制刷新且正在加载中，则不重复请求
        if (!forceRefresh && _isLoading.value == true) {
            Log.d(TAG, "Already loading, skipping request")
            return
        }
        
        val searchRadius = radius ?: _searchRadius.value ?: DEFAULT_RADIUS
        Log.d(TAG, "Using search radius: $searchRadius")
        
        val center = LatLng(latitude, longitude)
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            // 开始性能监控
            val timer = performanceMonitor.startTimer("load_nearby_audio")
            
            // 获取requestId
            var requestId: String? = null
            
            // 检查请求去重
            if (!forceRefresh) {
                val cacheResult = requestDeduplicationManager.checkRequest(center, searchRadius)
                when (cacheResult) {
                    is RequestDeduplicationManager.RequestResult.CacheHit -> {
                        Log.d(TAG, "Cache hit! Using cached data with ${cacheResult.data.size} records")
                        _audioRecordsWithLocation.value = cacheResult.data
                        
                        // 从缓存数据中提取基础音频记录
                        val audioRecords = cacheResult.data.map { it.audioRecord }
                        _nearbyAudioRecords.value = audioRecords
                        
                        val duration = timer.stopAndRecord(performanceMonitor)
                        performanceMonitor.recordNetworkRequest(duration, true)
                        
                        _isLoading.value = false
                        return@launch
                    }
                    is RequestDeduplicationManager.RequestResult.PartialHit -> {
                        Log.d(TAG, "Partial cache hit! Using ${cacheResult.cachedData.size} cached records")
                        _audioRecordsWithLocation.value = cacheResult.cachedData
                        
                        // 从缓存数据中提取基础音频记录
                        val audioRecords = cacheResult.cachedData.map { it.audioRecord }
                        _nearbyAudioRecords.value = audioRecords
                        
                        // 继续执行网络请求以获取完整数据，但使用部分缓存的requestId
                        requestId = cacheResult.requestId
                    }
                    is RequestDeduplicationManager.RequestResult.CacheMiss -> {
                        Log.d(TAG, "Cache miss, proceeding with network request")
                        // 继续执行网络请求
                        requestId = cacheResult.requestId
                    }
                }
            }
            
            // 使用AutoRetryManager执行网络请求
            val result = autoRetryManager.executeWithRetry<List<AudioRecord>>(
                operation = {
                    val apiService = RetrofitClient.apiService
                    val response = apiService.getNearbyAudios(
                        latitude = latitude,
                        longitude = longitude,
                        radius = searchRadius.toInt()
                    )
                    
                    if (response.isSuccessful && response.body() != null) {
                        val apiResponse = response.body()!!
                        if (apiResponse.code == 200 && apiResponse.data != null) {
                            apiResponse.data!!
                        } else {
                            throw Exception("API返回错误: ${apiResponse.message}")
                        }
                    } else {
                        throw Exception("网络请求失败: ${response.message()}")
                    }
                }
            )
            
            // 记录性能数据
            val duration = timer.stopAndRecord(performanceMonitor)
            performanceMonitor.recordNetworkRequest(duration, result is AutoRetryManager.RetryResult.Success)
            
            when (result) {
                is AutoRetryManager.RetryResult.Success -> {
                    val audioRecords = result.data
                    _nearbyAudioRecords.value = audioRecords
                    Log.d(TAG, "加载附近音频记录成功，数量: ${audioRecords.size}")
                    
                    // 打印每个音频记录的详细信息
                    audioRecords.forEachIndexed { index, record ->
                        Log.d(TAG, "音频记录[$index]: audioId=${record.audioId}, " +
                                "province=${record.province}, city=${record.city}, " +
                                "district=${record.district}, address=${record.address}, " +
                                "locationName=${record.locationName}, distance=${record.distance}")
                    }
                    
                    // 异步获取每个音频的位置信息
                    val finalRequestId = requestId ?: "req_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}"
                    fetchLocationForAudioRecords(audioRecords, center, searchRadius, finalRequestId)
                }
                is AutoRetryManager.RetryResult.Failure -> {
                    val errorMsg = "加载附近音频记录失败: ${result.error.message}"
                    _errorMessage.value = errorMsg
                    Log.e(TAG, errorMsg, result.error)
                    // 保持之前的数据，不清空
                }
            }
            
            _isLoading.value = false
        }
    }
    
    /**
     * 异步获取音频记录的位置信息
     * 
     * @param audioRecords 基础音频记录列表
     * @param center 请求中心点
     * @param radius 请求半径
     * @param requestId 请求ID
     */
    private fun fetchLocationForAudioRecords(
        audioRecords: List<AudioRecord>,
        center: LatLng,
        radius: Double,
        requestId: String
    ) {
        viewModelScope.launch {
            val timer = performanceMonitor.startTimer("fetch_audio_locations")
            
            // 使用AutoRetryManager的专门方法处理音频数据请求
            val result = autoRetryManager.executeAudioDataRequest(
                operation = {
                    val audioRecordsWithLocation = mutableListOf<AudioRecordWithLocation>()
                    val apiService = RetrofitClient.apiService
                    
                    // 并发获取所有音频的详细信息
                    audioRecords.forEach { audioRecord ->
                        Log.d(TAG, "开始获取音频详情: ${audioRecord.audioId}")
                        
                        val detailResponse = apiService.getAudioDetail(audioRecord.audioId)
                        
                        // 详细记录响应信息
                        Log.d(TAG, "音频详情API响应 - audioId: ${audioRecord.audioId}, " +
                                "isSuccessful: ${detailResponse.isSuccessful}, " +
                                "code: ${detailResponse.code()}, " +
                                "message: ${detailResponse.message()}")
                        
                        val responseBody = detailResponse.body()
                        
                        if (detailResponse.isSuccessful && responseBody?.code == 200) {
                            val audioDetail = responseBody.data
                            audioDetail?.let { detail ->
                                audioRecordsWithLocation.add(
                                    AudioRecordWithLocation(
                                        audioRecord = audioRecord,
                                        location = detail.location
                                    )
                                )
                                Log.d(TAG, "获取音频位置信息成功: ${audioRecord.audioId} -> ${detail.location}")
                            } ?: run {
                                Log.w(TAG, "音频详情数据为空: ${audioRecord.audioId}")
                            }
                        } else {
                            Log.w(TAG, "获取音频详情失败: ${audioRecord.audioId} - " +
                                    "HTTP状态: ${detailResponse.code()}, " +
                                    "响应码: ${responseBody?.code}, " +
                                    "错误信息: ${responseBody?.message}")
                        }
                    }
                    
                    audioRecordsWithLocation
                },
                onProgress = { attempt: Int, total: Int ->
                    Log.d(TAG, "正在重试获取音频位置信息: $attempt/$total")
                },
                onError = { error: Throwable ->
                    Log.e(TAG, "获取音频位置信息失败", error)
                }
            )
            
            // 记录性能数据
            val duration = timer.stopAndRecord(performanceMonitor)
            performanceMonitor.recordNetworkRequest(duration, result is AutoRetryManager.RetryResult.Success)
            
            when (result) {
                is AutoRetryManager.RetryResult.Success -> {
                    val audioRecordsWithLocation = result.data
                    _audioRecordsWithLocation.value = audioRecordsWithLocation
                    Log.d(TAG, "完成位置信息获取，成功数量: ${audioRecordsWithLocation.size}/${audioRecords.size}")
                    
                    // 缓存结果到RequestDeduplicationManager
                    requestDeduplicationManager.cacheResult(requestId, center, radius, audioRecordsWithLocation)
                    Log.d(TAG, "已缓存音频数据到RequestDeduplicationManager")
                }
                is AutoRetryManager.RetryResult.Failure -> {
                    Log.e(TAG, "批量获取音频位置信息失败: ${result.error.message}", result.error)
                    // 即使失败也设置空列表，避免UI显示旧数据
                    _audioRecordsWithLocation.value = emptyList()
                }
            }
        }
    }

    /**
     * 刷新附近音频记录
     *
     * 使用当前位置信息刷新数据
     */
    fun refreshNearbyAudioRecords() {
        _currentLocation.value?.let { location ->
            if (location.errorCode == 0) {
                loadNearbyAudioRecords(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    forceRefresh = true
                )
            } else {
                _errorMessage.value = "位置信息无效，无法刷新音频记录"
            }
        } ?: run {
            _errorMessage.value = "请先获取位置信息"
        }
    }
    
    /**
     * 根据当前位置自动加载附近音频
     * 
     * @param location 位置信息
     */
    fun loadAudioRecordsForLocation(location: LocationService.LocationResult) {
        setCurrentLocation(location)
        if (location.errorCode == 0) {
            loadNearbyAudioRecords(location.latitude, location.longitude)
        } else {
            _errorMessage.value = "位置获取失败: ${location.errorInfo}"
        }
    }
    
    /**
     * 添加新的音频记录到列表
     * 
     * @param audioRecord 新的音频记录
     */
    fun addAudioRecord(audioRecord: AudioRecord) {
        val currentList = _nearbyAudioRecords.value?.toMutableList() ?: mutableListOf()
        
        // 检查是否已存在
        val existingIndex = currentList.indexOfFirst { it.audioId == audioRecord.audioId }
        if (existingIndex >= 0) {
            // 更新现有记录
            currentList[existingIndex] = audioRecord
        } else {
            // 添加新记录
            currentList.add(audioRecord)
        }
        
        _nearbyAudioRecords.value = currentList
    }
    
    /**
     * 移除音频记录
     * 
     * @param audioId 音频ID
     */
    fun removeAudioRecord(audioId: String) {
        val currentList = _nearbyAudioRecords.value?.toMutableList() ?: return
        val updatedList = currentList.filter { it.audioId != audioId }
        _nearbyAudioRecords.value = updatedList
    }
    
    /**
     * 更新音频记录
     * 
     * @param audioRecord 更新的音频记录
     */
    fun updateAudioRecord(audioRecord: AudioRecord) {
        val currentList = _nearbyAudioRecords.value?.toMutableList() ?: return
        val index = currentList.indexOfFirst { it.audioId == audioRecord.audioId }
        if (index >= 0) {
            currentList[index] = audioRecord
            _nearbyAudioRecords.value = currentList
        }
    }
    
    /**
     * 获取指定ID的音频记录
     * 
     * @param audioId 音频ID
     * @return 音频记录，如果不存在则返回null
     */
    fun getAudioRecord(audioId: String): AudioRecord? {
        return _nearbyAudioRecords.value?.find { it.audioId == audioId }
    }
    
    /**
     * 清除错误信息
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
    
    /**
     * 清除所有数据
     */
    fun clearData() {
        _nearbyAudioRecords.value = emptyList()
        _currentLocation.value = null
        _errorMessage.value = null
        _isLoading.value = false
    }
    
    /**
     * 获取当前音频记录数量
     */
    fun getAudioRecordCount(): Int {
        return _nearbyAudioRecords.value?.size ?: 0
    }
    
    /**
     * 检查是否有音频记录
     */
    fun hasAudioRecords(): Boolean {
        return getAudioRecordCount() > 0
    }
}