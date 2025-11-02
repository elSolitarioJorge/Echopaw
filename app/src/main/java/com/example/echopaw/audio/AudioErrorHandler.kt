package com.example.echopaw.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 音频错误处理和用户反馈管理器
 * 
 * 负责统一处理音频相关的错误，提供用户友好的错误信息和反馈。
 * 实现单例模式，确保全局错误处理的一致性。
 * 
 * 主要功能：
 * 1. 错误分类和处理 - 将技术错误转换为用户友好的信息
 * 2. 用户反馈管理 - 统一的Toast、对话框和通知机制
 * 3. 错误日志记录 - 详细的错误日志用于调试
 * 4. 错误恢复建议 - 为用户提供解决方案
 * 5. 错误统计和分析 - 收集错误数据用于改进
 * 
 * 技术特性：
 * - 单例模式，全局统一错误处理
 * - SharedFlow事件流，支持多个监听器
 * - 错误分类和优先级管理
 * - 自动错误恢复机制
 * - 用户体验优化的错误提示
 * 
 * 使用示例：
 * ```kotlin
 * // 获取错误处理器实例
 * val errorHandler = AudioErrorHandler.getInstance()
 * 
 * // 处理播放错误
 * errorHandler.handlePlaybackError(
 *     context = context,
 *     audioId = "audio_123",
 *     error = exception,
 *     showToUser = true
 * )
 * 
 * // 处理录音错误
 * errorHandler.handleRecordingError(
 *     context = context,
 *     sessionId = "session_456",
 *     error = exception,
 *     canRetry = true
 * )
 * 
 * // 监听错误事件
 * errorHandler.errorEvents.collect { event ->
 *     when (event.type) {
 *         AudioErrorType.PLAYBACK_FAILED -> {
 *             // 处理播放失败
 *         }
 *         AudioErrorType.RECORDING_FAILED -> {
 *             // 处理录音失败
 *         }
 *     }
 * }
 * ```
 * 
 * @author EchoPaw Team
 * @since 1.0
 */
class AudioErrorHandler private constructor() {

    companion object {
        private const val TAG = "AudioErrorHandler"
        
        @Volatile
        private var INSTANCE: AudioErrorHandler? = null
        
        /**
         * 获取音频错误处理器单例实例
         */
        fun getInstance(): AudioErrorHandler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioErrorHandler().also { INSTANCE = it }
            }
        }
    }

    /**
     * 音频错误类型枚举
     */
    enum class AudioErrorType {
        // 播放相关错误
        PLAYBACK_FAILED,           // 播放失败
        PLAYBACK_INTERRUPTED,      // 播放中断
        AUDIO_FILE_NOT_FOUND,      // 音频文件未找到
        AUDIO_FORMAT_UNSUPPORTED,  // 音频格式不支持
        NETWORK_ERROR,             // 网络错误
        
        // 录音相关错误
        RECORDING_FAILED,          // 录音失败
        RECORDING_INTERRUPTED,     // 录音中断
        MICROPHONE_UNAVAILABLE,    // 麦克风不可用
        STORAGE_INSUFFICIENT,      // 存储空间不足
        PERMISSION_DENIED,         // 权限被拒绝
        
        // 系统相关错误
        AUDIO_FOCUS_LOST,          // 音频焦点丢失
        HARDWARE_ERROR,            // 硬件错误
        UNKNOWN_ERROR              // 未知错误
    }

    /**
     * 错误严重程度枚举
     */
    enum class ErrorSeverity {
        LOW,      // 低严重程度，不影响核心功能
        MEDIUM,   // 中等严重程度，影响部分功能
        HIGH,     // 高严重程度，影响核心功能
        CRITICAL  // 严重错误，需要立即处理
    }

    /**
     * 音频错误事件数据类
     */
    data class AudioErrorEvent(
        val type: AudioErrorType,
        val severity: ErrorSeverity,
        val message: String,
        val technicalDetails: String,
        val audioId: String? = null,
        val sessionId: String? = null,
        val timestamp: Long = System.currentTimeMillis(),
        val canRetry: Boolean = false,
        val suggestedAction: String? = null
    )

    // 错误事件流
    private val _errorEvents = MutableSharedFlow<AudioErrorEvent>()
    val errorEvents: SharedFlow<AudioErrorEvent> = _errorEvents.asSharedFlow()

    // 错误统计
    private val errorStats = mutableMapOf<AudioErrorType, Int>()

    /**
     * 处理播放错误
     * 
     * @param context Android上下文
     * @param audioId 音频ID
     * @param error 异常对象
     * @param showToUser 是否向用户显示错误信息
     */
    fun handlePlaybackError(
        context: Context,
        audioId: String,
        error: Throwable,
        showToUser: Boolean = true
    ) {
        val errorType = classifyPlaybackError(error)
        val severity = getErrorSeverity(errorType)
        val userMessage = getUserFriendlyMessage(context, errorType)
        val suggestedAction = getSuggestedAction(context, errorType)
        
        val errorEvent = AudioErrorEvent(
            type = errorType,
            severity = severity,
            message = userMessage,
            technicalDetails = error.message ?: "Unknown error",
            audioId = audioId,
            canRetry = canRetryPlayback(errorType),
            suggestedAction = suggestedAction
        )
        
        // 记录错误日志
        logError(errorEvent, error)
        
        // 更新错误统计
        updateErrorStats(errorType)
        
        // 发送错误事件
        _errorEvents.tryEmit(errorEvent)
        
        // 向用户显示错误信息
        if (showToUser) {
            showErrorToUser(context, errorEvent)
        }
    }

    /**
     * 处理录音错误
     * 
     * @param context Android上下文
     * @param sessionId 录音会话ID
     * @param error 异常对象
     * @param canRetry 是否可以重试
     */
    fun handleRecordingError(
        context: Context,
        sessionId: String,
        error: Throwable,
        canRetry: Boolean = false
    ) {
        val errorType = classifyRecordingError(error)
        val severity = getErrorSeverity(errorType)
        val userMessage = getUserFriendlyMessage(context, errorType)
        val suggestedAction = getSuggestedAction(context, errorType)
        
        val errorEvent = AudioErrorEvent(
            type = errorType,
            severity = severity,
            message = userMessage,
            technicalDetails = error.message ?: "Unknown error",
            sessionId = sessionId,
            canRetry = canRetry && canRetryRecording(errorType),
            suggestedAction = suggestedAction
        )
        
        // 记录错误日志
        logError(errorEvent, error)
        
        // 更新错误统计
        updateErrorStats(errorType)
        
        // 发送错误事件
        _errorEvents.tryEmit(errorEvent)
        
        // 向用户显示错误信息
        showErrorToUser(context, errorEvent)
    }

    /**
     * 分类播放错误
     */
    private fun classifyPlaybackError(error: Throwable): AudioErrorType {
        return when {
            error is java.io.FileNotFoundException -> AudioErrorType.AUDIO_FILE_NOT_FOUND
            error is java.net.UnknownHostException -> AudioErrorType.NETWORK_ERROR
            error is java.net.SocketTimeoutException -> AudioErrorType.NETWORK_ERROR
            error.message?.contains("unsupported", ignoreCase = true) == true -> AudioErrorType.AUDIO_FORMAT_UNSUPPORTED
            error.message?.contains("interrupted", ignoreCase = true) == true -> AudioErrorType.PLAYBACK_INTERRUPTED
            else -> AudioErrorType.PLAYBACK_FAILED
        }
    }

    /**
     * 分类录音错误
     */
    private fun classifyRecordingError(error: Throwable): AudioErrorType {
        return when {
            error.message?.contains("permission", ignoreCase = true) == true -> AudioErrorType.PERMISSION_DENIED
            error.message?.contains("microphone", ignoreCase = true) == true -> AudioErrorType.MICROPHONE_UNAVAILABLE
            error.message?.contains("storage", ignoreCase = true) == true -> AudioErrorType.STORAGE_INSUFFICIENT
            error.message?.contains("space", ignoreCase = true) == true -> AudioErrorType.STORAGE_INSUFFICIENT
            error.message?.contains("interrupted", ignoreCase = true) == true -> AudioErrorType.RECORDING_INTERRUPTED
            else -> AudioErrorType.RECORDING_FAILED
        }
    }

    /**
     * 获取错误严重程度
     */
    private fun getErrorSeverity(errorType: AudioErrorType): ErrorSeverity {
        return when (errorType) {
            AudioErrorType.NETWORK_ERROR -> ErrorSeverity.MEDIUM
            AudioErrorType.AUDIO_FILE_NOT_FOUND -> ErrorSeverity.MEDIUM
            AudioErrorType.PERMISSION_DENIED -> ErrorSeverity.HIGH
            AudioErrorType.MICROPHONE_UNAVAILABLE -> ErrorSeverity.HIGH
            AudioErrorType.STORAGE_INSUFFICIENT -> ErrorSeverity.HIGH
            AudioErrorType.HARDWARE_ERROR -> ErrorSeverity.CRITICAL
            AudioErrorType.PLAYBACK_INTERRUPTED,
            AudioErrorType.RECORDING_INTERRUPTED -> ErrorSeverity.LOW
            else -> ErrorSeverity.MEDIUM
        }
    }

    /**
     * 获取用户友好的错误信息
     */
    private fun getUserFriendlyMessage(context: Context, errorType: AudioErrorType): String {
        return when (errorType) {
            AudioErrorType.PLAYBACK_FAILED -> "音频播放失败"
            AudioErrorType.PLAYBACK_INTERRUPTED -> "音频播放被中断"
            AudioErrorType.AUDIO_FILE_NOT_FOUND -> "音频文件未找到"
            AudioErrorType.AUDIO_FORMAT_UNSUPPORTED -> "不支持的音频格式"
            AudioErrorType.NETWORK_ERROR -> "网络连接错误，请检查网络设置"
            AudioErrorType.RECORDING_FAILED -> "录音失败"
            AudioErrorType.RECORDING_INTERRUPTED -> "录音被中断"
            AudioErrorType.MICROPHONE_UNAVAILABLE -> "麦克风不可用，请检查设备设置"
            AudioErrorType.STORAGE_INSUFFICIENT -> "存储空间不足，请清理设备存储"
            AudioErrorType.PERMISSION_DENIED -> "缺少必要权限，请在设置中授予权限"
            AudioErrorType.AUDIO_FOCUS_LOST -> "音频焦点丢失"
            AudioErrorType.HARDWARE_ERROR -> "硬件错误，请重启应用"
            AudioErrorType.UNKNOWN_ERROR -> "未知错误，请稍后重试"
        }
    }

    /**
     * 获取建议的解决方案
     */
    private fun getSuggestedAction(context: Context, errorType: AudioErrorType): String? {
        return when (errorType) {
            AudioErrorType.NETWORK_ERROR -> "请检查网络连接后重试"
            AudioErrorType.PERMISSION_DENIED -> "请前往设置页面授予必要权限"
            AudioErrorType.STORAGE_INSUFFICIENT -> "请清理设备存储空间"
            AudioErrorType.MICROPHONE_UNAVAILABLE -> "请检查麦克风是否被其他应用占用"
            AudioErrorType.HARDWARE_ERROR -> "请重启应用或设备"
            AudioErrorType.AUDIO_FORMAT_UNSUPPORTED -> "请尝试其他音频文件"
            else -> null
        }
    }

    /**
     * 检查播放错误是否可以重试
     */
    private fun canRetryPlayback(errorType: AudioErrorType): Boolean {
        return when (errorType) {
            AudioErrorType.NETWORK_ERROR,
            AudioErrorType.PLAYBACK_INTERRUPTED,
            AudioErrorType.AUDIO_FOCUS_LOST -> true
            else -> false
        }
    }

    /**
     * 检查录音错误是否可以重试
     */
    private fun canRetryRecording(errorType: AudioErrorType): Boolean {
        return when (errorType) {
            AudioErrorType.RECORDING_INTERRUPTED,
            AudioErrorType.AUDIO_FOCUS_LOST -> true
            else -> false
        }
    }

    /**
     * 向用户显示错误信息
     */
    private fun showErrorToUser(context: Context, errorEvent: AudioErrorEvent) {
        val message = if (errorEvent.suggestedAction != null) {
            "${errorEvent.message}\n${errorEvent.suggestedAction}"
        } else {
            errorEvent.message
        }
        
        when (errorEvent.severity) {
            ErrorSeverity.LOW -> {
                // 低严重程度，短时间Toast
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            ErrorSeverity.MEDIUM -> {
                // 中等严重程度，长时间Toast
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
            ErrorSeverity.HIGH, ErrorSeverity.CRITICAL -> {
                // 高严重程度，长时间Toast（可以考虑使用对话框）
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 记录错误日志
     */
    private fun logError(errorEvent: AudioErrorEvent, error: Throwable) {
        val logLevel = when (errorEvent.severity) {
            ErrorSeverity.LOW -> Log.INFO
            ErrorSeverity.MEDIUM -> Log.WARN
            ErrorSeverity.HIGH, ErrorSeverity.CRITICAL -> Log.ERROR
        }
        
        val logMessage = buildString {
            append("Audio Error: ${errorEvent.type}")
            append(", Severity: ${errorEvent.severity}")
            append(", Message: ${errorEvent.message}")
            errorEvent.audioId?.let { append(", AudioId: $it") }
            errorEvent.sessionId?.let { append(", SessionId: $it") }
            append(", Technical: ${errorEvent.technicalDetails}")
        }
        
        Log.println(logLevel, TAG, logMessage)
        Log.println(logLevel, TAG, "Exception details:" + Log.getStackTraceString(error))
    }

    /**
     * 更新错误统计
     */
    private fun updateErrorStats(errorType: AudioErrorType) {
        synchronized(errorStats) {
            errorStats[errorType] = (errorStats[errorType] ?: 0) + 1
        }
    }

    /**
     * 获取错误统计信息
     */
    fun getErrorStats(): Map<AudioErrorType, Int> {
        synchronized(errorStats) {
            return errorStats.toMap()
        }
    }

    /**
     * 清除错误统计
     */
    fun clearErrorStats() {
        synchronized(errorStats) {
            errorStats.clear()
        }
    }

    /**
     * 处理通用音频错误
     */
    fun handleGenericError(
        context: Context,
        error: Throwable,
        operation: String,
        showToUser: Boolean = true
    ) {
        val errorEvent = AudioErrorEvent(
            type = AudioErrorType.UNKNOWN_ERROR,
            severity = ErrorSeverity.MEDIUM,
            message = "操作失败: $operation",
            technicalDetails = error.message ?: "Unknown error"
        )
        
        logError(errorEvent, error)
        updateErrorStats(AudioErrorType.UNKNOWN_ERROR)
        _errorEvents.tryEmit(errorEvent)
        
        if (showToUser) {
            showErrorToUser(context, errorEvent)
        }
    }
}