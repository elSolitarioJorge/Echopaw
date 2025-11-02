package com.example.echopaw.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * API服务接口
 * 
 * 定义了与服务器交互的所有API接口，包括：
 * 1. 音频文件上传
 * 2. 附近音频查询
 * 3. 获取音频详情
 */
interface ApiService {
    
    /**
     * 上传音频文件
     * 
     * @param file 音频文件
     * @param latitude 纬度
     * @param longitude 经度
     * @return 上传结果
     */
    @Multipart
    @POST("api/audio/upload")
    suspend fun uploadAudio(
        @Part file: MultipartBody.Part,
        @Part("latitude") latitude: RequestBody,
        @Part("longitude") longitude: RequestBody
    ): Response<ApiResponse<AudioUploadResult>>
    

    /**
     * 查询附近的音频记录
     * 
     * @param latitude 纬度
     * @param longitude 经度
     * @param radius 搜索半径（米）
     * @param limit 返回数量限制
     * @return 附近音频列表
     */
    @GET("api/audio/nearby")
    suspend fun getNearbyAudios(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radius") radius: Int = 1000,
        @Query("limit") limit: Int = 50
    ): Response<ApiResponse<List<AudioRecord>>>
    
    /**
     * 获取音频详情
     * 
     * @param audioId 音频ID
     * @return 音频详细信息
     */
    @GET("api/audio/info/{audioId}")
    suspend fun getAudioDetail(
        @Path("audioId") audioId: String
    ): Response<ApiResponse<AudioDetail>>

    /**
     * AI情绪识别
     * 
     * 分析音频文件的情感状态
     * 
     * @param request 情绪识别请求，包含音频文件URL
     * @return 情绪识别结果
     */
    @POST("api/ai/emotion")
    suspend fun analyzeEmotion(
        @Body request: EmotionAnalysisRequest
    ): Response<ApiResponse<EmotionAnalysisResult>>

}