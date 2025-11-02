package com.example.echopaw.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * 时间工具类
 * 
 * 提供时间格式化和相对时间计算功能，包括：
 * 1. 格式化时间为"yyyy-MM-dd HH:mm"格式
 * 2. 计算相对时间（X分钟/小时/天前）
 * 3. 时间字符串解析
 * 4. 实时更新支持
 */
object TimeUtils {

    private const val TAG = "TimeUtils"
    
    // 时间格式
    private const val DISPLAY_FORMAT = "yyyy.MM.dd HH:mm"
    private const val ISO_FORMAT = "yyyy.MM.dd'T'HH:mm:ss.SSS'Z'"
    private const val ISO_FORMAT_NO_MILLIS = "yyyy.MM.dd'T'HH:mm:ss'Z'"
    
    // 时间常量（毫秒）
    private const val MINUTE_IN_MILLIS = 60 * 1000L
    private const val HOUR_IN_MILLIS = 60 * MINUTE_IN_MILLIS
    private const val DAY_IN_MILLIS = 24 * HOUR_IN_MILLIS
    private const val WEEK_IN_MILLIS = 7 * DAY_IN_MILLIS
    private const val MONTH_IN_MILLIS = 30 * DAY_IN_MILLIS
    private const val YEAR_IN_MILLIS = 365 * DAY_IN_MILLIS

    /**
     * 格式化时间戳为显示格式
     * 
     * @param timestamp 时间戳（毫秒）
     * @return 格式化后的时间字符串 "yyyy.MM.dd HH:mm"
     */
    fun formatDisplayTime(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat(DISPLAY_FORMAT, Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting display time: ${e.message}", e)
            "时间格式错误"
        }
    }

    /**
     * 格式化时间字符串为显示格式
     * 
     * @param timeString 时间字符串（ISO格式或其他格式）
     * @return 格式化后的时间字符串 "yyyy.MM.dd HH:mm"
     */
    fun formatDisplayTime(timeString: String): String {
        return try {
            val timestamp = parseTimeString(timeString)
            formatDisplayTime(timestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting display time from string: ${e.message}", e)
            timeString // 返回原始字符串
        }
    }

    /**
     * 计算相对时间（X分钟/小时/天前）
     * 
     * @param timestamp 时间戳（毫秒）
     * @return 相对时间字符串
     */
    fun getRelativeTime(timestamp: Long): String {
        return try {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            
            when {
                diff < 0 -> "刚刚" // 未来时间
                diff < MINUTE_IN_MILLIS -> "刚刚"
                diff < HOUR_IN_MILLIS -> {
                    val minutes = diff / MINUTE_IN_MILLIS
                    "发布于${minutes}分钟前"
                }
                diff < DAY_IN_MILLIS -> {
                    val hours = diff / HOUR_IN_MILLIS
                    "发布于${hours}小时前"
                }
                diff < WEEK_IN_MILLIS -> {
                    val days = diff / DAY_IN_MILLIS
                    "发布于${days}天前"
                }
                diff < MONTH_IN_MILLIS -> {
                    val weeks = diff / WEEK_IN_MILLIS
                    "发布于${weeks}周前"
                }
                diff < YEAR_IN_MILLIS -> {
                    val months = diff / MONTH_IN_MILLIS
                    "发布于${months}个月前"
                }
                else -> {
                    val years = diff / YEAR_IN_MILLIS
                    "发布于${years}年前"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating relative time: ${e.message}", e)
            "时间未知"
        }
    }

    /**
     * 计算相对时间（从时间字符串）
     * 
     * @param timeString 时间字符串
     * @return 相对时间字符串
     */
    fun getRelativeTime(timeString: String): String {
        return try {
            val timestamp = parseTimeString(timeString)
            getRelativeTime(timestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating relative time from string: ${e.message}", e)
            "时间未知"
        }
    }

    /**
     * 解析时间字符串为时间戳
     * 
     * @param timeString 时间字符串
     * @return 时间戳（毫秒）
     */
    fun parseTimeString(timeString: String): Long {
        // 增强参数验证，提供更友好的错误处理
        if (timeString.isBlank()) {
            Log.w(TAG, "Time string is blank, using current time")
            return System.currentTimeMillis()
        }
        
        // 检查字符串长度，避免过长的无效输入
        if (timeString.length > 50) {
            Log.w(TAG, "Time string too long: ${timeString.length} chars, using current time")
            return System.currentTimeMillis()
        }
        
        // 尝试不同的时间格式
        val formats = listOf(
            ISO_FORMAT,
            ISO_FORMAT_NO_MILLIS,
            DISPLAY_FORMAT,
            "yyyy.MM.dd HH:mm:ss",
            "yyyy.MM.dd",
            "MM.dd HH:mm",
            "HH:mm:ss"
        )
        
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC") // 假设服务器时间为UTC
                return sdf.parse(timeString)?.time ?: throw Exception("Parse result is null")
            } catch (e: Exception) {
                // 继续尝试下一个格式
                continue
            }
        }
        
        // 如果所有格式都失败，尝试直接解析为长整型（时间戳）
        try {
            return timeString.toLong()
        } catch (e: NumberFormatException) {
            // 最后尝试：假设是当前时间
            Log.w(TAG, "Unable to parse time string: $timeString, using current time")
            return System.currentTimeMillis()
        }
    }

    /**
     * 检查时间是否需要更新
     * 
     * @param lastUpdateTime 上次更新时间
     * @param updateInterval 更新间隔（毫秒）
     * @return 是否需要更新
     */
    fun shouldUpdate(lastUpdateTime: Long, updateInterval: Long = MINUTE_IN_MILLIS): Boolean {
        return System.currentTimeMillis() - lastUpdateTime >= updateInterval
    }

    /**
     * 获取下次更新的延迟时间
     * 
     * @param timestamp 目标时间戳
     * @return 延迟时间（毫秒），用于定时更新
     */
    fun getNextUpdateDelay(timestamp: Long): Long {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < HOUR_IN_MILLIS -> {
                // 1小时内，每分钟更新
                MINUTE_IN_MILLIS - (diff % MINUTE_IN_MILLIS)
            }
            diff < DAY_IN_MILLIS -> {
                // 1天内，每小时更新
                HOUR_IN_MILLIS - (diff % HOUR_IN_MILLIS)
            }
            else -> {
                // 超过1天，每天更新
                DAY_IN_MILLIS - (diff % DAY_IN_MILLIS)
            }
        }
    }

    /**
     * 格式化持续时间（用于音频播放时长）
     * 
     * @param durationMs 持续时间（毫秒）
     * @return 格式化的时长字符串 "mm:ss" 或 "hh:mm:ss"
     */
    fun formatDuration(durationMs: Int): String {
        return try {
            val totalSeconds = durationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            
            if (hours > 0) {
                String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting duration: ${e.message}", e)
            "00:00"
        }
    }

    /**
     * 获取当前时间戳
     */
    fun getCurrentTimestamp(): Long = System.currentTimeMillis()

    /**
     * 检查时间字符串是否有效
     */
    fun isValidTimeString(timeString: String): Boolean {
        return try {
            parseTimeString(timeString)
            true
        } catch (e: Exception) {
            false
        }
    }
}