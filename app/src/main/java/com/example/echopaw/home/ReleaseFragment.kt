package com.example.echopaw.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.widget.Toast
import com.example.echopaw.utils.ToastUtils
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Observer
import com.example.echopaw.R
import com.example.echopaw.databinding.PageReleaseBinding
import com.example.echopaw.navigation.MainActivity
import com.example.echopaw.network.AuthManager
import com.example.echopaw.service.LocationService
import com.example.echopaw.utils.NetworkDiagnostic
import kotlinx.coroutines.launch

/**
 * 发布页面Fragment，对应布局page_release.xml
 * - 必须勾选cb_agree方可激活btn_release
 * - 点击btn_release后获取录制的音频文件和GPS位置，上传到服务器
 * - 上传成功后跳转到发布成功页
 * - 使用Activity作用域的ReleaseFlowViewModel传递选中状态和上传状态
 */
class ReleaseFragment : Fragment() {

    private var _binding: PageReleaseBinding? = null
    private val binding get() = _binding!!

    private val flowViewModel: ReleaseFlowViewModel by activityViewModels()
    private val recordViewModel: RecordViewModel by viewModels()
    
    private lateinit var locationService: LocationService
    
    private val REQUEST_LOCATION_PERMISSIONS = 102
    private val requiredLocationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PageReleaseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 初始化LocationService
        locationService = LocationService(requireContext())
        
        // 初始化AuthManager
        AuthManager.init(requireContext())
        
        // 初始化按钮状态
        val currentChecked = flowViewModel.agreeChecked.value ?: false
        updateReleaseButtonState(currentChecked)
        binding.cbAgree.isChecked = currentChecked

        // 监听复选框改变
        binding.cbAgree.setOnCheckedChangeListener { _, isChecked ->
            flowViewModel.setAgreeChecked(isChecked)
            updateReleaseButtonState(isChecked)
        }

        // 文本"用户协议"加粗并添加下划线（仅最后四个字符）
        styleAgreementText()

        // 可见性图片切换：在 img_disclose 和 img_private 间平滑过渡
        setupVisibilityToggle()

        // 检查录音文件状态
        checkAudioFileStatus()

        // 观察ViewModel状态
        observeViewModels()

        // 发布按钮点击
        binding.btnRelease.setOnClickListener {
            if (binding.btnRelease.isEnabled) {
                handleReleaseClick()
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun styleAgreementText() {
        val original = binding.tvAgreeText.text?.toString() ?: return
        val keyword = "用户协议"
        val start = original.lastIndexOf(keyword)
        val spannable = SpannableString(original)
        if (start >= 0) {
            val end = start + keyword.length
            spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(UnderlineSpan(), start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else if (original.length >= 4) {
            val start2 = original.length - 4
            val end2 = original.length
            spannable.setSpan(StyleSpan(Typeface.BOLD), start2, end2, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(UnderlineSpan(), start2, end2, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.tvAgreeText.text = spannable
    }

    private var isPrivateVisible = false

    private fun setupVisibilityToggle() {
        // 默认显示 img_disclose（布局已设置），点击切换到 img_private，再次点击切回
        binding.btnVisible.setOnClickListener {
            // 淡出 -> 切换资源 -> 淡入，保持布局和尺寸不变
            binding.btnVisible.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction {
                    val target = if (isPrivateVisible) R.drawable.img_disclose else R.drawable.img_private
                    binding.btnVisible.setImageResource(target)
                    binding.btnVisible.animate().alpha(1f).setDuration(150).start()
                    isPrivateVisible = !isPrivateVisible
                }
                .start()
        }
    }

    private fun updateReleaseButtonState(enabled: Boolean) {
        binding.btnRelease.isEnabled = enabled
        binding.btnRelease.isClickable = enabled
        binding.btnRelease.alpha = if (enabled) 1.0f else 0.5f
    }

    /**
     * 检查录音文件状态
     * 
     * 该方法在页面加载时检查是否有有效的录音文件：
     * 1. 如果没有录音文件，显示提示信息
     * 2. 如果文件无效，清除引用并显示提示
     */
    private fun checkAudioFileStatus() {
        val audioFile = flowViewModel.audioFile.value
        if (audioFile == null) {
            ToastUtils.Recording.showRecordFirst(context)
        } else if (!audioFile.exists() || audioFile.length() < 1024) {
            ToastUtils.Recording.showInvalidRecording(context)
            flowViewModel.setAudioFile(null)
        }
    }

    /**
     * 观察ViewModel状态变化
     */
    private fun observeViewModels() {
        // 观察录制状态，获取最新录制的音频文件
        lifecycleScope.launch {
            recordViewModel.recordingState.collect { state ->
                if (state is RecordViewModel.RecordingState.Completed) {
                    flowViewModel.setAudioFile(state.file)
                }
            }
        }

        // 观察情绪状态变化
        flowViewModel.emotionType.observe(viewLifecycleOwner, Observer { emotionType ->
            emotionType?.let {
                // 更新情绪图标
                binding.ivEmotion.setImageResource(it.iconRes)
                Log.d("ReleaseFragment", "更新情绪图标: ${it.displayName}")
            }
        })

        // 观察上传状态
        flowViewModel.uploadState.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is ReleaseFlowViewModel.UploadState.Idle -> {
                    // 空闲状态，不做处理
                }
                is ReleaseFlowViewModel.UploadState.Uploading -> {
                    // 显示上传中状态
                    binding.btnRelease.isEnabled = false
                    binding.btnRelease.alpha = 0.5f
                    ToastUtils.showInfo(requireContext(), "正在上传音频...")
                }
                is ReleaseFlowViewModel.UploadState.Success -> {
                    // 上传成功，跳转到成功页面
                    Log.d("ReleaseFragment", "Upload success, navigating to ReleaseSuccessFragment")
                    ToastUtils.showSuccess(requireContext(), "音频上传成功！")
                    
                    // 隐藏底部导航栏
                    (requireActivity() as? MainActivity)?.hideBottomNavigation()
                    
                    if (isAdded && !parentFragmentManager.isDestroyed) {
                        val transaction = parentFragmentManager.beginTransaction()
                            .setCustomAnimations(
                                R.anim.slide_in_right, R.anim.slide_out_left,
                                R.anim.slide_in_left, R.anim.slide_out_right
                            )
                            .hide(this) // 隐藏当前Fragment
                            .add(R.id.fragment_container_view, ReleaseSuccessFragment(), "release_success")
                            .addToBackStack("release_success")
                        
                        Log.d("ReleaseFragment", "Fragment transaction created, committing...")
                        transaction.commit()
                        Log.d("ReleaseFragment", "Fragment transaction committed")
                    }
                    
                    flowViewModel.resetUploadState()
                }
                is ReleaseFlowViewModel.UploadState.Error -> {
                    // 上传失败，显示详细错误信息
                    binding.btnRelease.isEnabled = true
                    binding.btnRelease.alpha = 1.0f
                    
                    // 显示错误信息
                    ToastUtils.showError(requireContext(), state.message)
                    Log.e("ReleaseFragment", "Upload failed: ${state.message}")
                    
                    // 如果是网络相关错误，提供网络诊断选项
                    if (isNetworkRelatedError(state.message)) {
                        showNetworkDiagnosticDialog()
                    }
                    
                    flowViewModel.resetUploadState()
                }
            }
        })
    }

    /**
     * 处理发布按钮点击
     */
    private fun handleReleaseClick() {
        // 检查是否有录制的音频文件
        val audioFile = flowViewModel.audioFile.value
        if (audioFile == null) {
            ToastUtils.showWarning(requireContext(), "请先录制音频")
            return
        }

        // 检查文件是否存在
        if (!audioFile.exists()) {
            ToastUtils.showWarning(requireContext(), "录音文件不存在，请重新录制")
            // 清除无效的文件引用
            flowViewModel.setAudioFile(null)
            return
        }

        // 检查文件大小是否有效（至少1KB）
        if (audioFile.length() < 1024) {
            ToastUtils.showWarning(requireContext(), "录音文件无效，请重新录制")
            // 清除无效的文件引用
            flowViewModel.setAudioFile(null)
            return
        }

        // 检查文件是否可读
        if (!audioFile.canRead()) {
            ToastUtils.showWarning(requireContext(), "无法读取录音文件，请重新录制")
            return
        }

        // 检查位置权限
        if (!hasLocationPermissions()) {
            requestPermissions(requiredLocationPermissions, REQUEST_LOCATION_PERMISSIONS)
            return
        }

        // 获取当前位置并上传
        getCurrentLocationAndUpload()
    }

    /**
     * 检查是否有位置权限
     */
    private fun hasLocationPermissions(): Boolean {
        return requiredLocationPermissions.all { permission ->
            ActivityCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 获取当前位置并上传音频
     */
    private fun getCurrentLocationAndUpload() {
        lifecycleScope.launch {
            try {
                ToastUtils.showInfo(requireContext(), "正在获取位置信息...")
                val location = locationService.getCurrentLocation()
                
                if (location.errorCode == 0) {
                    flowViewModel.setCurrentLocation(location)
                    flowViewModel.uploadAudio()
                } else {
                    // 根据错误码提供更具体的错误信息
                    val errorMessage = when (location.errorCode) {
                        12 -> "定位权限被拒绝，请在设置中开启位置权限"
                        13 -> "网络异常，请检查网络连接后重试"
                        14 -> "GPS定位失败，请确保GPS已开启"
                        else -> "获取位置失败: ${location.errorInfo}"
                    }
                    ToastUtils.showError(requireContext(), errorMessage)
                    Log.e("ReleaseFragment", "Location error: code=${location.errorCode}, info=${location.errorInfo}")
                }
            } catch (e: Exception) {
                val errorMessage = "获取位置异常: ${e.message ?: "未知错误"}，请重试"
                ToastUtils.showError(requireContext(), errorMessage)
                Log.e("ReleaseFragment", "Location exception", e)
            }
        }
    }

    /**
     * 权限请求结果处理
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            REQUEST_LOCATION_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // 权限已授予，重新尝试上传
                    getCurrentLocationAndUpload()
                } else {
                    // 检查是否有权限被永久拒绝
                    val deniedPermissions = permissions.filterIndexed { index, _ ->
                        grantResults[index] != PackageManager.PERMISSION_GRANTED
                    }
                    
                    val shouldShowRationale = deniedPermissions.any { permission ->
                        shouldShowRequestPermissionRationale(permission)
                    }
                    
                    val message = if (shouldShowRationale) {
                        "需要位置权限才能发布音频，请授予位置权限后重试"
                    } else {
                        "位置权限被拒绝，请在设置中手动开启位置权限"
                    }
                    
                    ToastUtils.showWarning(requireContext(), message)
                    Log.w("ReleaseFragment", "Location permissions denied: ${deniedPermissions.joinToString()}")
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        locationService.destroy()
        _binding = null
    }
    
    /**
     * 判断是否为网络相关错误
     */
    private fun isNetworkRelatedError(message: String): Boolean {
        val networkKeywords = listOf(
            "网络", "连接", "超时", "服务器", "维护", "不可用", 
            "network", "connection", "timeout", "server", "maintenance"
        )
        return networkKeywords.any { keyword ->
            message.contains(keyword, ignoreCase = true)
        }
    }
    
    /**
     * 显示网络诊断对话框
     */
    private fun showNetworkDiagnosticDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("网络诊断")
            .setMessage("检测到网络连接问题，是否进行网络诊断？")
            .setPositiveButton("诊断") { _, _ ->
                performNetworkDiagnosis()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 执行网络诊断
     */
    private fun performNetworkDiagnosis() {
        lifecycleScope.launch {
            try {
                // 显示诊断进度
                val progressDialog = AlertDialog.Builder(requireContext())
                    .setTitle("网络诊断")
                    .setMessage("正在检测网络状态...")
                    .setCancelable(false)
                    .create()
                progressDialog.show()
                
                // 执行诊断
                val diagnosis = NetworkDiagnostic.performFullDiagnosis(requireContext())
                
                progressDialog.dismiss()
                
                // 显示诊断结果
                AlertDialog.Builder(requireContext())
                    .setTitle("网络诊断结果")
                    .setMessage(diagnosis.generateReport())
                    .setPositiveButton("确定", null)
                    .setNeutralButton("重试上传") { _, _ ->
                        // 重新尝试上传
                        handleReleaseClick()
                    }
                    .show()
                    
            } catch (e: Exception) {
                Log.e("ReleaseFragment", "Network diagnosis failed", e)
                ToastUtils.showError(requireContext(), "网络诊断失败: ${e.message}")
            }
        }
    }
}