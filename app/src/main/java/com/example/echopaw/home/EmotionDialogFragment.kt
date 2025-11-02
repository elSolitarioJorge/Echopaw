package com.example.echopaw.home

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.echopaw.R
import com.example.echopaw.network.*
import kotlinx.coroutines.launch

/**
 * 情绪识别对话框Fragment
 * 
 * 用于显示AI情绪识别结果，包含：
 * 1. 加载状态显示
 * 2. 情绪图标和文本显示
 * 3. 确认和编辑按钮交互
 * 4. 错误处理
 */
class EmotionDialogFragment : DialogFragment() {
    
    companion object {
        private const val TAG = "EmotionDialogFragment"
        private const val ARG_AUDIO_URL = "audio_url"
        
        /**
         * 创建对话框实例
         * 
         * @param audioUrl 音频文件URL
         * @return 对话框实例
         */
        fun newInstance(audioUrl: String): EmotionDialogFragment {
            val fragment = EmotionDialogFragment()
            val args = Bundle()
            args.putString(ARG_AUDIO_URL, audioUrl)
            fragment.arguments = args
            return fragment
        }
    }
    
    private var audioUrl: String? = null
    private var emotionResult: EmotionAnalysisResult? = null
    
    // UI组件
    private lateinit var tvTitle: TextView
    private lateinit var ivFace: ImageView
    private lateinit var btnConfirm: ImageView
    private lateinit var btnEdit: ImageView
    
    // 回调接口
    interface EmotionDialogListener {
        fun onEmotionConfirmed(emotion: EmotionAnalysisResult)
        fun onEmotionEdit(emotion: EmotionAnalysisResult)
        fun onEmotionError(error: String)
    }
    
    private var listener: EmotionDialogListener? = null
    
    fun setEmotionDialogListener(listener: EmotionDialogListener) {
        this.listener = listener
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioUrl = arguments?.getString(ARG_AUDIO_URL)
        
        // 设置对话框样式
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Material_Dialog)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_one, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupClickListeners()
        
        // 开始情绪识别
        audioUrl?.let { url ->
            analyzeEmotion(url)
        } ?: run {
            showError("音频URL为空")
        }
    }
    
    /**
     * 初始化视图组件
     */
    private fun initViews(view: View) {
        tvTitle = view.findViewById(R.id.tv_title)
        ivFace = view.findViewById(R.id.iv_face)
        btnConfirm = view.findViewById(R.id.btn_confirm)
        btnEdit = view.findViewById(R.id.btn_edit)
        
        // 初始状态显示加载中
        tvTitle.text = "AI正在识别你的情绪..."
        ivFace.setImageResource(R.drawable.ic_smile) // 默认图标
        btnConfirm.visibility = View.GONE
        btnEdit.visibility = View.GONE
    }
    
    /**
     * 设置点击事件监听器
     */
    private fun setupClickListeners() {
        btnConfirm.setOnClickListener {
            emotionResult?.let { result ->
                Log.d(TAG, "用户确认情绪识别结果: ${result.emotion}")
                listener?.onEmotionConfirmed(result)
                dismiss()
            }
        }
        
        btnEdit.setOnClickListener {
            emotionResult?.let { result ->
                Log.d(TAG, "用户选择编辑情绪: ${result.emotion}")
                listener?.onEmotionEdit(result)
                dismiss()
            }
        }
    }
    
    /**
     * 调用AI情绪识别接口
     */
    private fun analyzeEmotion(audioUrl: String) {
        Log.d(TAG, "开始分析音频情绪: $audioUrl")
        
        lifecycleScope.launch {
            try {
                val request = EmotionAnalysisRequest(audioUrl)
                val response = RetrofitClient.apiService.analyzeEmotion(request)
                
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse != null && apiResponse.code == 0) {
                        val result = apiResponse.data
                        if (result != null) {
                            Log.d(TAG, "情绪识别成功: emotion=${result.emotion}, confidence=${result.confidence}")
                            emotionResult = result
                            showEmotionResult(result)
                        } else {
                            Log.e(TAG, "情绪识别响应数据为空")
                            showError("情绪识别失败：响应数据为空")
                        }
                    } else {
                        val errorMsg = apiResponse?.message ?: "未知错误"
                        Log.e(TAG, "情绪识别API返回错误: code=${apiResponse?.code}, message=$errorMsg")
                        showError("情绪识别失败：$errorMsg")
                    }
                } else {
                    val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                    Log.e(TAG, "情绪识别网络请求失败: $errorMsg")
                    showError("网络请求失败：$errorMsg")
                }
            } catch (e: Exception) {
                Log.e(TAG, "情绪识别异常", e)
                showError("情绪识别异常：${e.message}")
            }
        }
    }
    
    /**
     * 显示情绪识别结果
     */
    private fun showEmotionResult(result: EmotionAnalysisResult) {
        tvTitle.text = "AI识别出你的情绪..."
        
        // 根据情绪类型设置对应图标
        val iconResource = EmotionType.getIconResource(result.emotion)
        val resourceId = resources.getIdentifier(iconResource, "drawable", requireContext().packageName)
        if (resourceId != 0) {
            ivFace.setImageResource(resourceId)
        } else {
            Log.w(TAG, "未找到情绪图标资源: $iconResource")
            ivFace.setImageResource(R.drawable.ic_smile) // 默认图标
        }
        
        // 显示操作按钮
        btnConfirm.visibility = View.VISIBLE
        btnEdit.visibility = View.VISIBLE
        
        Log.d(TAG, "情绪识别结果显示完成: ${result.emotion}")
    }
    
    /**
     * 显示错误信息
     */
    private fun showError(errorMessage: String) {
        tvTitle.text = "情绪识别失败"
        ivFace.setImageResource(R.drawable.ic_emotion_4) // 使用伤心图标表示错误
        
        // 隐藏操作按钮
        btnConfirm.visibility = View.GONE
        btnEdit.visibility = View.GONE
        
        Log.e(TAG, "显示错误信息: $errorMessage")
        listener?.onEmotionError(errorMessage)
        
        // 3秒后自动关闭对话框
        view?.postDelayed({
            if (isAdded && !isDetached) {
                dismiss()
            }
        }, 3000)
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        
        // 设置对话框属性
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }
        
        // 设置点击外部不可取消
        isCancelable = false
        
        return dialog
    }
}