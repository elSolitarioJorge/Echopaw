package com.example.echopaw.phonograph

import android.app.Application
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.echopaw.audio.AudioStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 留声机功能的ViewModel
 * 
 * 负责管理留声机功能的业务逻辑和状态，包括：
 * 1. 录音状态管理（空闲、录音中、暂停、完成）
 * 2. 录音文件的创建、保存和管理
 * 3. 录音时长计算和显示
 * 4. 录音质量和格式配置
 * 5. 错误处理和用户反馈
 * 
 * 使用StateFlow管理UI状态，确保状态变化能够被UI层及时感知。
 * 所有录音操作都在IO线程中执行，避免阻塞主线程。
 * 
 * 技术特性：
 * - 使用MediaRecorder进行音频录制
 * - 协程支持，确保线程安全
 * - 自动文件管理和清理
 * - 实时录音时长更新
 * - 完整的错误处理机制
 * 
 * @param application Android应用程序上下文
 */
class PhonographViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PhonographViewModel"
        private const val AUDIO_FORMAT = ".m4a"
        private const val DURATION_UPDATE_INTERVAL = 100L // 时长更新间隔（毫秒）
    }

    // 音频状态管理器
    private val audioStateManager = AudioStateManager.getInstance()

    // MediaRecorder实例
    private var mediaRecorder: MediaRecorder? = null
    
    // 当前录音文件
    private var currentRecordingFile: File? = null
    
    // 录音开始时间
    private var recordingStartTime: Long = 0L
    
    // 时长更新任务
    private var durationUpdateJob: Job? = null

    /**
     * 录音状态枚举
     */
    enum class RecordingState {
        IDLE,       // 空闲状态
        RECORDING,  // 录音中
        PAUSED,     // 暂停
        COMPLETED   // 录音完成
    }

    /**
     * UI状态数据类
     */
    data class PhonographUiState(
        val recordingState: RecordingState = RecordingState.IDLE,
        val recordingDuration: Long = 0L,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val recordingFile: File? = null
    )

    // 私有可变状态流
    private val _uiState = MutableStateFlow(PhonographUiState())
    
    // 公开只读状态流
    val uiState: StateFlow<PhonographUiState> = _uiState.asStateFlow()

    /**
     * 开始录音 - 使用协程确保线程安全
     */
    fun startRecording() {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                // 生成录音会话ID
                val sessionId = "recording_${System.currentTimeMillis()}"
                
                // 请求录音权限
                if (!audioStateManager.requestRecording(sessionId, canInterrupt = false)) {
                    Log.w(TAG, "录音请求被拒绝")
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "无法开始录音，其他音频操作正在进行"
                    )
                    return@launch
                }
                
                _uiState.value = _uiState.value.copy(
                    recordingState = RecordingState.RECORDING,
                    errorMessage = null,
                    isLoading = true
                )
                
                // 在IO线程执行录音操作
                withContext(Dispatchers.IO) {
                    initializeMediaRecorder()
                    startRecordingInternal()
                }
                
                // 更新UI状态
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    recordingFile = currentRecordingFile
                )
                
                // 开始时长更新
                startDurationUpdates()
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording", e)
                _uiState.value = _uiState.value.copy(
                    recordingState = RecordingState.IDLE,
                    isLoading = false,
                    errorMessage = "录音启动失败: ${e.message}"
                )
                cleanupMediaRecorder()
            }
        }
    }

    /**
     * 停止录音 - 使用协程确保线程安全
     */
    fun stopRecording() {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                _uiState.value = _uiState.value.copy(
                    recordingState = RecordingState.COMPLETED,
                    errorMessage = null,
                    isLoading = true
                )
                
                // 停止时长更新
                stopDurationUpdates()
                
                // 在IO线程执行停止录音操作
                withContext(Dispatchers.IO) {
                    stopRecordingInternal()
                }
                
                // 通知音频状态管理器录音结束
                val sessionId = "recording_${recordingStartTime}"
                audioStateManager.stopCurrentOperation(sessionId)
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false
                )
                
                Log.d(TAG, "Recording completed successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop recording", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "录音停止失败: ${e.message}",
                    isLoading = false
                )
                cleanupMediaRecorder()
            }
        }
    }

    /**
     * 初始化MediaRecorder
     */
    private fun initializeMediaRecorder() {
        try {
            // 创建录音文件
            currentRecordingFile = createRecordingFile()
            
            // 创建MediaRecorder实例
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(getApplication())
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                // 设置音频源
                setAudioSource(MediaRecorder.AudioSource.MIC)
                
                // 设置输出格式
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                
                // 设置音频编码器
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                
                // 设置音频采样率
                setAudioSamplingRate(44100)
                
                // 设置音频编码比特率
                setAudioEncodingBitRate(128000)
                
                // 设置输出文件
                setOutputFile(currentRecordingFile?.absolutePath)
                
                // 准备录音
                prepare()
            }
            
            Log.d(TAG, "MediaRecorder initialized successfully")
            
        } catch (e: IOException) {
            Log.e(TAG, "Failed to initialize MediaRecorder", e)
            throw e
        }
    }

    /**
     * 开始录音内部实现
     */
    private fun startRecordingInternal() {
        try {
            mediaRecorder?.start()
            recordingStartTime = System.currentTimeMillis()
            Log.d(TAG, "Recording started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaRecorder", e)
            throw e
        }
    }

    /**
     * 停止录音内部实现
     */
    private fun stopRecordingInternal() {
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
            mediaRecorder = null
            Log.d(TAG, "Recording stopped and MediaRecorder released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop MediaRecorder", e)
            throw e
        }
    }

    /**
     * 创建录音文件
     */
    private fun createRecordingFile(): File {
        val recordingsDir = File(getApplication<Application>().filesDir, "recordings")
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
        }
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "recording_$timestamp$AUDIO_FORMAT"
        
        return File(recordingsDir, fileName)
    }

    /**
     * 开始时长更新
     */
    private fun startDurationUpdates() {
        stopDurationUpdates()
        durationUpdateJob = viewModelScope.launch(Dispatchers.Main) {
            while (_uiState.value.recordingState == RecordingState.RECORDING) {
                val currentTime = System.currentTimeMillis()
                val duration = currentTime - recordingStartTime
                updateRecordingDuration(duration)
                delay(DURATION_UPDATE_INTERVAL)
            }
        }
    }

    /**
     * 停止时长更新
     */
    private fun stopDurationUpdates() {
        durationUpdateJob?.cancel()
        durationUpdateJob = null
    }

    /**
     * 清理MediaRecorder资源
     */
    private fun cleanupMediaRecorder() {
        try {
            mediaRecorder?.apply {
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up MediaRecorder", e)
        } finally {
            mediaRecorder = null
        }
    }

    /**
     * 重置录音状态
     */
    fun resetRecording() {
        viewModelScope.launch(Dispatchers.Main) {
            stopDurationUpdates()
            cleanupMediaRecorder()
            
            // 删除当前录音文件（如果存在）
            currentRecordingFile?.let { file ->
                if (file.exists()) {
                    file.delete()
                    Log.d(TAG, "Deleted recording file: ${file.name}")
                }
            }
            currentRecordingFile = null
            
            _uiState.value = PhonographUiState()
            Log.d(TAG, "Recording state reset")
        }
    }

    /**
     * 发送录音
     */
    fun sendRecording() {
        viewModelScope.launch(Dispatchers.Main) {
            val recordingFile = currentRecordingFile
            if (recordingFile == null || !recordingFile.exists()) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "没有可发送的录音文件"
                )
                return@launch
            }
            
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    errorMessage = null
                )
                
                // 在IO线程执行发送操作
                withContext(Dispatchers.IO) {
                    // TODO: 实现发送录音的逻辑
                    // 可以调用上传API或其他发送机制
                    Log.d(TAG, "Sending recording file: ${recordingFile.absolutePath}")
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false
                )
                
                Log.d(TAG, "Recording sent successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send recording", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "发送录音失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 更新录音时长
     */
    private fun updateRecordingDuration(duration: Long) {
        _uiState.value = _uiState.value.copy(recordingDuration = duration)
    }

    /**
     * 清除错误消息
     */
    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * ViewModel销毁时清理资源
     */
    override fun onCleared() {
        super.onCleared()
        stopDurationUpdates()
        cleanupMediaRecorder()
        Log.d(TAG, "PhonographViewModel cleared")
    }
}