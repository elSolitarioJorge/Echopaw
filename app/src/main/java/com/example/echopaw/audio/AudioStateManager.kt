package com.example.echopaw.audio

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 音频状态管理器
 * 
 * 负责统一管理应用中所有音频播放和录制的状态，避免多个音频组件之间的冲突。
 * 实现单例模式，确保全局只有一个音频状态管理实例。
 * 
 * 主要功能：
 * 1. 音频播放状态管理 - 跟踪当前播放的音频
 * 2. 音频录制状态管理 - 管理录音状态和冲突检测
 * 3. 音频焦点管理 - 确保同时只有一个音频操作
 * 4. 状态监听和通知 - 提供状态变化的响应式通知
 * 5. 冲突解决机制 - 自动处理音频操作冲突
 * 
 * 技术特性：
 * - 单例模式，全局唯一实例
 * - StateFlow响应式状态管理
 * - 线程安全的状态更新
 * - 自动冲突检测和解决
 * - 完整的状态生命周期管理
 * 
 * 使用示例：
 * ```kotlin
 * // 获取音频状态管理器实例
 * val audioStateManager = AudioStateManager.getInstance()
 * 
 * // 请求播放音频
 * if (audioStateManager.requestPlayback("audio_id")) {
 *     // 可以开始播放
 *     startPlayback()
 * } else {
 *     // 播放请求被拒绝，可能有其他音频正在播放
 * }
 * 
 * // 请求录音
 * if (audioStateManager.requestRecording("recording_session")) {
 *     // 可以开始录音
 *     startRecording()
 * } else {
 *     // 录音请求被拒绝
 * }
 * 
 * // 监听音频状态变化
 * audioStateManager.audioState.collect { state ->
 *     when (state.currentOperation) {
 *         AudioOperation.PLAYING -> {
 *             // 有音频正在播放
 *         }
 *         AudioOperation.RECORDING -> {
 *             // 有录音正在进行
 *         }
 *         AudioOperation.IDLE -> {
 *             // 音频系统空闲
 *         }
 *     }
 * }
 * ```
 * 
 * @author EchoPaw Team
 * @since 1.0
 */
class AudioStateManager private constructor() {

    companion object {
        private const val TAG = "AudioStateManager"
        
        @Volatile
        private var INSTANCE: AudioStateManager? = null
        
        /**
         * 获取音频状态管理器单例实例
         */
        fun getInstance(): AudioStateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioStateManager().also { INSTANCE = it }
            }
        }
    }

    /**
     * 音频操作类型枚举
     */
    enum class AudioOperation {
        IDLE,       // 空闲状态
        PLAYING,    // 播放中
        RECORDING   // 录音中
    }

    /**
     * 音频状态数据类
     */
    data class AudioState(
        val currentOperation: AudioOperation = AudioOperation.IDLE,
        val currentAudioId: String? = null,
        val currentSessionId: String? = null,
        val operationStartTime: Long = 0L,
        val canInterrupt: Boolean = true
    )

    // 私有可变状态流
    private val _audioState = MutableStateFlow(AudioState())
    
    // 公开只读状态流
    val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    // 音频操作监听器
    private val operationListeners = mutableSetOf<AudioOperationListener>()

    /**
     * 音频操作监听器接口
     */
    interface AudioOperationListener {
        fun onOperationStarted(operation: AudioOperation, id: String)
        fun onOperationStopped(operation: AudioOperation, id: String)
        fun onOperationInterrupted(operation: AudioOperation, id: String, reason: String)
    }

    /**
     * 请求播放音频
     * 
     * @param audioId 音频ID
     * @param canInterrupt 是否可以被其他操作中断
     * @return 是否允许播放
     */
    fun requestPlayback(audioId: String, canInterrupt: Boolean = true): Boolean {
        synchronized(this) {
            val currentState = _audioState.value
            
            // 如果当前正在播放相同音频，返回true
            if (currentState.currentOperation == AudioOperation.PLAYING && 
                currentState.currentAudioId == audioId) {
                return true
            }
            
            // 如果有其他操作正在进行
            if (currentState.currentOperation != AudioOperation.IDLE) {
                // 检查是否可以中断当前操作
                if (!currentState.canInterrupt) {
                    Log.w(TAG, "Cannot interrupt current operation: ${currentState.currentOperation}")
                    return false
                }
                
                // 中断当前操作
                interruptCurrentOperation("New playback request")
            }
            
            // 更新状态为播放中
            _audioState.value = AudioState(
                currentOperation = AudioOperation.PLAYING,
                currentAudioId = audioId,
                currentSessionId = null,
                operationStartTime = System.currentTimeMillis(),
                canInterrupt = canInterrupt
            )
            
            // 通知监听器
            notifyOperationStarted(AudioOperation.PLAYING, audioId)
            
            Log.d(TAG, "Playback request granted for audio: $audioId")
            return true
        }
    }

    /**
     * 请求录音
     * 
     * @param sessionId 录音会话ID
     * @param canInterrupt 是否可以被其他操作中断
     * @return 是否允许录音
     */
    fun requestRecording(sessionId: String, canInterrupt: Boolean = false): Boolean {
        synchronized(this) {
            val currentState = _audioState.value
            
            // 如果当前正在录音相同会话，返回true
            if (currentState.currentOperation == AudioOperation.RECORDING && 
                currentState.currentSessionId == sessionId) {
                return true
            }
            
            // 如果有其他操作正在进行
            if (currentState.currentOperation != AudioOperation.IDLE) {
                // 检查是否可以中断当前操作
                if (!currentState.canInterrupt) {
                    Log.w(TAG, "Cannot interrupt current operation: ${currentState.currentOperation}")
                    return false
                }
                
                // 中断当前操作
                interruptCurrentOperation("New recording request")
            }
            
            // 更新状态为录音中
            _audioState.value = AudioState(
                currentOperation = AudioOperation.RECORDING,
                currentAudioId = null,
                currentSessionId = sessionId,
                operationStartTime = System.currentTimeMillis(),
                canInterrupt = canInterrupt
            )
            
            // 通知监听器
            notifyOperationStarted(AudioOperation.RECORDING, sessionId)
            
            Log.d(TAG, "Recording request granted for session: $sessionId")
            return true
        }
    }

    /**
     * 停止当前音频操作
     * 
     * @param operationId 操作ID（音频ID或会话ID）
     */
    fun stopCurrentOperation(operationId: String) {
        synchronized(this) {
            val currentState = _audioState.value
            
            // 检查操作ID是否匹配
            val isCurrentOperation = when (currentState.currentOperation) {
                AudioOperation.PLAYING -> currentState.currentAudioId == operationId
                AudioOperation.RECORDING -> currentState.currentSessionId == operationId
                AudioOperation.IDLE -> false
            }
            
            if (!isCurrentOperation) {
                Log.w(TAG, "Operation ID mismatch: $operationId")
                return
            }
            
            // 通知监听器操作停止
            notifyOperationStopped(currentState.currentOperation, operationId)
            
            // 重置状态为空闲
            _audioState.value = AudioState()
            
            Log.d(TAG, "Operation stopped: ${currentState.currentOperation}, ID: $operationId")
        }
    }

    /**
     * 强制停止所有音频操作
     */
    fun forceStopAllOperations() {
        synchronized(this) {
            val currentState = _audioState.value
            
            if (currentState.currentOperation != AudioOperation.IDLE) {
                val operationId = currentState.currentAudioId ?: currentState.currentSessionId ?: "unknown"
                interruptCurrentOperation("Force stop all operations")
            }
        }
    }

    /**
     * 中断当前操作
     */
    private fun interruptCurrentOperation(reason: String) {
        val currentState = _audioState.value
        
        if (currentState.currentOperation != AudioOperation.IDLE) {
            val operationId = currentState.currentAudioId ?: currentState.currentSessionId ?: "unknown"
            
            // 通知监听器操作被中断
            notifyOperationInterrupted(currentState.currentOperation, operationId, reason)
            
            Log.d(TAG, "Operation interrupted: ${currentState.currentOperation}, ID: $operationId, Reason: $reason")
        }
        
        // 重置状态
        _audioState.value = AudioState()
    }

    /**
     * 检查是否可以执行音频操作
     * 
     * @param requestedOperation 请求的操作类型
     * @return 是否可以执行
     */
    fun canPerformOperation(requestedOperation: AudioOperation): Boolean {
        val currentState = _audioState.value
        
        return when {
            currentState.currentOperation == AudioOperation.IDLE -> true
            currentState.currentOperation == requestedOperation -> true
            currentState.canInterrupt -> true
            else -> false
        }
    }

    /**
     * 获取当前操作信息
     */
    fun getCurrentOperationInfo(): Pair<AudioOperation, String?> {
        val currentState = _audioState.value
        val operationId = currentState.currentAudioId ?: currentState.currentSessionId
        return Pair(currentState.currentOperation, operationId)
    }

    /**
     * 添加音频操作监听器
     */
    fun addOperationListener(listener: AudioOperationListener) {
        synchronized(operationListeners) {
            operationListeners.add(listener)
        }
    }

    /**
     * 移除音频操作监听器
     */
    fun removeOperationListener(listener: AudioOperationListener) {
        synchronized(operationListeners) {
            operationListeners.remove(listener)
        }
    }

    /**
     * 通知操作开始
     */
    private fun notifyOperationStarted(operation: AudioOperation, id: String) {
        synchronized(operationListeners) {
            operationListeners.forEach { listener ->
                try {
                    listener.onOperationStarted(operation, id)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying operation started", e)
                }
            }
        }
    }

    /**
     * 通知操作停止
     */
    private fun notifyOperationStopped(operation: AudioOperation, id: String) {
        synchronized(operationListeners) {
            operationListeners.forEach { listener ->
                try {
                    listener.onOperationStopped(operation, id)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying operation stopped", e)
                }
            }
        }
    }

    /**
     * 通知操作被中断
     */
    private fun notifyOperationInterrupted(operation: AudioOperation, id: String, reason: String) {
        synchronized(operationListeners) {
            operationListeners.forEach { listener ->
                try {
                    listener.onOperationInterrupted(operation, id, reason)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying operation interrupted", e)
                }
            }
        }
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        synchronized(this) {
            forceStopAllOperations()
            operationListeners.clear()
            Log.d(TAG, "AudioStateManager cleaned up")
        }
    }
}