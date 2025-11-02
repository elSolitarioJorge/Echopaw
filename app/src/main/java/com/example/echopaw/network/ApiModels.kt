package com.example.echopaw.network

import com.example.echopaw.R
import com.google.gson.annotations.SerializedName

/**
 * API响应基础模型
 * 
 * @param T 数据类型
 * @property code 响应状态码，0表示成功
 * @property data 响应数据内容
 * @property message 响应消息描述
 */
data class ApiResponse<T>(
    @SerializedName("code")
    val code: Int,
    @SerializedName("data")
    val data: T?,
    @SerializedName("message")
    val message: String
)

/**
 * 音频上传结果
 *
 * @property audioId 音频文件唯一标识
 * @property emotion 识别出的情绪
 * @property emotionConfidence 情绪识别置信度（0~1）
 */
data class AudioUploadResult(
    @SerializedName("audio_id")
    val audioId: String,

    @SerializedName("emotion")
    val emotion: String,

    @SerializedName("emotion_confidence")
    val emotionConfidence: Double
)

/**
 * 音频记录信息
 *
 * @property audioId 音频文件唯一标识
 * @property userId 用户唯一标识
 * @property text 音频识别出的文本内容
 * @property emotion 识别出的情绪标签（如 calm、happy、sad 等）
 * @property distance 与当前位置的距离（单位：米）
 * @property audioUrl 音频文件的在线地址
 * @property province 录制位置的省份
 * @property city 录制位置的城市
 * @property district 录制位置的区县
 * @property address 录制位置的详细地址
 * @property locationName 位置信息简写（例如：陕西省西安市长安区）
 */
data class AudioRecord(
    @SerializedName("audio_id")
    val audioId: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("text")
    val text: String? = null,
    @SerializedName("emotion")
    val emotion: String,
    @SerializedName("distance")
    val distance: Int,
    @SerializedName("audio_url")
    val audioUrl: String,
    @SerializedName("province")
    val province: String? = null,
    @SerializedName("city")
    val city: String? = null,
    @SerializedName("district")
    val district: String? = null,
    @SerializedName("address")
    val address: String? = null,
    @SerializedName("location_name")
    val locationName: String? = null
)

/**
 * 带有位置信息的音频记录
 * 
 * 这个类用于在获取音频详细信息后，将位置信息与基础音频记录合并
 * 
 * @property audioRecord 基础音频记录信息
 * @property location 位置信息（从AudioDetail中获取）
 */
data class AudioRecordWithLocation(
    val audioRecord: AudioRecord,
    val location: Location
)

/**
 * 音频详情信息
 *
 * @property audioId 音频文件唯一标识
 * @property text 音频识别出的文本内容
 * @property emotion 识别出的情绪标签（如 calm、happy、sad 等）
 * @property timestamp 音频上传或创建的时间戳
 * @property audioUrl 音频文件的在线地址
 * @property likes 点赞数量
 * @property comments 评论数量
 * @property province 录制位置的省份
 * @property city 录制位置的城市
 * @property district 录制位置的区县
 * @property address 录制位置的详细地址
 * @property locationName 位置信息简写（例如：陕西省西安市长安区）
 */
data class AudioDetail(
    @SerializedName("audio_id")
    val audioId: String,
    @SerializedName("text")
    val text: String,
    @SerializedName("emotion")
    val emotion: String,
    @SerializedName("timestamp")
    val timestamp: String,
    @SerializedName("audio_url")
    val audioUrl: String,
    @SerializedName("likes")
    val likes: Int,
    @SerializedName("comments")
    val comments: Int,
    @SerializedName("province")
    val province: String,
    @SerializedName("city")
    val city: String,
    @SerializedName("district")
    val district: String,
    @SerializedName("address")
    val address: String,
    @SerializedName("location_name")
    val locationName: String,
    @SerializedName("location")
    val location: Location
)

/**
 * 位置信息
 *
 * @property lat 纬度
 * @property lon 经度
 */
data class Location(
    @SerializedName("lat")
    val lat: Double,
    @SerializedName("lon")
    val lon: Double
)






/**
 * 认证响应
 * 
 * @property accessToken 访问令牌
 * @property refreshToken 刷新令牌
 * @property expiresIn 过期时间（秒）
 * @property user 用户信息
 */
data class AuthResponse(
    @SerializedName("accessToken")
    val accessToken: String,
    @SerializedName("refreshToken")
    val refreshToken: String,
    @SerializedName("expiresIn")
    val expiresIn: Long,
    @SerializedName("user")
    val user: UserInfo
)

/**
 * 用户信息
 * 
 * @property id 用户ID
 * @property username 用户名
 * @property email 邮箱
 * @property phone 手机号
 * @property avatar 头像URL
 */
data class UserInfo(
    @SerializedName("id")
    val id: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("phone")
    val phone: String,
    @SerializedName("avatar")
    val avatar: String?
)

/**
 * API结果封装类
 * 
 * 用于统一处理API调用结果
 */
sealed class ApiResult<T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error<T>(val code: Int, val message: String) : ApiResult<T>()
    data class Exception<T>(val throwable: Throwable) : ApiResult<T>()
}

/**
 * 音频播放状态
 */
enum class AudioPlayState {
    IDLE,       // 空闲状态
    LOADING,    // 加载中
    PLAYING,    // 播放中
    PAUSED,     // 暂停
    COMPLETED,  // 播放完成
    ERROR       // 播放错误
}

/**
 * AI情绪识别请求
 * 
 * @property audioUrl 音频文件URL
 */
data class EmotionAnalysisRequest(
    @SerializedName("audio_url")
    val audioUrl: String
)

/**
 * AI情绪识别结果
 * 
 * @property text 识别出的文本内容
 * @property emotion 识别出的情绪标签（如 calm、happy、sad、angry、excited、tired、anxious、peaceful、mysterious）
 * @property confidence 情绪识别置信度（0~1）
 */
data class EmotionAnalysisResult(
    @SerializedName("text")
    val text: String,
    @SerializedName("emotion")
    val emotion: String,
    @SerializedName("confidence")
    val confidence: Double
)

/**
 * 情绪类型枚举
 * 
 * 根据API文档定义的情绪返回值
 */
enum class EmotionType(val value: String, val displayName: String, val iconResource: String, val iconRes: Int) {
    /*HAPPY("happy", "高兴", "ic_emotion_happy", R.drawable.ic_emotion_happy),
    SAD("sad", "伤心", "ic_emotion_sad", R.drawable.ic_emotion_sad),
    ANGRY("angry", "生气", "ic_emotion_angry", R.drawable.ic_emotion_angry),
    CALM("calm", "平静", "ic_emotion_calm", R.drawable.ic_emotion_calm),
    EXCITED("excited", "兴奋", "ic_emotion_excited", R.drawable.ic_emotion_excited),
    TIRED("tired", "疲惫", "ic_emotion_tired", R.drawable.ic_emotion_tired),
    ANXIOUS("anxious", "焦虑", "ic_emotion_anxious", R.drawable.ic_emotion_anxious),
    PEACEFUL("peaceful", "祥和", "ic_emotion_peaceful", R.drawable.ic_emotion_peaceful),
    MYSTERIOUS("mysterious", "神秘", "ic_emotion_mysterious", R.drawable.ic_emotion_mysterious);*/

    HAPPY("happy", "高兴", "img_emotion_happy", R.drawable.img_emotion_happy),
    SAD("sad", "伤心", "img_emotion_anxious", R.drawable.img_emotion_anxious),
    ANGRY("angry", "生气", "img_emotion_angry", R.drawable.img_emotion_angry),
    CALM("calm", "平静", "img_emotion_calm", R.drawable.img_emotion_calm),
    EXCITED("excited", "兴奋", "img_emotion_happy", R.drawable.img_emotion_happy),
    TIRED("tired", "疲惫", "img_emotion_anxious", R.drawable.img_emotion_anxious),
    ANXIOUS("anxious", "焦虑", "img_emotion_anxious", R.drawable.img_emotion_anxious),
    PEACEFUL("peaceful", "祥和", "img_emotion_calm", R.drawable.img_emotion_calm),
    MYSTERIOUS("mysterious", "神秘", "img_emotion_curious", R.drawable.img_emotion_curious),
    CURIOUS("curious", "好奇", "img_emotion_curious", R.drawable.img_emotion_curious);

    companion object {
        /**
         * 根据情绪值获取对应的枚举
         */
        fun fromValue(value: String): EmotionType {
            return values().find { it.value == value } ?: MYSTERIOUS
        }
        
        /**
         * 获取情绪对应的图标资源名称
         */
        fun getIconResource(emotion: String): String {
            return fromValue(emotion).iconResource
        }
        
        /**
         * 根据情绪值获取对应的图标资源ID
         */
        fun fromEmotion(emotion: String): EmotionType {
            return fromValue(emotion)
        }
    }
}