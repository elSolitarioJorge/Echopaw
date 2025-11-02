package com.example.echopaw.home

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Layout
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.AlignmentSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.echopaw.utils.ToastUtils
import android.media.AudioAttributes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.echopaw.R
import com.example.echopaw.audio.AudioPlayerManager
import com.example.echopaw.audio.AudioErrorHandler
import com.example.echopaw.databinding.FragmentRecordBinding
import com.example.echopaw.network.EmotionAnalysisRequest
import com.example.echopaw.network.EmotionType
import com.example.echopaw.network.RetrofitClient
import kotlinx.coroutines.launch
import androidx.core.graphics.drawable.toDrawable
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.location.Location
import android.location.LocationManager
import android.location.Geocoder
import android.content.Context
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 录制Fragment
 * 
 * 该Fragment负责音频录制功能的用户界面：
 * 1. 提供录音控制界面（开始/停止录音）
 * 2. 实时显示录音状态和时长
 * 3. 展示音频波形可视化效果
 * 4. 处理录音权限申请和管理
 * 5. 提供录音动画效果和用户反馈
 * 
 * 使用MVVM架构，通过RecordViewModel管理录音逻辑和状态
 */
class RecordFragment : Fragment() {

    /**
     * ViewBinding实例，可为空
     * 使用私有可变属性确保在Fragment销毁时正确清理
     */
    private var _binding: FragmentRecordBinding? = null
    
    /**
     * ViewBinding访问器
     * 提供非空的binding实例访问，简化视图操作
     */
    private val binding get() = _binding!!

    /**
     * 录制ViewModel实例
     * 使用viewModels()委托创建，自动管理生命周期
     */
    private val viewModel: RecordViewModel by viewModels()

    /**
     * 发布流程ViewModel实例
     * 使用activityViewModels()委托创建，在Activity范围内共享状态
     */
    private val flowViewModel: ReleaseFlowViewModel by activityViewModels()

    /**
     * 录音权限请求码
     * 用于识别录音权限申请的回调结果
     */
    private val REQUEST_RECORD_PERMISSIONS = 101
    
    /**
     * 所需权限数组
     * 包含录音功能必需的权限列表
     */
    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    /**
     * 动画运行状态标志
     * 用于控制录音时的气泡动画效果
     */
    private var isAnimationRunning = false
    
    /**
     * 日期显示TextView
     */
    private lateinit var dateTextView: TextView
    
    /**
     * 位置显示TextView
     */
    private lateinit var locationTextView: TextView

    /**
     * 音频播放管理器，用于播放录制的音频
     */
    private lateinit var audioPlayerManager: AudioPlayerManager
    
    /**
     * 音频错误处理器
     */
    private val audioErrorHandler = AudioErrorHandler.getInstance()
    
    // 播放器与进度更新
    private var playbackHandler: Handler? = null
    private var lastRecordedFile: java.io.File? = null

    /**
     * 创建Fragment视图
     * 
     * 该方法负责Fragment视图的创建和初始化：
     * 1. 使用ViewBinding inflate布局
     * 2. 返回根视图用于显示
     * 
     * @param inflater 布局填充器，用于将XML布局转换为View对象
     * @param container 父容器，Fragment视图将被添加到此容器中
     * @param savedInstanceState 保存的实例状态，用于恢复Fragment状态
     * @return 创建的根视图
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * 视图创建完成后的初始化
     * 
     * 该方法在视图创建完成后执行初始化操作：
     * 1. 设置视图组件和观察ViewModel状态
     * 2. 配置输入框的提示文字样式
     * 3. 设置录音按钮和返回按钮的点击事件
     * 4. 初始化光标位置和焦点
     * 
     * @param view 创建的视图
     * @param savedInstanceState 保存的实例状态
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 初始化音频播放管理器
        audioPlayerManager = AudioPlayerManager(requireContext(), lifecycleScope)
        
        setupViews()
        observeViewModel()
        
        // 初始化时间和位置
        initializeTimeAndLocation()

        // 设置提示文字样式（保持居中效果）
        val editText: EditText = binding.inputBox
        val hintText = "输入分享你此刻的心情..."
        val spannableString = SpannableString(hintText)

        // 设置提示文字颜色（半透明白色）
        spannableString.setSpan(
            ForegroundColorSpan(0x80FFFFFF.toInt()),
            0,
            hintText.length,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        // 设置提示文字大小（1.1倍）
        spannableString.setSpan(
            RelativeSizeSpan(1.1f),
            0,
            hintText.length,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        // 设置提示文字为粗体
        spannableString.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            hintText.length,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        editText.hint = spannableString

        // 关键：让光标显示在最左边
        binding.inputBox.post {
            binding.inputBox.requestFocus()
            binding.inputBox.setSelection(0) // 光标设置到位置0（最前面）
        }

        // 设置录音按钮点击事件
        binding.recordBtn.setOnClickListener {
            when (viewModel.recordingState.value) {
                is RecordViewModel.RecordingState.Recording -> viewModel.stopRecording()
                else -> startRecording()
            }
        }
        // 设置返回按钮点击事件
        binding.back.setOnClickListener {
            requireActivity().finish()
        }
        
        // 设置确认按钮点击事件
        binding.ivConfirm.setOnClickListener {
            showCustomDialog()
        }

        // 设置重置按钮点击事件：播放最近录音
        binding.ivReset.setOnClickListener {
            onResetClick()
        }
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            WindowInsetsCompat.CONSUMED
        }
    }

    /**
     * 设置视图组件
     * 
     * 该方法配置视图组件的基本属性：
     * 1. 设置气泡动画的中心点
     * 2. 确保动画效果从视图中心开始
     */
    private fun setupViews() {
        binding.bubble.post {
            binding.bubble.pivotX = binding.bubble.width / 2f
            binding.bubble.pivotY = binding.bubble.height / 2f
        }
        
        // 绑定date和location视图
        dateTextView = binding.date
        locationTextView = binding.location
    }

    /**
     * 观察ViewModel状态变化
     * 
     * 该方法设置对ViewModel各种状态的观察：
     * 1. 录音状态变化 - 更新UI显示
     * 2. 录音时长变化 - 更新时间显示
     * 3. 波形数据变化 - 更新波形可视化
     * 
     * 使用协程和生命周期感知确保安全的状态观察
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.recordingState.collect { state ->
                        updateUIState(state)
                    }
                }
                launch {
                    viewModel.recordingTime.collect { time ->
                        binding.recordingTime.text = time
                    }
                }
                launch {
                    viewModel.waveformData.collect { data ->
                        binding.waveform.updateWaveform(data)
                    }
                }
            }
        }
    }

    /**
     * 开始录音
     * 
     * 该方法处理录音开始的逻辑：
     * 1. 检查是否具有必要的录音权限
     * 2. 如果有权限，通过ViewModel开始录音
     * 3. 如果没有权限，请求录音权限
     */
    private fun startRecording() {
        if (hasRequiredPermissions()) {
            viewModel.startRecording(requireContext())
        } else {
            requestPermissions(requiredPermissions, REQUEST_RECORD_PERMISSIONS)
        }
    }

    /**
     * 检查是否具有必要权限
     * 
     * 该方法验证应用是否已获得录音功能所需的权限：
     * 1. 遍历所有必需权限
     * 2. 检查每个权限的授权状态
     * 
     * @return 如果所有必需权限都已授予则返回true，否则返回false
     */
    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 权限请求结果回调
     * 
     * 该方法处理权限请求的结果：
     * 1. 检查请求码是否为录音权限请求
     * 2. 如果权限被授予，开始录音
     * 3. 如果权限被拒绝，显示提示信息
     * 
     * @param requestCode 权限请求码
     * @param permissions 请求的权限数组
     * @param grantResults 权限授予结果数组
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_PERMISSIONS) {
            // 检查录音权限
            val recordPermissionGranted = permissions.zip(grantResults.toTypedArray())
                .any { (permission, result) ->
                    permission == Manifest.permission.RECORD_AUDIO && result == PackageManager.PERMISSION_GRANTED
                }
            
            // 检查位置权限
            val locationPermissionGranted = permissions.zip(grantResults.toTypedArray())
                .any { (permission, result) ->
                    (permission == Manifest.permission.ACCESS_FINE_LOCATION || 
                     permission == Manifest.permission.ACCESS_COARSE_LOCATION) && 
                    result == PackageManager.PERMISSION_GRANTED
                }
            
            // 如果获得了录音权限，开始录音
            if (recordPermissionGranted) {
                startRecording()
            }
            
            // 如果获得了位置权限，重新获取位置
            if (locationPermissionGranted) {
                getCurrentLocation()
            }
            
            // 处理被拒绝的权限
            val deniedPermissions = permissions.filterIndexed { index, _ ->
                grantResults[index] != PackageManager.PERMISSION_GRANTED
            }
            
            if (deniedPermissions.isNotEmpty()) {
                val shouldShowRationale = deniedPermissions.any { permission ->
                    shouldShowRequestPermissionRationale(permission)
                }
                
                val recordDenied = deniedPermissions.contains(Manifest.permission.RECORD_AUDIO)
                val locationDenied = deniedPermissions.any { 
                    it == Manifest.permission.ACCESS_FINE_LOCATION || it == Manifest.permission.ACCESS_COARSE_LOCATION 
                }
                
                val message = when {
                    recordDenied && locationDenied -> {
                        if (shouldShowRationale) {
                            "需要录音和位置权限才能完整使用功能，请授予相关权限"
                        } else {
                            "录音和位置权限被拒绝，请在设置中手动开启"
                        }
                    }
                    recordDenied -> {
                        if (shouldShowRationale) {
                            "需要录音权限才能使用录音功能，请授予录音权限后重试"
                        } else {
                            "录音权限被拒绝，请在设置中手动开启录音权限"
                        }
                    }
                    locationDenied -> {
                        if (shouldShowRationale) {
                            "需要位置权限才能自动获取位置信息"
                        } else {
                            "位置权限被拒绝，将使用默认位置"
                        }
                    }
                    else -> ""
                }
                
                if (message.isNotEmpty()) {
                    ToastUtils.showWarning(requireContext(), message)
                    Log.w("RecordFragment", "Permissions denied: ${deniedPermissions.joinToString()}")
                }
            }
        }
    }

    /**
     * 更新UI状态
     * 
     * 该方法根据录音状态更新用户界面：
     * 1. Recording状态：显示录音中的UI元素和动画
     * 2. Idle/Completed状态：隐藏录音UI，显示完成提示
     * 3. Error状态：隐藏录音UI，显示错误信息
     * 
     * @param state 当前的录音状态
     */
    private fun updateUIState(state: RecordViewModel.RecordingState) {
        when (state) {
            is RecordViewModel.RecordingState.Recording -> {
                binding.bubble.visibility = View.VISIBLE
                binding.bubbleTwo.visibility = View.GONE
                binding.waveform.visibility = View.VISIBLE
                binding.ivConfirm.visibility = View.INVISIBLE
                binding.ivReset.visibility = View.INVISIBLE
                binding.recordingTime.visibility = View.VISIBLE
                binding.recordBtn.setImageResource(R.drawable.ic_microphoneing)
                binding.recordingStatus.text = "录音中"
                startBubbleAnimation()
            }
            /*is RecordViewModel.RecordingState.Idle,
            is RecordViewModel.RecordingState.Completed -> {
                binding.bubble.visibility = View.INVISIBLE
                binding.waveform.visibility = View.INVISIBLE
                binding.recordingTime.visibility = View.INVISIBLE
                binding.recordBtn.setImageResource(R.drawable.ic_microphone)
                binding.recordingStatus.text = "点击录音"
                isAnimationRunning = false
                binding.bubble.clearAnimation()
                if (state is RecordViewModel.RecordingState.Completed) {
                    ToastUtils.showSuccess(requireContext(), "录音已保存: ${state.file.name}")
                }
            }*/

            is RecordViewModel.RecordingState.Idle -> {
                binding.bubble.visibility = View.INVISIBLE
                binding.bubbleTwo.visibility = View.GONE
                binding.waveform.visibility = View.INVISIBLE
                binding.ivConfirm.visibility = View.INVISIBLE
                binding.ivReset.visibility = View.INVISIBLE
                binding.recordingTime.visibility = View.INVISIBLE
                binding.recordBtn.setImageResource(R.drawable.ic_microphone)
                binding.recordingStatus.text = "录音"
                isAnimationRunning = false
                binding.bubble.clearAnimation()
            }

            is RecordViewModel.RecordingState.Completed -> {
                binding.bubble.visibility = View.INVISIBLE
                binding.bubbleTwo.visibility = View.VISIBLE
                binding.waveform.visibility = View.INVISIBLE
                binding.ivConfirm.visibility = View.VISIBLE
                binding.ivReset.visibility = View.VISIBLE
                binding.recordingTime.visibility = View.INVISIBLE
                binding.recordBtn.setImageResource(R.drawable.ic_microphoneed)
                binding.recordingStatus.text = "结束录音"
                isAnimationRunning = false
                binding.bubble.clearAnimation()
                lastRecordedFile = state.file
                /*Toast.makeText(
                    context,
                    "录音已保存: ${state.file.name}",
                    Toast.LENGTH_SHORT
                ).show()*/
            }

            is RecordViewModel.RecordingState.Error -> {
                // 显示详细的错误信息
                ToastUtils.Recording.showRecordingFailed(context, state.message)
                
                // 重置UI状态
                binding.bubble.visibility = View.INVISIBLE
                binding.bubbleTwo.visibility = View.GONE
                binding.waveform.visibility = View.INVISIBLE
                binding.recordingTime.visibility = View.INVISIBLE
                binding.recordBtn.setImageResource(R.drawable.ic_microphone)
                binding.recordingStatus.text = "录音失败，点击重试"
                isAnimationRunning = false
                binding.bubble.clearAnimation()
                
                // 清除可能的无效文件引用
                flowViewModel.setAudioFile(null)
                
                // 记录错误日志
                Log.e("RecordFragment", "Recording error: ${state.message}")
            }
        }
    }

    /**
     * 处理 iv_reset 点击播放最近录音
     */
    private fun onResetClick() {
        val state = viewModel.recordingState.value
        if (state is RecordViewModel.RecordingState.Recording) {
            ToastUtils.Recording.showRecordingInProgress(requireContext())
            return
        }

        val file = when (state) {
            is RecordViewModel.RecordingState.Completed -> state.file
            else -> lastRecordedFile
        }

        if (file == null || !file.exists()) {
            ToastUtils.Recording.showNoRecording(requireContext())
            return
        }

        try {
            startPlayback(file)
        } catch (e: Exception) {
            ToastUtils.showError(requireContext(), "播放失败：${e.message ?: "未知错误"}")
            stopPlayback(true)
        }
    }

    private fun startPlayback(file: java.io.File) {
        stopPlayback(true)
        
        // 设置播放监听器
        audioPlayerManager.setPlaybackListener(object : AudioPlayerManager.PlaybackListener {
                override fun onPlaybackStarted(audioId: String) {
                    binding.recordingStatus.text = "播放中"
                    binding.recordingTime.visibility = View.VISIBLE
                    startPlaybackProgress()
                }
                
                override fun onPlaybackPaused(audioId: String) {
                    binding.recordingStatus.text = "播放暂停"
                }
                
                override fun onPlaybackCompleted(audioId: String) {
                    ToastUtils.showInfo(requireContext(), "播放结束")
                    binding.recordingStatus.text = "结束录音"
                    stopPlayback(true)
                    binding.recordingTime.visibility = View.INVISIBLE
                }
                
                override fun onPlaybackError(audioId: String, error: String) {
                    audioErrorHandler.handlePlaybackError(
                        requireContext(),
                        audioId,
                        Exception(error)
                    )
                    stopPlayback(true)
                }
                
                override fun onProgressUpdate(audioId: String, currentPosition: Int, duration: Int) {
                    binding.recordingTime.text = "${formatTime(currentPosition.toLong())} / ${formatTime(duration.toLong())}"
                }
                
                override fun onBufferingUpdate(audioId: String, percent: Int) {
                    // 缓冲更新处理，可以在这里显示缓冲进度
                }
            })
        
        // 使用AudioPlayerManager播放音频
        audioPlayerManager.playAudio(
            audioId = file.absolutePath,
            audioUrl = file.absolutePath
        )
    }

    private fun startPlaybackProgress() {
        // 进度更新现在由AudioPlayerManager的onProgressUpdate回调处理
        // 这个方法保留为空，以保持兼容性
    }

    private fun stopPlayback(release: Boolean) {
        // 停止音频播放
        audioPlayerManager.stopAudio()
        playbackHandler?.removeCallbacksAndMessages(null)
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }

    /**
     * 开始气泡动画
     * 
     * 该方法启动录音时的气泡动画效果：
     * 1. 检查Fragment和binding的有效性
     * 2. 防止重复启动动画
     * 3. 设置动画运行标志并开始循环动画
     */
    private fun startBubbleAnimation() {
        if (!isAdded || _binding == null || isAnimationRunning) {
            return
        }

        isAnimationRunning = true
        animateBubbleLoop()
    }

    /**
     * 气泡循环动画
     * 
     * 该方法实现气泡的循环缩放动画：
     * 1. 放大到1.05倍（600ms）
     * 2. 缩小到原始大小（600ms）
     * 3. 循环执行直到动画停止
     * 
     * 包含完整的生命周期检查，确保动画安全执行
     */
    private fun animateBubbleLoop() {
        if (!isAdded || _binding == null) {
            isAnimationRunning = false
            return
        }

        binding.bubble.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(600)
            .withEndAction {
                if (!isAdded || _binding == null) {
                    isAnimationRunning = false
                    return@withEndAction
                }
                binding.bubble.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(600)
                    .withEndAction {
                        if (isAdded && _binding != null && isAnimationRunning) {
                            animateBubbleLoop()
                        } else {
                            isAnimationRunning = false
                        }
                    }
                    .start()
            }.start()
    }

    /**
     * 显示自定义对话框
     * 
     * 该方法使用dialog_one.xml布局创建并显示自定义对话框：
     * 1. 使用LayoutInflater加载dialog_one.xml布局
     * 2. 创建AlertDialog并设置自定义视图
     * 3. 为对话框中的按钮设置点击事件
     * 4. 显示对话框
     * 
     * 对话框功能：
     * - 确认按钮：执行确认操作并关闭对话框
     * - 编辑按钮：执行编辑操作并关闭对话框
     * - 点击对话框外部或返回键可关闭对话框
     */
    private fun showCustomDialog() {
        // 首先检查是否有有效的录音文件
        val currentState = viewModel.recordingState.value
        if (currentState !is RecordViewModel.RecordingState.Completed) {
            ToastUtils.showWarning(requireContext(), "请先录制音频")
            return
        }

        // 使用LayoutInflater加载dialog_one.xml布局
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_one, null)
        
        // 创建AlertDialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false) // 不允许点击外部关闭对话框
            .create()

        // 获取对话框中的控件
        val ivFace = dialogView.findViewById<ImageView>(R.id.iv_face)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_title)
        val btnConfirm = dialogView.findViewById<ImageView>(R.id.btn_confirm)
        val btnEdit = dialogView.findViewById<ImageView>(R.id.btn_edit)
        
        // 显示对话框
        dialog.show()
        
        // 设置对话框背景透明
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        // 设置对话框大小
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(), // 90%屏宽
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        // 初始状态：显示加载中
        tvTitle.text = "AI正在分析你的情绪..."
        ivFace.setImageResource(R.drawable.ic_record2) // 默认图标
        btnConfirm.isEnabled = false
        btnEdit.isEnabled = false
        btnConfirm.alpha = 0.5f
        btnEdit.alpha = 0.5f
        
        // 调用AI情绪识别接口
        analyzeEmotion(currentState.file) { emotionType ->
            if (isAdded && !dialog.isShowing.not()) {
                // 更新UI显示识别结果
                tvTitle.text = "AI识别出你的情绪..."
                ivFace.setImageResource(emotionType.iconRes)
                
                // 将情绪结果保存到ViewModel中
                flowViewModel.setEmotionType(emotionType)
                
                // 启用按钮
                btnConfirm.isEnabled = true
                btnEdit.isEnabled = true
                btnConfirm.alpha = 1.0f
                btnEdit.alpha = 1.0f
                
                Log.d("RecordFragment", "AI情绪识别完成: ${emotionType.displayName}")
            }
        }
        
        // 设置确认按钮点击事件：直接进入发布页面
        btnConfirm.setOnClickListener {
            if (btnConfirm.isEnabled) {
                dialog.dismiss()
                // 将录音文件传递给ReleaseFragment
                navigateToReleaseFragment()
            }
        }
        
        // 设置编辑按钮点击事件：先弹出情绪选择对话框，确认后进入发布页面
        btnEdit.setOnClickListener {
            if (btnEdit.isEnabled) {
                // 关闭当前对话框
                dialog.dismiss()
                
                // 显示情绪选择对话框
                val emotionDialog = EmotionSelectionDialogFragment.newInstance()
                emotionDialog.show(childFragmentManager, EmotionSelectionDialogFragment.TAG)
            }
        }
    }
    
    /**
     * 调用AI情绪识别接口
     * 先上传音频文件，然后使用返回的URL进行情绪识别
     */
    private fun analyzeEmotion(audioFile: File, onResult: (EmotionType) -> Unit) {
        lifecycleScope.launch {
            try {
                Log.d("RecordFragment", "开始AI情绪识别，文件: ${audioFile.name}, 大小: ${audioFile.length()} bytes")
                
                // 第一步：上传音频文件
                val requestFile = audioFile.asRequestBody("audio/*".toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", audioFile.name, requestFile)
                
                // 获取当前位置（使用默认值）
                val latitude = RequestBody.create("text/plain".toMediaTypeOrNull(), "0.0")
                val longitude = RequestBody.create("text/plain".toMediaTypeOrNull(), "0.0")
                
                val uploadResponse = RetrofitClient.apiService.uploadAudio(filePart, latitude, longitude)
                
                if (uploadResponse.isSuccessful && uploadResponse.body() != null) {
                    val uploadResult = uploadResponse.body()!!
                    if (uploadResult.code == 200 && uploadResult.data != null) {
                        // 上传成功，直接使用上传结果中的情绪信息
                        val emotionValue = uploadResult.data.emotion
                        val emotionType = EmotionType.fromEmotion(emotionValue)
                        
                        Log.d("RecordFragment", "音频上传并识别成功: $emotionValue -> ${emotionType.displayName}")
                        onResult(emotionType)
                    } else {
                        Log.e("RecordFragment", "音频上传失败: ${uploadResult.message}")
                        // 使用默认情绪
                        onResult(EmotionType.CALM)
                        ToastUtils.showWarning(requireContext(), "音频上传失败，使用默认情绪")
                    }
                } else {
                    val errorMessage = when (uploadResponse.code()) {
                        401 -> "认证失败，请重新登录"
                        403 -> "权限不足"
                        413 -> "文件过大"
                        415 -> "不支持的文件格式"
                        429 -> "请求过于频繁，请稍后重试"
                        in 500..599 -> "服务器错误，请稍后重试"
                        else -> "网络错误: ${uploadResponse.code()}"
                    }
                    Log.e("RecordFragment", "音频上传请求失败: $errorMessage")
                    // 使用默认情绪
                    onResult(EmotionType.CALM)
                    ToastUtils.showError(requireContext(), errorMessage)
                }
                
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is java.net.UnknownHostException -> "无法连接到服务器，请检查网络"
                    is java.net.SocketTimeoutException -> "网络连接超时，请重试"
                    is java.net.ConnectException -> "无法连接到服务器"
                    else -> "情绪识别异常: ${e.message}"
                }
                Log.e("RecordFragment", "情绪识别异常", e)
                // 使用默认情绪
                onResult(EmotionType.CALM)
                ToastUtils.showError(requireContext(), errorMessage)
            }
        }
    }

    /**
     * 导航到发布页面并传递录音文件
     * 
     * 该方法负责：
     * 1. 获取当前录音状态和文件
     * 2. 将录音文件设置到共享ViewModel中
     * 3. 导航到ReleaseFragment
     */
    private fun navigateToReleaseFragment() {
        try {
            val currentState = viewModel.recordingState.value
            if (currentState is RecordViewModel.RecordingState.Completed) {
                // 将录音文件设置到共享ViewModel中
                flowViewModel.setAudioFile(currentState.file)
                
                // 导航到ReleaseFragment
                if (isAdded && !parentFragmentManager.isDestroyed) {
                    parentFragmentManager.beginTransaction()
                        .setCustomAnimations(
                            R.anim.slide_in_right, R.anim.slide_out_left,
                            R.anim.slide_in_left, R.anim.slide_out_right
                        )
                        .replace(R.id.fragment_container_view, ReleaseFragment())
                        .addToBackStack("release")
                        .commit()
                }
            }
        } catch (e: Exception) {
            Log.e("RecordFragment", "Error navigating to release fragment", e)
            // 显示错误提示
            ToastUtils.showNavigationError(requireContext())
        }
    }

    /**
     * 初始化时间和位置
     * 
     * 该方法负责：
     * 1. 设置当前时间到date TextView
     * 2. 获取当前位置并设置到location TextView
     */
    private fun initializeTimeAndLocation() {
        // 设置当前时间
        setCurrentTime()
        
        // 获取当前位置
        getCurrentLocation()
    }

    /**
     * 设置当前时间
     * 
     * 获取设备当前系统时间并格式化为YYYY.MM.DD格式
     */
    private fun setCurrentTime() {
        try {
            val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
            val currentDate = dateFormat.format(Date())
            binding.date.text = currentDate
            Log.d("RecordFragment", "Current time set: $currentDate")
        } catch (e: Exception) {
            Log.e("RecordFragment", "Error setting current time", e)
            binding.date.text = "2025.01.01" // 默认值
        }
    }

    /**
     * 获取当前位置
     * 
     * 获取设备当前位置信息并格式化为"国家•省份•城市"格式
     */
    private fun getCurrentLocation() {
        if (!hasLocationPermissions()) {
            Log.w("RecordFragment", "Location permissions not granted")
            setDefaultLocation()
            return
        }

        lifecycleScope.launch {
            try {
                val location = getLastKnownLocation()
                if (location != null) {
                    val address = getAddressFromLocation(location.latitude, location.longitude)
                    binding.location.text = address
                    Log.d("RecordFragment", "Location set: $address")
                } else {
                    Log.w("RecordFragment", "Unable to get location")
                    setDefaultLocation()
                }
            } catch (e: Exception) {
                Log.e("RecordFragment", "Error getting location", e)
                setDefaultLocation()
            }
        }
    }

    /**
     * 检查是否有位置权限
     */
    private fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 获取最后已知位置
     */
    private suspend fun getLastKnownLocation(): Location? = withContext(Dispatchers.IO) {
        try {
            val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            // 检查权限
            if (!hasLocationPermissions()) {
                return@withContext null
            }

            // 尝试从GPS获取位置
            val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (gpsLocation != null) {
                return@withContext gpsLocation
            }

            // 尝试从网络获取位置
            val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (networkLocation != null) {
                return@withContext networkLocation
            }

            // 尝试从被动位置提供者获取位置
            val passiveLocation = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            return@withContext passiveLocation
        } catch (e: SecurityException) {
            Log.e("RecordFragment", "Security exception getting location", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e("RecordFragment", "Error getting last known location", e)
            return@withContext null
        }
    }

    /**
     * 根据经纬度获取地址信息
     */
    private suspend fun getAddressFromLocation(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            
            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]
                val country = address.countryName ?: "未知国家"
                val adminArea = address.adminArea ?: "未知省份"
                val locality = address.locality ?: address.subAdminArea ?: "未知城市"
                
                return@withContext "$country•$adminArea•$locality"
            } else {
                Log.w("RecordFragment", "No address found for coordinates: $latitude, $longitude")
                return@withContext "未知位置"
            }
        } catch (e: Exception) {
            Log.e("RecordFragment", "Error getting address from location", e)
            return@withContext "位置解析失败"
        }
    }

    /**
     * 设置默认位置
     */
    private fun setDefaultLocation() {
        binding.location.text = "中国•浙江省•杭州市"
        Log.d("RecordFragment", "Default location set")
    }

    /**
     * Fragment视图销毁时的清理工作
     * 
     * 该方法在Fragment视图被销毁时执行清理：
     * 1. 停止动画效果
     * 2. 如果正在录音，取消录音
     * 3. 清空ViewBinding引用，防止内存泄漏
     */
    override fun onDestroyView() {
        super.onDestroyView()
        isAnimationRunning = false
        if (viewModel.recordingState.value is RecordViewModel.RecordingState.Recording) {
            viewModel.cancelRecording()
        }
        _binding = null
    }
    
    /**
     * 情绪选择完成后导航到发布页面
     * 
     * 该方法由EmotionSelectionDialogFragment调用，
     * 在用户选择情绪并确认后导航到ReleaseFragment
     */
    fun navigateToReleaseAfterEmotionSelection() {
        navigateToReleaseFragment()
    }
}