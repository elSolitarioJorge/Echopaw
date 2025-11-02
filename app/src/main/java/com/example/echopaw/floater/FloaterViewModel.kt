package com.example.echopaw.floater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

/**
 * 留声漂流瓶ViewModel
 * 
 * 该ViewModel负责管理留声漂流瓶功能的业务逻辑和状态，采用与RecordViewModel相同的MVVM架构：
 * 1. 使用StateFlow进行响应式状态管理
 * 2. 管理录音状态和UI状态
 * 3. 处理录音相关的业务逻辑
 * 
 * 状态管理：
 * - 录音状态（准备、录音中、完成）
 * - UI状态（按钮状态、文本显示等）
 * - 错误状态处理
 * 
 * @author EchoPaw Team
 * @since 1.0
 */
class FloaterViewModel : ViewModel() {
    
    /**
     * 录音状态枚举
     */
    enum class RecordingState {
        READY,      // 准备录音
        RECORDING,  // 录音中
        FINISHED,   // 录音完成
        IDLE        // 空闲状态
    }
    
    /**
     * UI状态数据类
     */
    data class FloaterUiState(
        val recordingState: RecordingState = RecordingState.READY,
        val isRecording: Boolean = false,
        val recordingDuration: Long = 0L,
        val errorMessage: String? = null,
        val isLoading: Boolean = false
    )
    
    // 私有可变状态
    private val _uiState = MutableStateFlow(FloaterUiState())
    
    // 公开只读状态
    val uiState: StateFlow<FloaterUiState> = _uiState.asStateFlow()
    
    /**
     * 开始录音
     */
    fun startRecording() {
        _uiState.value = _uiState.value.copy(
            recordingState = RecordingState.RECORDING,
            isRecording = true,
            errorMessage = null
        )
        
        // TODO: 实现实际的录音逻辑
        // 这里可以集成录音服务或使用现有的录音组件
    }
    
    /**
     * 停止录音
     */
    fun stopRecording() {
        _uiState.value = _uiState.value.copy(
            recordingState = RecordingState.FINISHED,
            isRecording = false
        )
        
        // TODO: 停止录音并保存文件
    }
    
    /**
     * 重新录音
     */
    fun resetRecording() {
        _uiState.value = _uiState.value.copy(
            recordingState = RecordingState.READY,
            isRecording = false,
            recordingDuration = 0L,
            errorMessage = null
        )
        
        // TODO: 清理录音文件
    }
    
    /**
     * 发送录音 - 使用协程确保线程安全
     */
    fun sendRecording() {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                // 在IO线程执行网络操作
                withContext(Dispatchers.IO) {
                    // TODO: 实现发送录音的逻辑
                    delay(2000) // 模拟网络请求
                }
                
                // 回到主线程更新UI
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    recordingState = RecordingState.IDLE
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "发送失败: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 更新录音时长
     */
    fun updateRecordingDuration(duration: Long) {
        _uiState.value = _uiState.value.copy(
            recordingDuration = duration
        )
    }
    
    /**
     * 设置错误消息
     */
    fun setError(message: String) {
        _uiState.value = _uiState.value.copy(
            errorMessage = message,
            isLoading = false
        )
    }
    
    /**
     * 清除错误消息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null
        )
    }
}