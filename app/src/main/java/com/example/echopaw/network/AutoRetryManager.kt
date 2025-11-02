package com.example.echopaw.network

import android.util.Log
import com.example.echopaw.config.MapInteractionConfig
import kotlinx.coroutines.*
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.min
import kotlin.math.pow

/**
 * 自动重试管理器
 * 
 * 负责管理网络请求的自动重试机制，包括：
 * 1. 指数退避策略
 * 2. 重试条件判断
 * 3. 失败回调处理
 * 4. 重试统计监控
 */
class AutoRetryManager(
    private val config: MapInteractionConfig
) {
    
    companion object {
        private const val TAG = "AutoRetryManager"
    }

    /**
     * 重试结果
     */
    sealed class RetryResult<T> {
        /**
         * 重试成功
         */
        data class Success<T>(val data: T, val attemptCount: Int) : RetryResult<T>()
        
        /**
         * 重试失败
         */
        data class Failure<T>(
            val error: Throwable,
            val attemptCount: Int,
            val totalDuration: Long
        ) : RetryResult<T>()
    }

    /**
     * 重试策略
     */
    data class RetryPolicy(
        val maxAttempts: Int = 3,
        val initialDelayMs: Long = 1000L,
        val maxDelayMs: Long = 30000L,
        val backoffMultiplier: Double = 2.0,
        val jitterFactor: Double = 0.1,
        val retryableExceptions: Set<Class<out Throwable>> = setOf(
            IOException::class.java,
            SocketTimeoutException::class.java,
            UnknownHostException::class.java
        )
    ) {
        /**
         * 计算下次重试的延迟时间
         */
        fun calculateDelay(attemptNumber: Int): Long {
            val exponentialDelay = (initialDelayMs * backoffMultiplier.pow(attemptNumber - 1)).toLong()
            val cappedDelay = min(exponentialDelay, maxDelayMs)
            
            // 添加抖动以避免雷群效应
            val jitter = (cappedDelay * jitterFactor * Math.random()).toLong()
            return cappedDelay + jitter
        }
        
        /**
         * 判断异常是否可重试
         */
        fun isRetryable(exception: Throwable): Boolean {
            return retryableExceptions.any { it.isInstance(exception) }
        }
    }

    /**
     * 重试监听器
     */
    interface RetryListener {
        /**
         * 重试开始
         */
        fun onRetryStarted(attemptNumber: Int, totalAttempts: Int, delay: Long)
        
        /**
         * 重试失败
         */
        fun onRetryFailed(attemptNumber: Int, exception: Throwable)
        
        /**
         * 重试成功
         */
        fun onRetrySucceeded(attemptNumber: Int, totalDuration: Long)
        
        /**
         * 所有重试都失败
         */
        fun onAllRetriesFailed(totalAttempts: Int, finalException: Throwable, totalDuration: Long)
    }

    /**
     * 重试统计信息
     */
    data class RetryStats(
        val totalRequests: Int,
        val successfulRequests: Int,
        val failedRequests: Int,
        val totalRetries: Int,
        val averageAttempts: Double,
        val averageSuccessDuration: Long,
        val averageFailureDuration: Long
    )

    // 统计信息
    private var totalRequests = 0
    private var successfulRequests = 0
    private var failedRequests = 0
    private var totalRetries = 0
    private var totalSuccessDuration = 0L
    private var totalFailureDuration = 0L

    /**
     * 执行带重试的异步操作
     * 
     * @param operation 要执行的操作
     * @param policy 重试策略
     * @param listener 重试监听器
     * @return 重试结果
     */
    suspend fun <T> executeWithRetry(
        operation: suspend () -> T,
        policy: RetryPolicy = createDefaultPolicy(),
        listener: RetryListener? = null
    ): RetryResult<T> = withContext(Dispatchers.IO) {
        totalRequests++
        val startTime = System.currentTimeMillis()
        var lastException: Throwable? = null
        
        Log.d(TAG, "Starting operation with retry policy: maxAttempts=${policy.maxAttempts}")
        
        for (attempt in 1..policy.maxAttempts) {
            try {
                Log.d(TAG, "Executing attempt $attempt/${policy.maxAttempts}")
                
                val result = operation()
                val duration = System.currentTimeMillis() - startTime
                
                successfulRequests++
                totalSuccessDuration += duration
                totalRetries += (attempt - 1)
                
                listener?.onRetrySucceeded(attempt, duration)
                Log.d(TAG, "Operation succeeded on attempt $attempt after ${duration}ms")
                
                return@withContext RetryResult.Success(result, attempt)
                
            } catch (exception: Throwable) {
                lastException = exception
                val duration = System.currentTimeMillis() - startTime
                
                Log.w(TAG, "Attempt $attempt failed: ${exception.message}", exception)
                listener?.onRetryFailed(attempt, exception)
                
                // 检查是否应该重试
                if (attempt >= policy.maxAttempts) {
                    // 已达到最大重试次数
                    break
                } else if (!policy.isRetryable(exception)) {
                    // 异常不可重试
                    Log.d(TAG, "Exception is not retryable: ${exception::class.simpleName}")
                    break
                } else {
                    // 计算延迟并等待
                    val delay = policy.calculateDelay(attempt)
                    Log.d(TAG, "Retrying in ${delay}ms (attempt ${attempt + 1}/${policy.maxAttempts})")
                    
                    listener?.onRetryStarted(attempt + 1, policy.maxAttempts, delay)
                    delay(delay)
                }
            }
        }
        
        // 所有重试都失败了
        val totalDuration = System.currentTimeMillis() - startTime
        failedRequests++
        totalFailureDuration += totalDuration
        totalRetries += (policy.maxAttempts - 1)
        
        val finalException = lastException ?: RuntimeException("Unknown error")
        listener?.onAllRetriesFailed(policy.maxAttempts, finalException, totalDuration)
        
        Log.e(TAG, "All retries failed after ${totalDuration}ms", finalException)
        
        return@withContext RetryResult.Failure(finalException, policy.maxAttempts, totalDuration)
    }

    /**
     * 创建默认重试策略
     */
    private fun createDefaultPolicy(): RetryPolicy {
        return RetryPolicy(
            maxAttempts = config.maxRetryAttempts,
            initialDelayMs = config.initialRetryDelay,
            maxDelayMs = config.maxRetryDelay,
            backoffMultiplier = config.retryBackoffMultiplier
        )
    }

    /**
     * 执行带重试的网络请求（专门针对音频数据请求）
     */
    suspend fun executeAudioDataRequest(
        operation: suspend () -> List<AudioRecordWithLocation>,
        onProgress: ((Int, Int) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ): RetryResult<List<AudioRecordWithLocation>> {
        
        val listener = object : RetryListener {
            override fun onRetryStarted(attemptNumber: Int, totalAttempts: Int, delay: Long) {
                onProgress?.invoke(attemptNumber, totalAttempts)
                Log.d(TAG, "Audio data request retry $attemptNumber/$totalAttempts starting in ${delay}ms")
            }
            
            override fun onRetryFailed(attemptNumber: Int, exception: Throwable) {
                Log.w(TAG, "Audio data request attempt $attemptNumber failed", exception)
            }
            
            override fun onRetrySucceeded(attemptNumber: Int, totalDuration: Long) {
                Log.d(TAG, "Audio data request succeeded on attempt $attemptNumber")
            }
            
            override fun onAllRetriesFailed(totalAttempts: Int, finalException: Throwable, totalDuration: Long) {
                onError?.invoke(finalException)
                Log.e(TAG, "Audio data request failed after $totalAttempts attempts", finalException)
            }
        }
        
        return executeWithRetry(operation, createDefaultPolicy(), listener)
    }

    /**
     * 执行带重试的地理编码请求
     */
    suspend fun executeGeocodingRequest(
        operation: suspend () -> String,
        onError: ((Throwable) -> Unit)? = null
    ): RetryResult<String> {
        
        // 地理编码请求使用更宽松的重试策略
        val geocodingPolicy = RetryPolicy(
            maxAttempts = 2, // 地理编码重试次数较少
            initialDelayMs = 500L,
            maxDelayMs = 5000L,
            backoffMultiplier = 2.0
        )
        
        val listener = object : RetryListener {
            override fun onRetryStarted(attemptNumber: Int, totalAttempts: Int, delay: Long) {
                Log.d(TAG, "Geocoding request retry $attemptNumber/$totalAttempts starting in ${delay}ms")
            }
            
            override fun onRetryFailed(attemptNumber: Int, exception: Throwable) {
                Log.w(TAG, "Geocoding request attempt $attemptNumber failed", exception)
            }
            
            override fun onRetrySucceeded(attemptNumber: Int, totalDuration: Long) {
                Log.d(TAG, "Geocoding request succeeded on attempt $attemptNumber")
            }
            
            override fun onAllRetriesFailed(totalAttempts: Int, finalException: Throwable, totalDuration: Long) {
                onError?.invoke(finalException)
                Log.e(TAG, "Geocoding request failed after $totalAttempts attempts", finalException)
            }
        }
        
        return executeWithRetry(operation, geocodingPolicy, listener)
    }

    /**
     * 创建自定义重试策略
     */
    fun createCustomPolicy(
        maxAttempts: Int = config.maxRetryAttempts,
        initialDelayMs: Long = config.initialRetryDelay,
        maxDelayMs: Long = config.maxRetryDelay,
        backoffMultiplier: Double = config.retryBackoffMultiplier,
        retryableExceptions: Set<Class<out Throwable>>? = null
    ): RetryPolicy {
        return RetryPolicy(
            maxAttempts = maxAttempts,
            initialDelayMs = initialDelayMs,
            maxDelayMs = maxDelayMs,
            backoffMultiplier = backoffMultiplier,
            retryableExceptions = retryableExceptions ?: RetryPolicy().retryableExceptions
        )
    }

    /**
     * 获取重试统计信息
     */
    fun getRetryStats(): RetryStats {
        val averageAttempts = if (totalRequests > 0) {
            (totalRequests + totalRetries).toDouble() / totalRequests
        } else 0.0
        
        val averageSuccessDuration = if (successfulRequests > 0) {
            totalSuccessDuration / successfulRequests
        } else 0L
        
        val averageFailureDuration = if (failedRequests > 0) {
            totalFailureDuration / failedRequests
        } else 0L
        
        return RetryStats(
            totalRequests = totalRequests,
            successfulRequests = successfulRequests,
            failedRequests = failedRequests,
            totalRetries = totalRetries,
            averageAttempts = averageAttempts,
            averageSuccessDuration = averageSuccessDuration,
            averageFailureDuration = averageFailureDuration
        )
    }

    /**
     * 重置统计信息
     */
    fun resetStats() {
        totalRequests = 0
        successfulRequests = 0
        failedRequests = 0
        totalRetries = 0
        totalSuccessDuration = 0L
        totalFailureDuration = 0L
        Log.d(TAG, "Reset retry statistics")
    }

    /**
     * 检查异常是否可重试
     */
    fun isRetryableException(exception: Throwable): Boolean {
        return createDefaultPolicy().isRetryable(exception)
    }

    /**
     * 计算指定重试次数的总延迟时间
     */
    fun calculateTotalDelay(attempts: Int, policy: RetryPolicy = createDefaultPolicy()): Long {
        var totalDelay = 0L
        for (attempt in 1 until attempts) {
            totalDelay += policy.calculateDelay(attempt)
        }
        return totalDelay
    }

    /**
     * 获取成功率
     */
    fun getSuccessRate(): Double {
        return if (totalRequests > 0) {
            successfulRequests.toDouble() / totalRequests * 100
        } else 0.0
    }

    /**
     * 获取平均重试次数
     */
    fun getAverageRetries(): Double {
        return if (totalRequests > 0) {
            totalRetries.toDouble() / totalRequests
        } else 0.0
    }
}