package com.example.echopaw.home

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Layout
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.AlignmentSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.echopaw.R
import com.example.echopaw.databinding.FragmentRecordBinding
import kotlinx.coroutines.launch

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
     * 录音权限请求码
     * 用于识别录音权限申请的回调结果
     */
    private val REQUEST_RECORD_PERMISSIONS = 101
    
    /**
     * 所需权限数组
     * 包含录音功能必需的权限列表
     */
    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO
    )

    /**
     * 动画运行状态标志
     * 用于控制录音时的气泡动画效果
     */
    private var isAnimationRunning = false

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
        setupViews()
        observeViewModel()

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
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startRecording()
            } else {
                Toast.makeText(
                    context,
                    "需要录音权限才能使用录音功能",
                    Toast.LENGTH_SHORT
                ).show()
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
                binding.waveform.visibility = View.VISIBLE
                binding.recordingTime.visibility = View.VISIBLE
                binding.recordBtn.setImageResource(R.drawable.ic_microphoneing)
                binding.recordingStatus.text = "录音中"
                startBubbleAnimation()
            }
            is RecordViewModel.RecordingState.Idle,
            is RecordViewModel.RecordingState.Completed -> {
                binding.bubble.visibility = View.INVISIBLE
                binding.waveform.visibility = View.INVISIBLE
                binding.recordingTime.visibility = View.INVISIBLE
                binding.recordBtn.setImageResource(R.drawable.ic_microphone)
                binding.recordingStatus.text = "点击录音"
                isAnimationRunning = false
                binding.bubble.clearAnimation()
                if (state is RecordViewModel.RecordingState.Completed) {
                    Toast.makeText(
                        context,
                        "录音已保存: ${state.file.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            is RecordViewModel.RecordingState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                binding.bubble.visibility = View.INVISIBLE
                binding.waveform.visibility = View.INVISIBLE
                binding.recordingTime.visibility = View.INVISIBLE
                binding.recordBtn.setImageResource(R.drawable.ic_microphone)
                binding.recordingStatus.text = "点击录音"
                isAnimationRunning = false
                binding.bubble.clearAnimation()
            }
        }
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
}