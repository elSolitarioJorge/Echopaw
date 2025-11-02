package com.example.echopaw.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.echopaw.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * 网络诊断工具类
 * 提供网络连接测试和诊断功能
 */
object NetworkDiagnostic {
    private const val TAG = "NetworkDiagnostic"
    
    /**
     * 检查网络连接状态
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * 获取网络类型
     */
    fun getNetworkType(context: Context): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return "无网络连接"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "未知网络"
        
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "移动数据"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
            else -> "其他网络"
        }
    }
    
    /**
     * 测试DNS解析
     */
    suspend fun testDnsResolution(hostname: String): Boolean = withContext(Dispatchers.IO) {
        try {
            InetAddress.getByName(hostname)
            Log.d(TAG, "DNS解析成功: $hostname")
            true
        } catch (e: UnknownHostException) {
            Log.e(TAG, "DNS解析失败: $hostname", e)
            false
        }
    }
    
    /**
     * 测试服务器连接
     */
    suspend fun testServerConnection(url: String): NetworkTestResult = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            
            val request = Request.Builder()
                .url(url)
                .head() // 使用HEAD请求减少数据传输
                .build()
            
            val response = client.newCall(request).execute()
            val result = NetworkTestResult(
                success = response.isSuccessful,
                statusCode = response.code,
                message = if (response.isSuccessful) "连接成功" else "HTTP ${response.code}: ${response.message}",
                responseTime = response.receivedResponseAtMillis - response.sentRequestAtMillis
            )
            
            Log.d(TAG, "服务器连接测试: $url - ${result.message}")
            result
        } catch (e: Exception) {
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "无法解析服务器地址"
                is java.net.SocketTimeoutException -> "连接超时"
                is java.net.ConnectException -> "无法连接到服务器"
                is javax.net.ssl.SSLException -> "SSL连接失败"
                else -> "连接失败: ${e.message}"
            }
            
            Log.e(TAG, "服务器连接测试失败: $url", e)
            NetworkTestResult(
                success = false,
                statusCode = -1,
                message = errorMessage,
                responseTime = -1
            )
        }
    }
    
    /**
     * 执行完整的网络诊断
     */
    suspend fun performFullDiagnosis(context: Context): NetworkDiagnosisResult = withContext(Dispatchers.IO) {
        val baseUrl = RetrofitClient.BASE_URL
        val hostname = baseUrl.substringAfter("://").substringBefore(":")
        
        NetworkDiagnosisResult(
            networkAvailable = isNetworkAvailable(context),
            networkType = getNetworkType(context),
            dnsResolution = testDnsResolution(hostname),
            serverConnection = testServerConnection(baseUrl),
            apiEndpointTest = testServerConnection("${baseUrl}api/audio/upload")
        )
    }
}

/**
 * 网络测试结果
 */
data class NetworkTestResult(
    val success: Boolean,
    val statusCode: Int,
    val message: String,
    val responseTime: Long
)

/**
 * 网络诊断结果
 */
data class NetworkDiagnosisResult(
    val networkAvailable: Boolean,
    val networkType: String,
    val dnsResolution: Boolean,
    val serverConnection: NetworkTestResult,
    val apiEndpointTest: NetworkTestResult
) {
    /**
     * 生成诊断报告
     */
    fun generateReport(): String {
        return buildString {
            appendLine("=== 网络诊断报告 ===")
            appendLine("网络状态: ${if (networkAvailable) "已连接" else "未连接"}")
            appendLine("网络类型: $networkType")
            appendLine("DNS解析: ${if (dnsResolution) "正常" else "失败"}")
            appendLine()
            appendLine("服务器连接测试:")
            appendLine("  状态: ${if (serverConnection.success) "成功" else "失败"}")
            appendLine("  消息: ${serverConnection.message}")
            if (serverConnection.responseTime > 0) {
                appendLine("  响应时间: ${serverConnection.responseTime}ms")
            }
            appendLine()
            appendLine("API接口测试:")
            appendLine("  状态: ${if (apiEndpointTest.success) "成功" else "失败"}")
            appendLine("  消息: ${apiEndpointTest.message}")
            if (apiEndpointTest.responseTime > 0) {
                appendLine("  响应时间: ${apiEndpointTest.responseTime}ms")
            }
            appendLine()
            appendLine("建议:")
            when {
                !networkAvailable -> appendLine("- 请检查网络连接")
                !dnsResolution -> appendLine("- DNS解析失败，请检查网络设置或尝试切换网络")
                !serverConnection.success -> appendLine("- 无法连接到服务器，请稍后重试或联系技术支持")
                !apiEndpointTest.success -> appendLine("- API服务暂时不可用，请稍后重试")
                else -> appendLine("- 网络连接正常")
            }
        }
    }
}