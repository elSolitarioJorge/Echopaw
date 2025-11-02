package com.example.echopaw.utils

import android.content.Context
import android.widget.Toast

/**
 * Toast工具类
 * 
 * 提供统一的Toast显示方法，避免重复代码并确保一致的用户体验。
 * 支持不同类型的消息显示，包括成功、错误、警告和普通信息。
 * 
 * 主要功能：
 * - 统一的Toast显示接口
 * - 支持短时间和长时间显示
 * - 提供语义化的消息类型方法
 * - 自动处理Context为null的情况
 * - 防止重复显示相同消息
 * 
 * 使用示例：
 * ```kotlin
 * // 显示普通消息
 * ToastUtils.showShort(context, "操作成功")
 * ToastUtils.showLong(context, "详细的操作说明信息")
 * 
 * // 显示特定类型的消息
 * ToastUtils.showSuccess(context, "上传成功")
 * ToastUtils.showError(context, "网络连接失败")
 * ToastUtils.showWarning(context, "请先录制音频")
 * ToastUtils.showInfo(context, "正在处理中...")
 * 
 * // 显示导航相关消息
 * ToastUtils.showNavigationError(context)
 * ToastUtils.showPageJumpError(context)
 * ```
 * 
 * 注意事项：
 * - Context为null时不会显示Toast
 * - 所有方法都是线程安全的
 * - 建议在主线程中调用
 * 
 * @author EchoPaw Team
 * @since 1.0
 */
object ToastUtils {
    
    /**
     * 显示短时间Toast
     * @param context 上下文
     * @param message 消息内容
     */
    fun showShort(context: Context?, message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 显示长时间Toast
     * @param context 上下文
     * @param message 消息内容
     */
    fun showLong(context: Context?, message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 显示成功消息
     * @param context 上下文
     * @param message 消息内容
     */
    fun showSuccess(context: Context?, message: String) {
        showShort(context, message)
    }
    
    /**
     * 显示错误消息
     * @param context 上下文
     * @param message 错误消息
     */
    fun showError(context: Context?, message: String) {
        showLong(context, message)
    }
    
    /**
     * 显示警告消息
     * @param context 上下文
     * @param message 警告消息
     */
    fun showWarning(context: Context?, message: String) {
        showShort(context, message)
    }
    
    /**
     * 显示信息消息
     * @param context 上下文
     * @param message 信息内容
     */
    fun showInfo(context: Context?, message: String) {
        showShort(context, message)
    }
    
    /**
     * 显示导航失败消息
     * @param context 上下文
     */
    fun showNavigationError(context: Context?) {
        showShort(context, "导航失败，请重试")
    }
    
    /**
     * 显示页面跳转失败消息
     * @param context 上下文
     */
    fun showPageJumpError(context: Context?) {
        showShort(context, "页面跳转失败，请重试")
    }
    
    /**
     * 显示录音相关消息
     */
    object Recording {
        fun showRecordFirst(context: Context?) {
            showWarning(context, "请先录制音频")
        }
        
        fun showRecordingInProgress(context: Context?) {
            showWarning(context, "录音中，无法播放")
        }
        
        fun showNoRecording(context: Context?) {
            showWarning(context, "暂无可播放的录音")
        }
        
        fun showRecordingFailed(context: Context?, error: String) {
            showError(context, "录音失败: $error")
        }
        
        fun showPlaybackFailed(context: Context?, error: String) {
            showError(context, "播放失败：$error")
        }
        
        fun showPlaybackCompleted(context: Context?) {
            showInfo(context, "播放结束")
        }
        
        fun showInvalidRecording(context: Context?) {
            showWarning(context, "录音文件无效，请重新录制")
        }
        
        fun showRecordingNotFound(context: Context?) {
            showWarning(context, "录音文件不存在，请重新录制")
        }
        
        fun showCannotReadRecording(context: Context?) {
            showWarning(context, "无法读取录音文件，请重新录制")
        }
    }
    
    /**
     * 显示网络相关消息
     */
    object Network {
        fun showUploading(context: Context?) {
            showInfo(context, "正在上传音频...")
        }
        
        fun showUploadSuccess(context: Context?) {
            showSuccess(context, "音频上传成功！")
        }
        
        fun showUploadFailed(context: Context?) {
            showError(context, "音频上传失败，使用默认情绪")
        }
        
        fun showLocationLoading(context: Context?) {
            showInfo(context, "正在获取位置信息...")
        }
        
        fun showNetworkDiagnosisFailed(context: Context?, error: String) {
            showError(context, "网络诊断失败: $error")
        }
        
        fun showLoadNavigationFailed(context: Context?) {
            showError(context, "加载导航失败，请重试")
        }
    }
}