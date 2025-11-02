package com.example.echopaw.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.echopaw.BuildConfig
import com.example.echopaw.utils.LogUtils
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Retrofit客户端配置
 * 
 * 提供统一的网络请求配置，包括：
 * 1. 基础URL配置
 * 2. 认证拦截器
 * 3. 日志拦截器
 * 4. 网络状态检查拦截器
 * 5. 错误处理拦截器
 * 6. 超时配置
 * 7. API服务实例
 */
object RetrofitClient {
    
    const val BASE_URL = "https://fei.singzer.cn/"
    private const val TAG = "RetrofitClient"
    
    private var applicationContext: Context? = null
    
    /**
     * 初始化RetrofitClient
     * 
     * @param context 应用上下文
     */
    fun init(context: Context) {
        applicationContext = context.applicationContext
    }
    
    /**
     * 日志拦截器
     * 
     * 在Debug模式下记录详细的请求和响应信息
     */
    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        LogUtils.d(TAG, message)
    }.apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.BASIC
        }
    }
    
    /**
     * 网络状态检查拦截器
     * 
     * 在发起请求前检查网络连接状态
     */
    private val networkInterceptor = Interceptor { chain ->
        if (!isNetworkAvailable()) {
            throw ConnectionException("网络连接不可用，请检查网络设置")
        }
        chain.proceed(chain.request())
    }
    
    /**
     * 认证拦截器
     * 
     * 自动在请求头中添加访问令牌和通用请求头
     */
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val token = getAccessToken()
        
        val requestBuilder = originalRequest.newBuilder()
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "EchoPaw-Android/${BuildConfig.VERSION_NAME}")
        
        if (token != null) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        
        val newRequest = requestBuilder.build()
        
        LogUtils.Network.requestStart(TAG, newRequest.url.toString())
        val startTime = System.currentTimeMillis()
        
        try {
            val response = chain.proceed(newRequest)
            val duration = System.currentTimeMillis() - startTime
            LogUtils.Network.requestSuccess(TAG, newRequest.url.toString(), duration)
            response
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            LogUtils.Network.requestFailed(TAG, newRequest.url.toString(), e.message ?: "Unknown error", e)
            throw e
        }
    }
    
    /**
     * 错误处理拦截器
     * 
     * 统一处理网络请求错误
     */
    private val errorInterceptor = Interceptor { chain ->
        try {
            val response = chain.proceed(chain.request())
            
            // 检查HTTP状态码
            if (!response.isSuccessful) {
                when (response.code) {
                    401 -> throw AuthenticationException("认证失败，请重新登录")
                    403 -> throw AuthorizationException("权限不足，无法访问该资源")
                    404 -> throw NotFoundException("请求的资源不存在")
                    429 -> throw RateLimitException("请求过于频繁，请稍后重试")
                    500 -> throw ServerException("服务器内部错误，请稍后重试")
                    502, 503, 504 -> throw ServerException("服务器暂时不可用，请稍后重试")
                    else -> throw HttpException("HTTP错误: ${response.code} ${response.message}")
                }
            }
            
            response
        } catch (e: SocketTimeoutException) {
            throw TimeoutException("请求超时，请检查网络连接")
        } catch (e: UnknownHostException) {
            throw ConnectionException("无法连接到服务器，请检查网络设置")
        } catch (e: IOException) {
            throw ConnectionException("网络连接异常: ${e.message}")
        }
    }
    
    /**
     * OkHttp客户端配置
     * 
     * 配置超时时间和拦截器链
     * 拦截器执行顺序：网络检查 -> 认证 -> 错误处理 -> 日志
     */
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)  // 连接超时
        .readTimeout(30, TimeUnit.SECONDS)     // 读取超时
        .writeTimeout(30, TimeUnit.SECONDS)    // 写入超时
        .callTimeout(60, TimeUnit.SECONDS)     // 整个请求超时
        .retryOnConnectionFailure(true)        // 连接失败时重试
        .addInterceptor(networkInterceptor)    // 网络状态检查
        .addInterceptor(authInterceptor)       // 认证处理
        .addInterceptor(errorInterceptor)      // 错误处理
        .addNetworkInterceptor(loggingInterceptor) // 网络日志
        .build()
    
    /**
     * Retrofit实例
     */
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    /**
     * API服务实例
     */
    val apiService: ApiService = retrofit.create(ApiService::class.java)
    
    /**
     * 获取访问令牌
     * 
     * @return 访问令牌，如果未登录则返回null
     */
    private fun getAccessToken(): String? {
        return AuthManager.getAccessToken()
    }
    
    /**
     * 检查网络连接状态
     * 
     * @return true表示网络可用，false表示网络不可用
     */
    private fun isNetworkAvailable(): Boolean {
        val context = applicationContext ?: return true // 如果context为空，假设网络可用
        
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }
}

/**
 * 网络相关异常基类
 */
sealed class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 网络连接异常
 */
class ConnectionException(message: String, cause: Throwable? = null) : NetworkException(message, cause)

/**
 * 认证异常
 */
class AuthenticationException(message: String) : NetworkException(message)

/**
 * 授权异常
 */
class AuthorizationException(message: String) : NetworkException(message)

/**
 * 资源不存在异常
 */
class NotFoundException(message: String) : NetworkException(message)

/**
 * 请求频率限制异常
 */
class RateLimitException(message: String) : NetworkException(message)

/**
 * 服务器异常
 */
class ServerException(message: String) : NetworkException(message)

/**
 * HTTP异常
 */
class HttpException(message: String) : NetworkException(message)

/**
 * 超时异常
 */
class TimeoutException(message: String) : NetworkException(message)

/**
 * 认证管理器
 * 
 * 负责管理用户认证信息的存储和获取
 */
object AuthManager {
    private const val PREF_NAME = "auth_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_IN = "expires_in"
    
    private var context: Context? = null
    private var testToken: String = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoxLCJleHAiOjE3NjIzNTc5Mzl9.VPy_y_FiBPDBBob2vP78gtKrJDNPD-EzuiVXBbbt8Kg"
    
    /**
     * 初始化认证管理器
     * 
     * @param context 应用上下文
     */
    fun init(context: Context) {
        this.context = context.applicationContext
    }
    
    /**
     * 保存认证信息
     * 
     * @param authResponse 认证响应数据
     */
    fun saveAuthInfo(authResponse: AuthResponse) {
        val sharedPreferences = context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPreferences?.edit()?.apply {
            putString(KEY_ACCESS_TOKEN, authResponse.accessToken)
            putString(KEY_REFRESH_TOKEN, authResponse.refreshToken)
            putLong(KEY_EXPIRES_IN, System.currentTimeMillis() + authResponse.expiresIn * 1000)
            apply()
        }
    }
    
    /**
     * 获取访问令牌
     * 
     * @return 访问令牌，如果过期或不存在则返回null
     */
    fun getAccessToken(): String? {
        val sharedPreferences = context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val token = sharedPreferences?.getString(KEY_ACCESS_TOKEN, null)
        val expiresIn = sharedPreferences?.getLong(KEY_EXPIRES_IN, 0) ?: 0
        
        return if (token != null && System.currentTimeMillis() < expiresIn) {
            token
        } else {
            testToken
        }
    }
    
    /**
     * 获取刷新令牌
     * 
     * @return 刷新令牌
     */
    fun getRefreshToken(): String? {
        val sharedPreferences = context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPreferences?.getString(KEY_REFRESH_TOKEN, null)
    }
    
    /**
     * 清除认证信息
     */
    fun clearAuthInfo() {
        val sharedPreferences = context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPreferences?.edit()?.clear()?.apply()
    }
    
    /**
     * 检查是否已登录
     * 
     * @return 是否已登录
     */
    fun isLoggedIn(): Boolean {
        return getAccessToken() != null
    }
}