package com.example.echopaw.phonograph

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
import com.example.echopaw.databinding.FragmentPhonograph3Binding
import kotlinx.coroutines.launch

/**
 * 留声机录音页面Fragment
 * 
 * 该Fragment实现录音功能的UI交互，包括：
 * 1. 录音状态切换（待录音 -> 录音中 -> 录音完成）
 * 2. 录音按钮图标和文字的动态变化
 * 3. 录音完成后显示重录和发送选项
 * 4. 平滑的状态切换动画
 */
class PhonographFragment3 : Fragment() {
    
    /**
     * ViewBinding实例，可为空
     * 使用私有可变属性确保在Fragment销毁时正确清理
     */
    private var _binding: FragmentPhonograph3Binding? = null
    
    /**
     * ViewBinding访问器
     * 提供非空的binding实例访问，简化视图操作
     */
    private val binding get() = _binding!!
    
    /**
     * ViewModel实例
     * 使用viewModels()委托自动管理ViewModel生命周期
     */
    private val viewModel: PhonographViewModel by viewModels()
    
    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 301
    }

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
        _binding = FragmentPhonograph3Binding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * 视图创建完成后的初始化
     * 
     * 该方法在视图创建完成后执行初始化操作：
     * 1. 初始化录音状态UI
     * 2. 设置各个按钮的点击事件
     * 3. 设置系统窗口插入处理
     * 
     * @param view 创建的视图
     * @param savedInstanceState 保存的实例状态
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 设置录音按钮点击事件
        binding.btnPlay.setOnClickListener {
            handleRecordingButtonClick()
        }

        // 设置重录按钮点击事件
        binding.ivReset.setOnClickListener {
            resetRecording()
        }

        // 设置发送按钮点击事件
        binding.ivConfirm.setOnClickListener {
            navigateToFragment(PhonographFragment4())
        }

        // 设置返回按钮点击事件
        binding.btnBack.setOnClickListener {
            navigateBack()
        }

        // 设置系统窗口插入处理
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            WindowInsetsCompat.CONSUMED
        }
        
        // 观察ViewModel状态变化
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
            android.util.Log.e("PhonographFragment3", "Error observing ViewModel", e)
        }
    }

    /**
     * 处理录音按钮点击事件
     * 
     * 根据当前状态切换到下一个状态：
     * - 待录音 -> 录音中
     * - 录音中 -> 录音完成
     * - 录音完成状态下不响应点击
     */
    private fun handleRecordingButtonClick() {
        when (viewModel.uiState.value.recordingState) {
            PhonographViewModel.RecordingState.IDLE -> {
                // 检查录音权限
                if (hasRecordAudioPermission()) {
                    viewModel.startRecording()
                } else {
                    requestRecordAudioPermission()
                }
            }
            PhonographViewModel.RecordingState.RECORDING -> {
                viewModel.stopRecording()
            }
            PhonographViewModel.RecordingState.COMPLETED -> {
                // 录音完成状态下不响应点击
            }
            else -> {
                // 其他状态不处理
            }
        }
    }

    /**
     * 重置录音
     * 
     * 重置到初始状态：
     * 1. 恢复到待录音状态
     * 2. 隐藏所有操作按钮和文字
     * 3. 播放重置动画
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
            Log.e("PhonographFragment3", "Error checking record audio permission", e)
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
            Log.e("PhonographFragment3", "Error requesting record audio permission", e)
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
                Log.w("PhonographFragment3", "Record audio permission denied")
                // 可以显示权限被拒绝的提示
            }
        }
    }
    
    /**
     * 根据状态更新UI
     */
    private fun updateUIForState(state: PhonographViewModel.PhonographUiState) {
        when (state.recordingState) {
            PhonographViewModel.RecordingState.IDLE -> {
                // 待录音状态
                binding.btnPlay.setImageResource(R.drawable.ic_microphone)
                binding.tvPlay.visibility = View.GONE
                binding.ivReset.visibility = View.GONE
                binding.ivConfirm.visibility = View.GONE
                binding.tvReset.visibility = View.GONE
                binding.tvSend.visibility = View.GONE
            }
            PhonographViewModel.RecordingState.RECORDING -> {
                // 录音中状态
                binding.btnPlay.setImageResource(R.drawable.ic_microphoneing)
                binding.tvPlay.text = "录音中"
                binding.tvPlay.visibility = View.VISIBLE
                binding.ivReset.visibility = View.GONE
                binding.ivConfirm.visibility = View.GONE
                binding.tvReset.visibility = View.GONE
                binding.tvSend.visibility = View.GONE
            }
            PhonographViewModel.RecordingState.COMPLETED -> {
                // 录音完成状态
                binding.btnPlay.setImageResource(R.drawable.ic_microphoneed)
                binding.tvPlay.text = "结束录音"
                binding.tvPlay.visibility = View.VISIBLE
                binding.ivReset.visibility = View.VISIBLE
                binding.ivConfirm.visibility = View.VISIBLE
                binding.tvReset.visibility = View.VISIBLE
                binding.tvSend.visibility = View.VISIBLE
            }
            else -> {
                // 其他状态保持当前UI
            }
        }
        
        // 处理加载状态
        if (state.isLoading) {
            // TODO: 显示加载状态
        }
        
        // 处理错误消息
        state.errorMessage?.let { message ->
            // TODO: 显示错误消息（可以使用Toast或Snackbar）
        }
    }



    /**
     * 导航到指定Fragment
     * 
     * 该方法使用Fragment事务进行页面跳转：
     * 1. 替换当前Fragment
     * 2. 添加到回退栈以支持返回操作
     * 3. 设置转场动画
     * 
     * @param fragment 目标Fragment实例
     */
    private fun navigateToFragment(fragment: Fragment) {
        try {
            if (isAdded && !parentFragmentManager.isDestroyed) {
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                    )
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        } catch (e: Exception) {Log.e("PhonographFragment3", "Error navigating to fragment", e)
            android.widget.Toast.makeText(context, "导航失败，请重试", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 返回上一页
     * 
     * 该方法处理返回操作：
     * 1. 弹出回退栈中的当前Fragment
     * 2. 显示上一个Fragment
     */
    private fun navigateBack() {
        try {
            if (isAdded && !parentFragmentManager.isDestroyed) {
                parentFragmentManager.popBackStack()
            }
        } catch (e: Exception) {
            android.util.Log.e("PhonographFragment3", "Error in navigateBack", e)
            activity?.finish()
        }
    }

    /**
     * 清理ViewBinding
     * 
     * 在Fragment销毁时清理ViewBinding引用，避免内存泄漏
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}