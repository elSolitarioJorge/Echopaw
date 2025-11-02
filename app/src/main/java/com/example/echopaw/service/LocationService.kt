package com.example.echopaw.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * GPS定位服务
 * 
 * 基于高德地图SDK提供定位功能，包括：
 * 1. 单次定位
 * 2. 持续定位
 * 3. 权限检查
 * 4. 定位精度配置
 */
class LocationService(private val context: Context) {
    
    private var locationClient: AMapLocationClient? = null
    private var locationOption: AMapLocationClientOption? = null
    
    /**
     * 定位结果数据类
     * 
     * @property latitude 纬度
     * @property longitude 经度
     * @property accuracy 精度（米）
     * @property address 地址信息
     * @property errorCode 错误码，0表示成功
     * @property errorInfo 错误信息
     */
    data class LocationResult(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val address: String?,
        val errorCode: Int,
        val errorInfo: String?
    )
    
    /**
     * 定位监听器接口
     */
    interface LocationListener {
        fun onLocationChanged(result: LocationResult)
        fun onLocationError(errorCode: Int, errorInfo: String)
    }
    
    init {
        initLocationClient()
    }
    
    /**
     * 初始化定位客户端
     */
    private fun initLocationClient() {
        try {
            locationClient = AMapLocationClient(context)
            locationOption = AMapLocationClientOption().apply {
                // 设置定位模式为高精度模式
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                // 设置是否返回地址信息
                isNeedAddress = true
                // 设置是否只定位一次
                isOnceLocation = false
                // 设置是否强制刷新WIFI
                isWifiActiveScan = true
                // 设置是否允许模拟位置
                isMockEnable = false
                // 设置定位间隔
                interval = 2000
                // 设置定位超时时间
                httpTimeOut = 30000
            }
            locationClient?.setLocationOption(locationOption)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 检查定位权限
     * 
     * @return 是否有定位权限
     */
    fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 获取当前位置（单次定位）
     * 
     * @return 定位结果
     */
    suspend fun getCurrentLocation(): LocationResult = suspendCancellableCoroutine { continuation ->
        if (!hasLocationPermission()) {
            continuation.resume(
                LocationResult(
                    0.0, 0.0, 0f, null,
                    -1, "缺少定位权限"
                )
            )
            return@suspendCancellableCoroutine
        }
        
        val onceLocationOption = AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            isNeedAddress = true
            isOnceLocation = true
            isWifiActiveScan = true
            isMockEnable = false
            httpTimeOut = 30000
        }
        
        val onceLocationClient = AMapLocationClient(context)
        onceLocationClient.setLocationOption(onceLocationOption)
        
        val locationListener = AMapLocationListener { location ->
            val result = if (location != null && location.errorCode == 0) {
                LocationResult(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    address = location.address,
                    errorCode = location.errorCode,
                    errorInfo = location.errorInfo
                )
            } else {
                LocationResult(
                    0.0, 0.0, 0f, null,
                    location?.errorCode ?: -1,
                    location?.errorInfo ?: "定位失败"
                )
            }
            
            onceLocationClient.stopLocation()
            onceLocationClient.onDestroy()
            
            if (continuation.isActive) {
                continuation.resume(result)
            }
        }
        
        onceLocationClient.setLocationListener(locationListener)
        onceLocationClient.startLocation()
        
        continuation.invokeOnCancellation {
            onceLocationClient.stopLocation()
            onceLocationClient.onDestroy()
        }
    }
    
    /**
     * 开始持续定位
     * 
     * @param listener 定位监听器
     */
    fun startLocation(listener: LocationListener) {
        if (!hasLocationPermission()) {
            listener.onLocationError(-1, "缺少定位权限")
            return
        }
        
        val locationListener = AMapLocationListener { location ->
            if (location != null && location.errorCode == 0) {
                val result = LocationResult(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    address = location.address,
                    errorCode = location.errorCode,
                    errorInfo = location.errorInfo
                )
                listener.onLocationChanged(result)
            } else {
                listener.onLocationError(
                    location?.errorCode ?: -1,
                    location?.errorInfo ?: "定位失败"
                )
            }
        }
        
        locationClient?.setLocationListener(locationListener)
        locationClient?.startLocation()
    }
    
    /**
     * 停止定位
     */
    fun stopLocation() {
        locationClient?.stopLocation()
    }
    
    /**
     * 销毁定位客户端
     */
    fun destroy() {
        locationClient?.stopLocation()
        locationClient?.onDestroy()
        locationClient = null
    }
    
    /**
     * 计算两点间距离
     * 
     * @param lat1 第一个点的纬度
     * @param lon1 第一个点的经度
     * @param lat2 第二个点的纬度
     * @param lon2 第二个点的经度
     * @return 距离（米）
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
    
    /**
     * 检查位置是否有效
     * 
     * @param latitude 纬度
     * @param longitude 经度
     * @return 是否有效
     */
    fun isValidLocation(latitude: Double, longitude: Double): Boolean {
        return latitude != 0.0 && longitude != 0.0 &&
                latitude >= -90 && latitude <= 90 &&
                longitude >= -180 && longitude <= 180
    }
}