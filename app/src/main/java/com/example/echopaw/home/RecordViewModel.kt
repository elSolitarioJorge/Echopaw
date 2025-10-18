package com.example.echopaw.home

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 录制ViewModel
 * 
 * 该ViewModel负责管理音频录制的业务逻辑：
 * 1. 控制录音的开始、停止和取消
 * 2. 管理录音状态和时长
 * 3. 实时采集和提供音频波形数据
 * 4. 处理录音文件的创建和管理
 * 5. 提供录音过程中的错误处理
 * 
 * 使用StateFlow提供响应式的状态管理，确保UI能够实时响应录音状态变化
 */
class RecordViewModel : ViewModel() {
    
    /**
     * 录音状态的私有可变状态流
     * 内部使用，用于更新录音状态
     */
    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    
    /**
     * 录音状态的公开只读状态流
     * 供UI层观察录音状态变化
     */
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    /**
     * 波形数据的私有可变状态流
     * 内部使用，用于更新音频波形数据
     */
    private val _waveformData = MutableStateFlow<List<Float>>(emptyList())
    
    /**
     * 波形数据的公开只读状态流
     * 供UI层观察和显示音频波形可视化
     */
    val waveformData: StateFlow<List<Float>> = _waveformData.asStateFlow()

    /**
     * 录音时长的私有可变状态流
     * 内部使用，用于更新录音时长显示
     */
    private val _recordingTime = MutableStateFlow("00:00.00")
    
    /**
     * 录音时长的公开只读状态流
     * 供UI层显示格式化的录音时长
     */
    val recordingTime: StateFlow<String> = _recordingTime.asStateFlow()

    /**
     * MediaRecorder实例
     * 用于执行实际的音频录制操作
     */
    private var mediaRecorder: MediaRecorder? = null
    
    /**
     * 录音计时协程任务
     * 负责更新录音时长显示
     */
    private var recordingJob: Job? = null
    
    /**
     * 波形数据采集协程任务
     * 负责实时采集音频振幅数据
     */
    private var waveformJob: Job? = null
    
    /**
     * 当前录音文件
     * 存储正在录制的音频文件引用
     */
    private var currentFile: File? = null
    
    /**
     * 录音开始时间戳
     * 用于计算录音时长
     */
    private var startTime: Long = 0

    /**
     * 录音状态密封类
     * 
     * 定义录音过程中的所有可能状态：
     * - Idle: 空闲状态，未开始录音
     * - Recording: 正在录音状态
     * - Error: 错误状态，包含错误信息
     * - Completed: 录音完成状态，包含文件和时长信息
     */
    sealed class RecordingState {
        /** 空闲状态 */
        object Idle : RecordingState()
        
        /** 正在录音状态 */
        object Recording : RecordingState()
        
        /** 错误状态 */
        data class Error(val message: String) : RecordingState()
        
        /** 录音完成状态 */
        data class Completed(val file: File, val duration: Long) : RecordingState()
    }
    /**
     * 创建MediaRecorder实例
     * 
     * 该方法根据Android版本创建合适的MediaRecorder实例：
     * 1. Android 12+：使用新的构造方法
     * 2. 低版本：使用已弃用但仍可用的无参构造方法
     * 
     * @return 创建的MediaRecorder实例
     */
    fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31（Android 12）及以上使用新构造方法
            MediaRecorder()
        } else {
            // 低版本使用旧的无参构造（虽然标记过时，但低版本可用）
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }
    // 开始录音
//    fun startRecording(context: Context) {
//        if (_recordingState.value is RecordingState.Recording) return
//
//        try {
//            // 创建录音文件
//            currentFile = createRecordingFile(context)
//
//            // 初始化MediaRecorder
//            mediaRecorder = createMediaRecorder().apply {
//                setAudioSource(MediaRecorder.AudioSource.MIC)
//                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
//                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
//                setOutputFile(createTempFile().absolutePath)
//                prepare()
//                start()
//            }
//
//            startTime = System.currentTimeMillis()
//            _recordingState.value = RecordingState.Recording
//
//            // 启动计时器
//            startRecordingTimer()
//
//            // 启动波形数据采集
//            startWaveformCollection()
//
//        } catch (e: Exception) {
//            _recordingState.value = RecordingState.Error("录音启动失败: ${e.message ?: "未知错误"}")
//            cleanUpRecording()
//        }
//    }

    /**
     * 停止录音
     * 
     * 该方法停止当前的录音操作：
     * 1. 检查当前是否处于录音状态
     * 2. 停止MediaRecorder并计算录音时长
     * 3. 更新状态为完成或错误
     * 4. 清理录音资源
     */
    fun stopRecording() {
        if (_recordingState.value !is RecordingState.Recording) return

        try {
            mediaRecorder?.stop()
            val duration = System.currentTimeMillis() - startTime
            _recordingState.value = RecordingState.Completed(currentFile!!, duration)
        } catch (e: Exception) {
            _recordingState.value = RecordingState.Error("录音停止失败: ${e.message ?: "未知错误"}")
        } finally {
            cleanUpRecording()
        }
    }

    /**
     * 创建录音文件
     * 
     * 该方法在应用的音乐目录中创建录音文件：
     * 1. 生成基于时间戳的文件名
     * 2. 使用MP4格式存储音频
     * 3. 存储在外部文件目录的音乐文件夹中
     * 
     * @param context 应用上下文，用于获取外部文件目录
     * @return 创建的录音文件
     */
    private fun createRecordingFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        return File.createTempFile(
            "REC_${timeStamp}_",
            ".mp4",
            storageDir
        )
    }

    /**
     * 启动录音计时器
     * 
     * 该方法启动一个协程来实时更新录音时长：
     * 1. 在主线程中运行，确保UI更新安全
     * 2. 每50ms更新一次时长显示
     * 3. 计算从开始录音到当前的时间差
     * 4. 格式化时间并更新StateFlow
     */
    private fun startRecordingTimer() {
        recordingJob =  viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                _recordingTime.value = formatTime(elapsed)
                delay(50) // 每50ms更新一次
            }
        }
    }

//    private fun startWaveformCollection() {
//        waveformJob = viewModelScope.launch(Dispatchers.IO) {
//            val maxPoints = 50 // 波形点数
//            val waveformList = MutableList(maxPoints) { 0f } // 初始化全 0
//
//            while (isActive && mediaRecorder != null) {
//                try {
//                    val rawAmplitude = mediaRecorder?.maxAmplitude?.toFloat() ?: 0f
//                    val amplitude = (rawAmplitude / 32767f * 50f).coerceIn(0f, 50f)
//
//                    // 滚动波形：移除最左边的点，加入新振幅
//                    waveformList.removeAt(0)
//                    waveformList.add(amplitude * (0.5f + Math.random().toFloat() * 0.5f)) // 微抖动
//
//                    _waveformData.value = waveformList.toList() // 发送给 UI
//                    Log.d("Waveform", "Amplitude: $amplitude")
//                } catch (e: Exception) {
//                    _waveformData.value = List(maxPoints) { 0f }
//                }
//                delay(50)
//            }
//        }
//    }
    /**
     * 开始录音
     * 
     * 该方法启动音频录制过程：
     * 1. 检查当前是否已在录音状态
     * 2. 创建录音文件和MediaRecorder实例
     * 3. 配置音频源、输出格式和编码器
     * 4. 启动录音、计时器和波形数据收集
     * 5. 处理录音启动过程中的异常
     * 
     * @param context 应用上下文，用于创建录音文件
     */
    fun startRecording(context: Context) {
        if (_recordingState.value is RecordingState.Recording) return

        try {
            // 创建录音文件
            val file = createRecordingFile(context)
            currentFile = file

            // 初始化 MediaRecorder
            mediaRecorder = createMediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath) // 重要：使用可写文件路径
                prepare()
                start()
            }

            Log.d("Waveform", "Recording started: ${file.absolutePath}")

            startTime = System.currentTimeMillis()
            _recordingState.value = RecordingState.Recording

            // 启动计时器
            startRecordingTimer()

            // 启动波形数据采集
            startWaveformCollection()

        } catch (e: Exception) {
            Log.e("Waveform", "Recording failed: ${e.message}")
            _recordingState.value = RecordingState.Error("录音启动失败: ${e.message ?: "未知错误"}")
            cleanUpRecording()
        }
    }
//    private fun startWaveformCollection() {
//        waveformJob = viewModelScope.launch(Dispatchers.IO) {
//            val maxPoints = 50
//            val waveformList = MutableList(maxPoints) { 0f }
//
//            delay(500) // 给 MediaRecorder 启动缓冲时间
//
//            while (isActive && mediaRecorder != null) {
//                try {
//                    var rawAmplitude = mediaRecorder?.maxAmplitude?.toFloat() ?: 0f
//                    if (rawAmplitude == 0f) rawAmplitude = 1f // 防止完全为 0
//
//                    val amplitude = (rawAmplitude / 32767f * 100f).coerceIn(0f, 100f)
//
//                    waveformList.removeAt(0)
//                    waveformList.add(amplitude)
//
//                    _waveformData.value = waveformList.toList()
//                } catch (e: Exception) {
//                    _waveformData.value = List(maxPoints) { 0f }
//                }
//                delay(50)
//            }
//        }
//    }

//
    /**
     * 开始波形数据收集
     * 
     * 该方法启动一个协程来实时收集音频波形数据：
     * 1. 在主线程中运行，确保UI更新安全
     * 2. 每50ms从MediaRecorder获取最大振幅
     * 3. 将振幅值标准化并添加到波形列表
     * 4. 更新波形数据StateFlow供UI使用
     * 5. 在录音状态结束时自动停止收集
     */
    private fun startWaveformCollection() {
        waveformJob = viewModelScope.launch(Dispatchers.Main) { // 主线程更新 UI
            val maxPoints = 50
            val waveformList = MutableList(maxPoints) { 0f }

            while (isActive && mediaRecorder != null) {
                try {
                    // 获取原始振幅
                    val rawAmplitude = mediaRecorder?.maxAmplitude?.toFloat() ?: 0f

                    val amplitude = if (rawAmplitude > 0f) {
                        (rawAmplitude / 32767f * 150f).coerceIn(0f, 150f) // 放大到150，波动更大
                    } else {
                        5f + Math.random().toFloat() * 30f // 没有声音时也给点随机波动
                    }
                    // 滚动波形
                    waveformList.removeAt(0)
                    waveformList.add(amplitude)
                    // 更新 StateFlow
                    _waveformData.value = waveformList.toList()
                    // 打印日志调试
                    Log.d("Waveform", "Amplitude raw: $rawAmplitude, scaled: $amplitude")
                } catch (e: Exception) {
                    Log.e("Waveform", "采集波形出错: ${e.message}")
                    _waveformData.value = List(maxPoints) { 0f }
                }
                delay(50) // 每50ms采样一次
            }
        }
    }



    /**
     * 格式化时间显示
     * 
     * 该方法将毫秒时间转换为可读的时间格式：
     * 1. 将毫秒转换为分钟、秒和厘秒
     * 2. 格式化为"MM:SS.CC"格式
     * 3. 确保分钟、秒数和厘秒都是两位数显示
     * 
     * @param millis 要格式化的毫秒数
     * @return 格式化后的时间字符串，格式为"MM:SS.CC"
     */
    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / 60000) % 60
        val centis = (millis / 10) % 100
        return String.format("%02d:%02d.%02d", minutes, seconds, centis)
    }

    /**
     * 清理录音资源
     * 
     * 该方法释放所有与录音相关的资源：
     * 1. 释放MediaRecorder实例
     * 2. 取消录音计时器协程
     * 3. 取消波形数据收集协程
     * 4. 重置所有相关变量为null
     * 5. 忽略释放过程中的异常，确保清理完成
     */
    private fun cleanUpRecording() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            // 忽略释放错误
        }
        mediaRecorder = null
        recordingJob?.cancel()
        waveformJob?.cancel()
        recordingJob = null
        waveformJob = null
    }

    /**
     * 取消录音
     * 
     * 该方法取消当前的录音操作：
     * 1. 检查当前是否处于录音状态
     * 2. 停止MediaRecorder
     * 3. 删除已创建的录音文件
     * 4. 将状态重置为空闲状态
     * 5. 清理所有录音资源
     */
    fun cancelRecording() {
        if (_recordingState.value !is RecordingState.Recording) return

        try {
            mediaRecorder?.stop()
            currentFile?.delete() // 删除录音文件
        } catch (e: Exception) {
            // 忽略停止错误
        } finally {
            _recordingState.value = RecordingState.Idle
            cleanUpRecording()
        }
    }

    /**
     * ViewModel清理回调
     * 
     * 当ViewModel被销毁时自动调用，确保：
     * 1. 释放所有录音相关资源
     * 2. 取消所有正在运行的协程
     * 3. 防止内存泄漏
     */
    override fun onCleared() {
        super.onCleared()
        cleanUpRecording()
    }
}
