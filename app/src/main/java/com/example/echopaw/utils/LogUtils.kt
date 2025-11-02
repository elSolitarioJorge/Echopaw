package com.example.echopaw.utils

import android.util.Log

/**
 * 日志工具类
 * 
 * 提供统一的日志记录接口，支持不同级别的日志输出和格式化。
 * 在Release版本中可以控制日志输出，避免敏感信息泄露。
 * 
 * 主要功能：
 * - 统一的日志记录接口
 * - 支持Debug、Info、Warning、Error级别
 * - 自动格式化日志消息
 * - 支持异常堆栈跟踪
 * - Release版本日志控制
 * - 性能监控日志
 * 
 * 使用示例：
 * ```kotlin
 * class MyClass {
 *     companion object {
 *         private const val TAG = "MyClass"
 *     }
 *     
 *     fun someMethod() {
 *         LogUtils.d(TAG, "Method started")
 *         LogUtils.i(TAG, "Processing data: ${data.size} items")
 *         
 *         try {
 *             // some operation
 *             LogUtils.d(TAG, "Operation completed successfully")
 *         } catch (e: Exception) {
 *             LogUtils.e(TAG, "Operation failed", e)
 *         }
 *     }
 * }
 * ```
 * 
 * 性能监控示例：
 * ```kotlin
 * LogUtils.Performance.start(TAG, "data_loading")
 * // ... 执行耗时操作
 * LogUtils.Performance.end(TAG, "data_loading")
 * ```
 * 
 * 注意事项：
 * - 在Release版本中，Debug和Info级别的日志可能被禁用
 * - 避免在日志中记录敏感信息（密码、token等）
 * - 大量日志输出可能影响性能
 * 
 * @author EchoPaw Team
 * @since 1.0
 */
object LogUtils {
    
    /**
     * 是否启用调试日志
     * 在Release版本中应设置为false
     */
    private const val DEBUG_ENABLED = true
    
    /**
     * 输出Debug级别日志
     * @param tag 日志标签
     * @param message 日志消息
     */
    fun d(tag: String, message: String) {
        if (DEBUG_ENABLED) {
            Log.d(tag, message)
        }
    }
    
    /**
     * 输出Info级别日志
     * @param tag 日志标签
     * @param message 日志消息
     */
    fun i(tag: String, message: String) {
        if (DEBUG_ENABLED) {
            Log.i(tag, message)
        }
    }
    
    /**
     * 输出Warning级别日志
     * @param tag 日志标签
     * @param message 日志消息
     */
    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }
    
    /**
     * 输出Warning级别日志（带异常）
     * @param tag 日志标签
     * @param message 日志消息
     * @param throwable 异常对象
     */
    fun w(tag: String, message: String, throwable: Throwable) {
        Log.w(tag, message, throwable)
    }
    
    /**
     * 输出Error级别日志
     * @param tag 日志标签
     * @param message 日志消息
     */
    fun e(tag: String, message: String) {
        Log.e(tag, message)
    }
    
    /**
     * 输出Error级别日志（带异常）
     * @param tag 日志标签
     * @param message 日志消息
     * @param throwable 异常对象
     */
    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
    }
    
    /**
     * 性能监控相关日志
     */
    object Performance {
        private val startTimes = mutableMapOf<String, Long>()
        
        /**
         * 开始性能计时
         * @param tag 日志标签
         * @param operation 操作名称
         */
        fun start(tag: String, operation: String) {
            val key = "$tag:$operation"
            startTimes[key] = System.currentTimeMillis()
            d(tag, "Performance: Starting '$operation'")
        }
        
        /**
         * 结束性能计时并输出耗时
         * @param tag 日志标签
         * @param operation 操作名称
         */
        fun end(tag: String, operation: String) {
            val key = "$tag:$operation"
            val startTime = startTimes.remove(key)
            if (startTime != null) {
                val duration = System.currentTimeMillis() - startTime
                d(tag, "Performance: '$operation' completed in ${duration}ms")
            } else {
                w(tag, "Performance: No start time found for operation '$operation'")
            }
        }
        
        /**
         * 记录操作耗时
         * @param tag 日志标签
         * @param operation 操作名称
         * @param duration 耗时（毫秒）
         */
        fun log(tag: String, operation: String, duration: Long) {
            d(tag, "Performance: '$operation' took ${duration}ms")
        }
    }
    
    /**
     * 网络相关日志
     */
    object Network {
        fun requestStart(tag: String, url: String) {
            d(tag, "Network: Starting request to $url")
        }
        
        fun requestSuccess(tag: String, url: String, duration: Long) {
            d(tag, "Network: Request to $url succeeded in ${duration}ms")
        }
        
        fun requestFailed(tag: String, url: String, error: String) {
            e(tag, "Network: Request to $url failed - $error")
        }
        
        fun requestFailed(tag: String, url: String, error: String, throwable: Throwable) {
            e(tag, "Network: Request to $url failed - $error", throwable)
        }
    }
    
    /**
     * 音频相关日志
     */
    object Audio {
        fun playbackStart(tag: String, audioId: String) {
            d(tag, "Audio: Playback started for $audioId")
        }
        
        fun playbackPause(tag: String, audioId: String) {
            d(tag, "Audio: Playback paused for $audioId")
        }
        
        fun playbackComplete(tag: String, audioId: String) {
            d(tag, "Audio: Playback completed for $audioId")
        }
        
        fun playbackError(tag: String, audioId: String, error: String) {
            e(tag, "Audio: Playback error for $audioId - $error")
        }
        
        fun recordingStart(tag: String, outputFile: String) {
            d(tag, "Audio: Recording started, output: $outputFile")
        }
        
        fun recordingStop(tag: String, outputFile: String?) {
            d(tag, "Audio: Recording stopped, output: $outputFile")
        }
        
        fun recordingError(tag: String, error: String) {
            e(tag, "Audio: Recording error - $error")
        }
    }
    
    /**
     * 位置相关日志
     */
    object Location {
        fun locationUpdate(tag: String, latitude: Double, longitude: Double, accuracy: Float) {
            d(tag, "Location: Updated to ($latitude, $longitude) with accuracy ${accuracy}m")
        }
        
        fun locationError(tag: String, errorCode: Int, errorInfo: String) {
            e(tag, "Location: Error $errorCode - $errorInfo")
        }
        
        fun geocodingStart(tag: String, latitude: Double, longitude: Double) {
            d(tag, "Location: Starting geocoding for ($latitude, $longitude)")
        }
        
        fun geocodingSuccess(tag: String, latitude: Double, longitude: Double, address: String) {
            d(tag, "Location: Geocoding success for ($latitude, $longitude) -> $address")
        }
        
        fun geocodingError(tag: String, latitude: Double, longitude: Double, error: String) {
            e(tag, "Location: Geocoding failed for ($latitude, $longitude) - $error")
        }
    }
    
    /**
     * 导航相关日志
     */
    object Navigation {
        fun fragmentNavigation(tag: String, from: String, to: String) {
            d(tag, "Navigation: From $from to $to")
        }
        
        fun navigationError(tag: String, error: String) {
            e(tag, "Navigation: Error - $error")
        }
        
        fun navigationError(tag: String, error: String, throwable: Throwable) {
            e(tag, "Navigation: Error - $error", throwable)
        }
    }
}