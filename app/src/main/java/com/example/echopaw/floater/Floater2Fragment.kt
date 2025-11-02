package com.example.echopaw.floater

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.echopaw.R
import com.example.echopaw.databinding.FragmentFloater2Binding
import com.example.echopaw.navigation.MainActivity
import com.example.echopaw.utils.ToastUtils
import kotlinx.coroutines.launch

/**
 * 留声漂流瓶录音页面Fragment
 * 
 * 该Fragment负责录音功能的交互，采用MVVM架构：
 * 1. 使用ViewModel管理录音状态和业务逻辑
 * 2. 通过StateFlow进行响应式UI更新
 * 3. 与RecordFragment保持一致的架构模式
 * 
 * 录音状态：
 * - 初始状态：显示麦克风图标，底部显示"点击录音"
 * - 录音中：显示录音中图标，底部显示"录音中"
 * - 录音完成：显示录音完成图标，底部显示"结束录音"，显示重录和发送按钮
 * 
 * @author EchoPaw Team
 * @since 1.0
 */
class Floater2Fragment : Fragment() {
    
    private var _binding: FragmentFloater2Binding? = null
    private val binding get() = _binding!!
    
    // 使用ViewModel进行状态管理
    private val viewModel: FloaterViewModel by viewModels()
    
    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 201
    }
    
    /**
     * 创建Fragment的视图
     * 
     * @param inflater 用于加载布局的LayoutInflater
     * @param container 父容器ViewGroup
     * @param savedInstanceState 保存的实例状态
     * @return 创建的View
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFloater2Binding.inflate(inflater, container, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        return binding.root
    }
    
    /**
     * 视图创建完成后的初始化
     * 
     * @param view 创建的视图
     * @param savedInstanceState 保存的实例状态
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 隐藏底部导航栏（仅在MainActivity中使用时）
        (activity as? MainActivity)?.hideBottomNavigation()
        
        setupClickListeners()
        observeViewModel()
    }
    
    /**
     * 观察ViewModel状态变化
     */
    private fun observeViewModel() {
        try {
            if (isAdded && !isDetached && view != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.uiState.collect { state ->
                        if (isAdded && !isDetached && view != null) {
                            updateUIForState(state)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Floater2Fragment", "Error observing ViewModel", e)
        }
    }
    
    /**
     * 设置点击事件监听器
     */
    private fun setupClickListeners() {
        // 返回按钮点击事件
        binding.btnBack.setOnClickListener {
            navigateBack()
        }
        
        // 录音按钮点击事件
        binding.btnPlay.setOnClickListener {
            handleRecordingButtonClick()
        }
        
        // 重录按钮点击事件
        binding.ivReset.setOnClickListener {
            resetRecording()
        }
        
        // 发送按钮点击事件
        binding.ivConfirm.setOnClickListener {
            navigateToFloater3()
        }
    }
    
    /**
     * 处理录音按钮点击事件
     */
    private fun handleRecordingButtonClick() {
        when (viewModel.uiState.value.recordingState) {
            FloaterViewModel.RecordingState.READY -> {
                // 检查录音权限
                if (hasRecordAudioPermission()) {
                    viewModel.startRecording()
                } else {
                    requestRecordAudioPermission()
                }
            }
            FloaterViewModel.RecordingState.RECORDING -> {
                // 停止录音
                viewModel.stopRecording()
            }
            FloaterViewModel.RecordingState.FINISHED -> {
                // 录音已完成，可以重新开始
                resetRecording()
            }
            FloaterViewModel.RecordingState.IDLE -> {
                // 空闲状态，重置为准备状态
                viewModel.resetRecording()
            }
        }
    }
    
    /**
     * 重置录音状态
     */
    private fun resetRecording() {
        viewModel.resetRecording()
    }

    /**
     * 检查是否有录音权限
     */
    private fun hasRecordAudioPermission(): Boolean {
        return try {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e("Floater2Fragment", "Error checking record audio permission", e)
            false
        }
    }

    /**
     * 请求录音权限
     */
    private fun requestRecordAudioPermission() {
        try {
            if (isAdded && !isDetached) {
                requestPermissions(
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_RECORD_AUDIO_PERMISSION
                )
            }
        } catch (e: Exception) {
            Log.e("Floater2Fragment", "Error requesting record audio permission", e)
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
        
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予，开始录音
                viewModel.startRecording()
            } else {
                Log.w("Floater2Fragment", "Record audio permission denied")
                // 可以显示权限被拒绝的提示
            }
        }
    }
    
    /**
     * 根据状态更新UI
     * 
     * @param state 当前UI状态
     */
    private fun updateUIForState(state: FloaterViewModel.FloaterUiState) {
        when (state.recordingState) {
            FloaterViewModel.RecordingState.READY -> {
                // 准备录音状态
                binding.btnPlay.setImageResource(R.drawable.ic_microphone)
                binding.textView8.text = "点击录音"
                
                // 隐藏重录和发送按钮
                binding.ivReset.visibility = View.INVISIBLE
                binding.ivConfirm.visibility = View.INVISIBLE
                binding.tvReset.visibility = View.GONE
                binding.tvSend.visibility = View.GONE
            }
            FloaterViewModel.RecordingState.RECORDING -> {
                // 录音中状态
                binding.btnPlay.setImageResource(R.drawable.ic_microphoneing)
                binding.textView8.text = "录音中"
                
                // 隐藏重录和发送按钮
                binding.ivReset.visibility = View.INVISIBLE
                binding.ivConfirm.visibility = View.INVISIBLE
                binding.tvReset.visibility = View.GONE
                binding.tvSend.visibility = View.GONE
            }
            FloaterViewModel.RecordingState.FINISHED -> {
                // 录音完成状态
                binding.btnPlay.setImageResource(R.drawable.ic_microphoneed)
                binding.textView8.text = "结束录音"
                
                // 显示重录和发送按钮
                binding.ivReset.visibility = View.VISIBLE
                binding.ivConfirm.visibility = View.VISIBLE
                binding.tvReset.visibility = View.VISIBLE
                binding.tvSend.visibility = View.VISIBLE
            }
            FloaterViewModel.RecordingState.IDLE -> {
                // 空闲状态，与READY状态相同的UI
                binding.btnPlay.setImageResource(R.drawable.ic_microphone)
                binding.textView8.text = "点击录音"
                
                // 隐藏重录和发送按钮
                binding.ivReset.visibility = View.INVISIBLE
                binding.ivConfirm.visibility = View.INVISIBLE
                binding.tvReset.visibility = View.GONE
                binding.tvSend.visibility = View.GONE
            }
        }
        
        // 处理错误消息
        state.errorMessage?.let { message ->
            // TODO: 显示错误消息（可以使用Toast或Snackbar）
        }
    }
    
    /**
     * 返回上一页
     */
    private fun navigateBack() {
        try {
            if (isAdded && !parentFragmentManager.isDestroyed) {
                if (parentFragmentManager.backStackEntryCount > 0) {
                    parentFragmentManager.popBackStack()
                } else {
                    // 如果在FloaterActivity中，直接finish Activity
                    if (activity is FloaterActivity) {
                        activity?.finish()
                    } else {
                        // 如果在MainActivity中，返回到主页面
                        (activity as? MainActivity)?.showBottomNavigation()
                        if (isAdded && !parentFragmentManager.isDestroyed) {
                            parentFragmentManager.beginTransaction()
                                .remove(this)
                                .commit()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Floater2Fragment", "Error in navigateBack", e)
            activity?.finish()
        }
    }
    
    /**
     * 跳转到发送页面
     */
    private fun navigateToFloater3() {
        try {
            if (isAdded && !parentFragmentManager.isDestroyed) {
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.slide_in_right, R.anim.slide_out_left,
                        R.anim.slide_in_left, R.anim.slide_out_right
                    )
                    .replace(R.id.fragment_container, Floater3Fragment())
                    .addToBackStack("Floater3Fragment")
                    .commit()
            }
        } catch (e: Exception) {
            Log.e("Floater2Fragment", "Error navigating to Floater3", e)
            ToastUtils.showNavigationError(requireContext())
        }
    }
    
    /**
     * 清理资源
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}