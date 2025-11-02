package com.example.echopaw.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.echopaw.network.ApiResult
import com.example.echopaw.network.AudioUploadResult
import com.example.echopaw.network.EmotionType
import com.example.echopaw.network.RetrofitClient
import com.example.echopaw.service.LocationService
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.rajawali3d.curves.LogarithmicSpiral3D
import java.io.File

/**
 * 发布流程共享状态ViewModel
 * - 管理同意复选框(cb_agree)的选中状态
 * - 管理音频文件上传功能
 * - 管理GPS定位和位置信息
 * - 提供观察和更新方法，确保在页面切换中状态正确传递
 */
class ReleaseFlowViewModel : ViewModel() {
    private val _agreeChecked = MutableLiveData(false)
    val agreeChecked: LiveData<Boolean> = _agreeChecked

    private val _uploadState = MutableLiveData<UploadState>(UploadState.Idle)
    val uploadState: LiveData<UploadState> = _uploadState

    private val _audioFile = MutableLiveData<File?>()
    val audioFile: LiveData<File?> = _audioFile

    private val _currentLocation = MutableLiveData<LocationService.LocationResult?>()
    val currentLocation: LiveData<LocationService.LocationResult?> = _currentLocation

    private val _emotionType = MutableLiveData<EmotionType>()
    val emotionType: LiveData<EmotionType> = _emotionType

    /**
     * 上传状态密封类
     */
    sealed class UploadState {
        object Idle : UploadState()
        object Uploading : UploadState()
        data class Success(val result: AudioUploadResult) : UploadState()
        data class Error(val message: String) : UploadState()
    }

    fun setAgreeChecked(checked: Boolean) {
        _agreeChecked.value = checked
    }

    /**
     * 设置要上传的音频文件
     */
    fun setAudioFile(file: File?) {
        _audioFile.value = file
    }

    /**
     * 设置当前位置信息
     */
    fun setCurrentLocation(location: LocationService.LocationResult?) {
        _currentLocation.value = location
    }

    /**
     * 设置情绪类型
     */
    fun setEmotionType(emotion: EmotionType) {
        _emotionType.value = emotion
    }

    /**
     * 上传音频文件
     */
    fun uploadAudio() {
        val file = _audioFile.value
        val location = _currentLocation.value

        if (file == null) {
            _uploadState.value = UploadState.Error("没有选择音频文件")
            return
        }

        if (location == null || location.errorCode != 0) {
            _uploadState.value = UploadState.Error("无法获取位置信息")
            return
        }

        if (!file.exists()) {
            _uploadState.value = UploadState.Error("音频文件不存在")
            return
        }

        // 验证文件是否符合API要求
        val validationError = validateAudioFile(file)
        if (validationError != null) {
            _uploadState.value = UploadState.Error(validationError)
            return
        }

        _uploadState.value = UploadState.Uploading

        viewModelScope.launch {
            try {
                // 根据文件扩展名动态设置Content-Type
                val mimeType = getAudioMimeType(file)
                Log.d("ReleaseFlowViewModel", "上传文件: ${file.name}, 大小: ${file.length()} bytes, MIME类型: $mimeType")
                
                // 创建文件请求体
                val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

                // 创建位置参数 - 确保数字格式正确
                val latitudeValue = String.format("%.6f", location.latitude)
                val longitudeValue = String.format("%.6f", location.longitude)
                val latitudePart = latitudeValue.toRequestBody("text/plain".toMediaTypeOrNull())
                val longitudePart = longitudeValue.toRequestBody("text/plain".toMediaTypeOrNull())
                
                // 记录详细的请求参数
                Log.d("ReleaseFlowViewModel", "请求参数详情:")
                Log.d("ReleaseFlowViewModel", "- 原始纬度: ${location.latitude}")
                Log.d("ReleaseFlowViewModel", "- 原始经度: ${location.longitude}")
                Log.d("ReleaseFlowViewModel", "- 格式化纬度: $latitudeValue")
                Log.d("ReleaseFlowViewModel", "- 格式化经度: $longitudeValue")
                Log.d("ReleaseFlowViewModel", "- 文件可读: ${file.canRead()}")
                Log.d("ReleaseFlowViewModel", "- 文件路径: ${file.absolutePath}")

                // 调用API上传
                val response = RetrofitClient.apiService.uploadAudio(
                    file = filePart,
                    latitude = latitudePart,
                    longitude = longitudePart
                )

                // 记录响应详情
                Log.d("ReleaseFlowViewModel", "响应详情:")
                Log.d("ReleaseFlowViewModel", "- HTTP状态码: ${response.code()}")
                Log.d("ReleaseFlowViewModel", "- 响应消息: ${response.message()}")
                Log.d("ReleaseFlowViewModel", "- 响应头: ${response.headers()}")
                
                // 如果响应失败，记录错误响应体
                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string()
                    Log.e("ReleaseFlowViewModel", "错误响应体: $errorBody")
                }

                if (response.isSuccessful && response.body() != null) {
                    val apiResponse = response.body()!!
                    Log.d("ReleaseFlowViewModel", "响应成功: $apiResponse")
                    if (apiResponse.code == 200 && apiResponse.data != null) {
                        Log.d("ReleaseFlowViewModel", "上传成功: ${apiResponse.data}")
                        _uploadState.value = UploadState.Success(apiResponse.data)
                    } else {
                        Log.e("ReleaseFlowViewModel", "上传失败: ${apiResponse.message}")
                        _uploadState.value = UploadState.Error(apiResponse.message)
                    }
                } else {
                    // 根据HTTP状态码提供更具体的错误信息
                    val errorMessage = when (response.code()) {
                        502 -> "服务器维护中，请稍后重试"
                        503 -> "服务暂时不可用，请稍后重试"
                        504 -> "服务器响应超时，请检查网络后重试"
                        401 -> "认证失败，请重新登录"
                        403 -> "权限不足，请联系管理员"
                        413 -> "文件过大，请选择较小的音频文件"
                        415 -> "不支持的文件格式"
                        429 -> "请求过于频繁，请稍后重试"
                        in 500..599 -> "服务器内部错误，请稍后重试"
                        in 400..499 -> "请求错误: ${response.message()}"
                        else -> "网络连接异常: ${response.code()} ${response.message()}"
                    }
                    _uploadState.value = UploadState.Error(errorMessage)
                }

            } catch (e: Exception) {
                // 根据异常类型提供更具体的错误信息
                val errorMessage = when (e) {
                    is java.net.UnknownHostException -> "无法连接到服务器，请检查网络连接"
                    is java.net.SocketTimeoutException -> "网络连接超时，请检查网络后重试"
                    is java.net.ConnectException -> "无法连接到服务器，请检查网络设置"
                    is javax.net.ssl.SSLException -> "安全连接失败，请检查网络设置"
                    is java.io.IOException -> "网络连接异常，请检查网络后重试"
                    else -> "上传失败: ${e.message ?: "未知错误"}"
                }
                Log.e("ReleaseFlowViewModel", "Upload exception: ${e.javaClass.simpleName}", e)
                _uploadState.value = UploadState.Error(errorMessage)
            }
        }
    }

    /**
     * 重置上传状态
     */
    fun resetUploadState() {
        _uploadState.value = UploadState.Idle
    }
    
    /**
     * 验证音频文件是否符合API要求
     */
    private fun validateAudioFile(file: File): String? {
        // 检查文件大小 (最大50MB)
        val maxSize = 50 * 1024 * 1024L // 50MB
        if (file.length() > maxSize) {
            return "文件过大，最大支持50MB"
        }
        
        // 检查文件格式
        val supportedFormats = listOf("mp3", "wav", "m4a", "aac", "flac", "ogg", "3gp")
        val extension = file.extension.lowercase()
        
        // 检查文件是否可读
        if (!file.canRead()) {
            return "无法读取音频文件，请检查文件权限"
        }
        
        // 检查文件最小大小 (至少1KB)
        if (file.length() < 1024) {
            return "音频文件过小，可能已损坏"
        }
        
        return null // 验证通过
    }
    
    /**
     * 根据文件扩展名获取正确的MIME类型
     */
    private fun getAudioMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            "3gp" -> "audio/3gpp"
            else -> {
                Log.w("ReleaseFlowViewModel", "未知音频格式: ${file.extension}, 使用默认MIME类型")
                "audio/*"
            }
        }
    }
}