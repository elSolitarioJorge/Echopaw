package com.example.echopaw.utils

import android.content.Context
import android.util.Log
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.maps.model.LatLng
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 地址解析工具类
 * 
 * 提供地理坐标与地址信息的转换功能，包括：
 * 1. 坐标转地址（逆地理编码）
 * 2. 地址格式化为"省市•城市•区县"格式
 * 3. 异步地址解析
 * 4. 地址缓存机制
 */
object LocationUtils {

    private const val TAG = "LocationUtils"
    
    // 地址缓存，避免重复请求相同坐标的地址
    private val addressCache = mutableMapOf<String, String>()
    
    // 缓存过期时间（毫秒）
    private const val CACHE_EXPIRE_TIME = 30 * 60 * 1000L // 30分钟

    /**
     * 地址解析结果回调
     */
    interface AddressCallback {
        fun onAddressResolved(address: String)
        fun onAddressError(error: String)
    }

    /**
     * 异步解析坐标为地址
     * 
     * @param context 上下文
     * @param latitude 纬度
     * @param longitude 经度
     * @return 格式化的地址字符串
     */
    suspend fun resolveAddress(context: Context, latitude: Double, longitude: Double): String {
        return suspendCancellableCoroutine { continuation ->
            try {
                // 检查缓存
                val cacheKey = "${latitude}_${longitude}"
                val cachedAddress = addressCache[cacheKey]
                if (cachedAddress != null) {
                    Log.d(TAG, "Using cached address for ($latitude, $longitude): $cachedAddress")
                    continuation.resume(cachedAddress)
                    return@suspendCancellableCoroutine
                }
                
                val geocodeSearch = GeocodeSearch(context)
                geocodeSearch.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                    override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
                        try {
                            if (rCode == 1000 && result != null && result.regeocodeAddress != null) {
                                val address = formatAddress(result.regeocodeAddress)
                                // 缓存地址
                                addressCache[cacheKey] = address
                                Log.d(TAG, "Resolved address for ($latitude, $longitude): $address")
                                continuation.resume(address)
                            } else {
                                val errorMsg = "地址解析失败，错误码: $rCode"
                                Log.e(TAG, errorMsg)
                                continuation.resume("位置未知")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in onRegeocodeSearched: ${e.message}", e)
                            continuation.resume("位置解析异常")
                        }
                    }

                    override fun onGeocodeSearched(result: GeocodeResult?, rCode: Int) {
                        // 不需要实现
                    }
                })
                
                // 执行逆地理编码查询
                val latLng = LatLng(latitude, longitude)
                val query = RegeocodeQuery(
                    com.amap.api.services.core.LatLonPoint(latitude, longitude),
                    200f, // 搜索半径
                    GeocodeSearch.AMAP // 使用高德地图
                )
                
                geocodeSearch.getFromLocationAsyn(query)
                
                // 设置取消回调
                continuation.invokeOnCancellation {
                    try {
                        geocodeSearch.setOnGeocodeSearchListener(null)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error cancelling geocode search: ${e.message}")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving address: ${e.message}", e)
                continuation.resume("位置解析失败")
            }
        }
    }

    /**
     * 格式化地址为"省市•城市•区县"格式
     * 
     * @param regeocodeAddress 逆地理编码地址对象
     * @return 格式化的地址字符串
     */
    private fun formatAddress(regeocodeAddress: com.amap.api.services.geocoder.RegeocodeAddress): String {
        return try {
            val province = regeocodeAddress.province ?: ""
            val city = regeocodeAddress.city ?: ""
            val district = regeocodeAddress.district ?: ""
            
            // 处理直辖市的情况（省市相同）
            val formattedCity = if (province == city || city.isEmpty()) {
                province
            } else {
                city
            }
            
            // 构建地址字符串
            val addressParts = mutableListOf<String>()
            
            if (province.isNotEmpty()) {
                addressParts.add(province)
            }
            
            if (formattedCity.isNotEmpty() && formattedCity != province) {
                addressParts.add(formattedCity)
            }
            
            if (district.isNotEmpty()) {
                addressParts.add(district)
            }
            
            // 如果没有任何地址信息，尝试使用格式化地址
            if (addressParts.isEmpty()) {
                val formattedAddress = regeocodeAddress.formatAddress
                if (!formattedAddress.isNullOrEmpty()) {
                    return simplifyFormattedAddress(formattedAddress)
                }
                return "位置未知"
            }
            
            // 用"•"连接地址部分
            val result = addressParts.joinToString("•")
            Log.d(TAG, "Formatted address: $result (from province=$province, city=$city, district=$district)")
            
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting address: ${e.message}", e)
            "地址格式化失败"
        }
    }

    /**
     * 简化格式化地址
     * 
     * @param formattedAddress 完整的格式化地址
     * @return 简化的地址
     */
    private fun simplifyFormattedAddress(formattedAddress: String): String {
        return try {
            // 提取主要地址部分（省市区）
            val parts = formattedAddress.split("省", "市", "区", "县", "镇", "街道", "路", "号")
            val mainParts = mutableListOf<String>()
            
            for (i in 0 until minOf(3, parts.size)) {
                val part = parts[i].trim()
                if (part.isNotEmpty()) {
                    mainParts.add(part)
                }
            }
            
            if (mainParts.isNotEmpty()) {
                return mainParts.joinToString("•")
            }
            
            // 如果无法解析，返回前50个字符
            return if (formattedAddress.length > 50) {
                formattedAddress.substring(0, 50) + "..."
            } else {
                formattedAddress
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error simplifying formatted address: ${e.message}", e)
            formattedAddress
        }
    }

    /**
     * 使用回调方式解析地址（兼容旧代码）
     * 
     * @param context 上下文
     * @param latitude 纬度
     * @param longitude 经度
     * @param callback 回调接口
     */
    fun resolveAddressAsync(context: Context, latitude: Double, longitude: Double, callback: AddressCallback) {
        try {
            // 检查缓存
            val cacheKey = "${latitude}_${longitude}"
            val cachedAddress = addressCache[cacheKey]
            if (cachedAddress != null) {
                Log.d(TAG, "Using cached address for ($latitude, $longitude): $cachedAddress")
                callback.onAddressResolved(cachedAddress)
                return
            }
            
            val geocodeSearch = GeocodeSearch(context)
            geocodeSearch.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
                    try {
                        if (rCode == 1000 && result != null && result.regeocodeAddress != null) {
                            val address = formatAddress(result.regeocodeAddress)
                            // 缓存地址
                            addressCache[cacheKey] = address
                            Log.d(TAG, "Resolved address for ($latitude, $longitude): $address")
                            callback.onAddressResolved(address)
                        } else {
                            val errorMsg = "地址解析失败，错误码: $rCode"
                            Log.e(TAG, errorMsg)
                            callback.onAddressError(errorMsg)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onRegeocodeSearched: ${e.message}", e)
                        callback.onAddressError("地址解析异常: ${e.message}")
                    }
                }

                override fun onGeocodeSearched(result: GeocodeResult?, rCode: Int) {
                    // 不需要实现
                }
            })
            
            // 执行逆地理编码查询
            val query = RegeocodeQuery(
                com.amap.api.services.core.LatLonPoint(latitude, longitude),
                200f, // 搜索半径
                GeocodeSearch.AMAP // 使用高德地图
            )
            
            geocodeSearch.getFromLocationAsyn(query)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving address async: ${e.message}", e)
            callback.onAddressError("地址解析失败: ${e.message}")
        }
    }

    /**
     * 清理过期的地址缓存
     */
    fun clearExpiredCache() {
        // 简单实现：清空所有缓存
        // 在实际应用中，可以添加时间戳来实现真正的过期清理
        if (addressCache.size > 100) { // 限制缓存大小
            addressCache.clear()
            Log.d(TAG, "Cleared address cache")
        }
    }

    /**
     * 计算两点之间的距离（米）
     * 
     * @param lat1 第一个点的纬度
     * @param lon1 第一个点的经度
     * @param lat2 第二个点的纬度
     * @param lon2 第二个点的经度
     * @return 距离（米）
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // 地球半径（米）
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return earthRadius * c
    }

    /**
     * 格式化距离显示
     * 
     * @param distanceInMeters 距离（米）
     * @return 格式化的距离字符串
     */
    fun formatDistance(distanceInMeters: Double): String {
        return when {
            distanceInMeters < 1000 -> "${distanceInMeters.toInt()}m"
            distanceInMeters < 10000 -> String.format("%.1fkm", distanceInMeters / 1000)
            else -> "${(distanceInMeters / 1000).toInt()}km"
        }
    }
}