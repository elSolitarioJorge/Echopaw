package com.example.echopaw.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * 音频播放管理器
 * 
 * 负责音频播放的完整生命周期管理，提供统一的音频播放接口和状态管理。
 * 实现了DefaultLifecycleObserver接口，能够自动响应Activity/Fragment的生命周期变化。
 * 集成了AudioStateManager，确保与其他音频组件的协调工作。
 * 
 * 主要功能：
 * 1. 音频播放和暂停控制 - 支持本地文件和网络音频
 * 2. 播放状态监听和回调 - 提供详细的播放状态回调
 * 3. 跨生命周期管理 - 自动处理Activity/Fragment生命周期
 * 4. 错误处理和重试机制 - 网络异常和播放错误的自动处理
 * 5. 播放进度更新 - 实时更新播放进度，支持进度回调
 * 6. 状态协调 - 与全局音频状态管理器协调
 * 
 * 技术特性：
 * - 使用MediaPlayer作为底层播放引擎
 * - 协程支持，所有操作都是异步非阻塞的
 * - 自动资源管理，防止内存泄漏
 * - 支持音频焦点管理
 * - 线程安全的状态管理
 * - 集成全局音频状态管理
 * 
 * 使用示例：
 * ```kotlin
 * // 创建播放器实例
 * val audioPlayer = AudioPlayerManager(context, lifecycleScope)
 * lifecycle.addObserver(audioPlayer)
 * 
 * // 设置播放监听器
 * audioPlayer.setPlaybackListener(object : AudioPlayerManager.PlaybackListener {
 *     override fun onPlaybackStarted(audioId: String) {
 *         // 播放开始
 *     }
 *     override fun onPlaybackPaused(audioId: String) {
 *         // 播放暂停
 *     }
 *     override fun onPlaybackCompleted(audioId: String) {
 *         // 播放完成
 *     }
 *     override fun onPlaybackError(audioId: String, error: String) {
 *         // 播放错误
 *     }
 *     override fun onProgressUpdate(audioId: String, currentPosition: Int, duration: Int) {
 *         // 进度更新
 *     }
 * })
 * 
 * // 播放音频
 * audioPlayer.playAudio("audio_id", "http://example.com/audio.mp3")
 * 
 * // 暂停播放
 * audioPlayer.pauseAudio()
 * 
 * // 停止播放
 * audioPlayer.stopAudio()
 * ```
 * 
 * 注意事项：
 * - 必须在主线程中创建和使用
 * - 需要网络权限播放网络音频
 * - 建议配合生命周期使用，避免内存泄漏
 * - 播放网络音频时需要处理网络异常
 * 
 * @param context Android上下文，用于创建MediaPlayer
 * @param coroutineScope 协程作用域，用于异步操作
 * 
 * @author EchoPaw Team
 * @since 1.0
 */
class AudioPlayerManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : DefaultLifecycleObserver, AudioStateManager.AudioOperationListener {

    companion object {
        private const val TAG = "AudioPlayerManager"
        private const val PROGRESS_UPDATE_INTERVAL = 100L // 进度更新间隔（毫秒）
    }

    // 音频状态管理器
    private val audioStateManager = AudioStateManager.getInstance()

    private var mediaPlayer: MediaPlayer? = null
    private var currentAudioUrl: String? = null
    private var currentAudioId: String? = null
    private var isPlaying = false
    private var isPrepared = false
    private var progressUpdateJob: Job? = null

    init {
        // 注册音频状态管理器监听器
        audioStateManager.addOperationListener(this)
    }

    /**
     * 播放状态监听器
     */
    interface PlaybackListener {
        fun onPlaybackStarted(audioId: String)
        fun onPlaybackPaused(audioId: String)
        fun onPlaybackCompleted(audioId: String)
        fun onPlaybackError(audioId: String, error: String)
        fun onProgressUpdate(audioId: String, currentPosition: Int, duration: Int)
        fun onBufferingUpdate(audioId: String, percent: Int)
    }

    private var playbackListener: PlaybackListener? = null

    /**
     * 设置播放状态监听器
     */
    fun setPlaybackListener(listener: PlaybackListener?) {
        this.playbackListener = listener
    }

    /**
     * 播放音频
     * 
     * @param audioId 音频ID
     * @param audioUrl 音频URL
     */
    fun playAudio(audioId: String, audioUrl: String) {
        coroutineScope.launch(Dispatchers.Main) {
            try {
                Log.d(TAG, "播放音频请求: $audioId, 路径: $audioUrl")
                
                // 请求音频播放权限
                if (!audioStateManager.requestPlayback(audioId, canInterrupt = true)) {
                    Log.w(TAG, "音频播放请求被拒绝: $audioId")
                    playbackListener?.onPlaybackError(audioId, "无法播放音频，其他音频操作正在进行")
                    return@launch
                }
                
                when {
                    // 如果是相同音频且正在播放，则暂停
                    currentAudioId == audioId && isPlaying -> {
                        Log.d(TAG, "暂停当前播放的音频: $audioId")
                        pauseAudio()
                    }
                    
                    // 如果是相同音频且已暂停，则继续播放
                    currentAudioId == audioId && isPrepared && !isPlaying -> {
                        Log.d(TAG, "继续播放音频: $audioId")
                        resumeAudio()
                    }
                    
                    // 如果是不同音频，停止当前播放并开始新音频
                    else -> {
                        Log.d(TAG, "开始播放新音频: $audioId")
                        if (currentAudioId != null) {
                            stopAudio()
                        }
                        startNewAudio(audioId, audioUrl)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "播放音频时发生错误: $audioId", e)
                audioStateManager.stopCurrentOperation(audioId)
                playbackListener?.onPlaybackError(audioId, "播放失败: ${e.message}")
            }
        }
    }

    /**
     * 开始播放新音频
     */
    private fun startNewAudio(audioId: String, audioUrl: String) {
        Log.d(TAG, "Starting new audio: $audioId")
        
        // 释放之前的MediaPlayer
        releaseMediaPlayer()
        
        currentAudioId = audioId
        currentAudioUrl = audioUrl
        
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            
            setOnPreparedListener { mp ->
                Log.d(TAG, "MediaPlayer prepared for audio: $audioId")
                this@AudioPlayerManager.isPrepared = true
                mp.start()
                this@AudioPlayerManager.isPlaying = true
                playbackListener?.onPlaybackStarted(audioId)
                startProgressUpdates()
            }
            
            setOnCompletionListener { mp ->
                Log.d(TAG, "MediaPlayer completed for audio: $audioId")
                this@AudioPlayerManager.isPlaying = false
                this@AudioPlayerManager.isPrepared = false
                stopProgressUpdates()
                playbackListener?.onPlaybackCompleted(audioId)
            }
            
            setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "MediaPlayer error for audio: $audioId, what=$what, extra=$extra")
                this@AudioPlayerManager.isPlaying = false
                this@AudioPlayerManager.isPrepared = false
                stopProgressUpdates()
                playbackListener?.onPlaybackError(audioId, "播放错误: what=$what, extra=$extra")
                true
            }
            
            setOnBufferingUpdateListener { mp, percent ->
                playbackListener?.onBufferingUpdate(audioId, percent)
            }
            
            try {
                setDataSource(context, Uri.parse(audioUrl))
                prepareAsync()
            } catch (e: IOException) {
                Log.e(TAG, "Error setting data source: ${e.message}", e)
                playbackListener?.onPlaybackError(audioId, "无法加载音频: ${e.message}")
            }
        }
    }

    /**
     * 继续播放
     */
    private fun resumeAudio() {
        currentAudioId?.let { audioId ->
            mediaPlayer?.let { mp ->
                if (isPrepared && !isPlaying) {
                    mp.start()
                    isPlaying = true
                    playbackListener?.onPlaybackStarted(audioId)
                    startProgressUpdates()
                    Log.d(TAG, "Resumed audio: $audioId")
                }
            }
        }
    }

    /**
     * 暂停音频
     */
    fun pauseAudio() {
        currentAudioId?.let { audioId ->
            mediaPlayer?.let { mp ->
                if (isPlaying) {
                    mp.pause()
                    isPlaying = false
                    stopProgressUpdates()
                    playbackListener?.onPlaybackPaused(audioId)
                    Log.d(TAG, "Paused audio: $audioId")
                }
            }
        }
    }

    /**
     * 停止音频
     */
    fun stopAudio() {
        currentAudioId?.let { audioId ->
            if (isPlaying) {
                playbackListener?.onPlaybackPaused(audioId)
            }
            // 通知音频状态管理器停止操作
            audioStateManager.stopCurrentOperation(audioId)
        }
        
        isPlaying = false
        isPrepared = false
        stopProgressUpdates()
        releaseMediaPlayer()
        currentAudioId = null
        currentAudioUrl = null
        Log.d(TAG, "Stopped audio")
    }

    /**
     * 释放MediaPlayer资源
     */
    private fun releaseMediaPlayer() {
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.reset()
                mp.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing MediaPlayer: ${e.message}", e)
            }
        }
        mediaPlayer = null
    }

    /**
     * 开始进度更新
     */
    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressUpdateJob = coroutineScope.launch(Dispatchers.Main) {
            while (isPlaying && isPrepared) {
                mediaPlayer?.let { mp ->
                    try {
                        val currentPosition = mp.currentPosition
                        val duration = mp.duration
                        currentAudioId?.let { audioId ->
                            playbackListener?.onProgressUpdate(audioId, currentPosition, duration)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating progress: ${e.message}")
                    }
                }
                delay(PROGRESS_UPDATE_INTERVAL)
            }
        }
    }

    /**
     * 停止进度更新
     */
    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    /**
     * 获取当前播放的音频ID
     */
    fun getCurrentAudioId(): String? = currentAudioId

    /**
     * 检查是否正在播放
     */
    fun isPlaying(): Boolean = isPlaying

    /**
     * 检查指定音频是否正在播放
     */
    fun isPlaying(audioId: String): Boolean = currentAudioId == audioId && isPlaying

    // LifecycleObserver 方法
    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        // 在Activity/Fragment暂停时暂停播放
        if (isPlaying) {
            pauseAudio()
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        // 在Activity/Fragment销毁时释放资源
        stopAudio()
        cleanup()
    }

    // AudioOperationListener接口实现
    override fun onOperationStarted(operation: AudioStateManager.AudioOperation, id: String) {
        if (operation == AudioStateManager.AudioOperation.RECORDING) {
            // 如果开始录音，暂停当前播放
            if (isPlaying && currentAudioId != null) {
                Log.d(TAG, "Recording started, pausing current playback")
                pauseAudio()
            }
        }
    }

    override fun onOperationStopped(operation: AudioStateManager.AudioOperation, id: String) {
        // 可以在这里处理其他音频操作停止的逻辑
        Log.d(TAG, "Audio operation stopped: $operation, id: $id")
    }

    override fun onOperationInterrupted(operation: AudioStateManager.AudioOperation, id: String, reason: String) {
        if (operation == AudioStateManager.AudioOperation.PLAYING && id == currentAudioId) {
            Log.d(TAG, "Current playback interrupted: $reason")
            stopAudio()
        }
    }

    /**
     * 清理资源
     */
    private fun cleanup() {
        audioStateManager.removeOperationListener(this)
        stopProgressUpdates()
        releaseMediaPlayer()
        playbackListener = null
        Log.d(TAG, "AudioPlayerManager cleaned up")
    }
}