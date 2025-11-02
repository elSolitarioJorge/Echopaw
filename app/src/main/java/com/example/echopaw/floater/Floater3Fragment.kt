package com.example.echopaw.floater

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.echopaw.utils.ToastUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.echopaw.R
import com.example.echopaw.databinding.FragmentFloater3Binding
import com.example.echopaw.home.ReleaseSuccessFragment
import com.example.echopaw.navigation.MainActivity
import kotlinx.coroutines.launch

/**
 * 留声漂流瓶发送页面Fragment
 * 
 * 该Fragment负责显示漂流瓶发送界面，采用MVVM架构：
 * 1. 使用ViewModel管理发送状态和业务逻辑
 * 2. 通过StateFlow进行响应式UI更新
 * 3. 与RecordFragment保持一致的架构模式
 * 
 * 页面功能：
 * - 显示"系统正在为你寄出漂流瓶..."的状态
 * - 点击发送按钮跳转到发送成功页面
 * - 点击返回按钮返回上一页
 * - 响应式状态管理
 * 
 * @author EchoPaw Team
 * @since 1.0
 */
class Floater3Fragment : Fragment() {
    
    private var _binding: FragmentFloater3Binding? = null
    private val binding get() = _binding!!
    
    // 使用ViewModel进行状态管理
    private val viewModel: FloaterViewModel by viewModels()
    
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
        _binding = FragmentFloater3Binding.inflate(inflater, container, false)
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
                            updateUI(state)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Floater3Fragment", "Error observing ViewModel", e)
        }
    }
    
    /**
     * 根据状态更新UI
     */
    private fun updateUI(state: FloaterViewModel.FloaterUiState) {
        // 根据加载状态更新UI
        if (state.isLoading) {
            // TODO: 显示加载状态
        }
        
        // 处理错误消息
        state.errorMessage?.let { message ->
            // TODO: 显示错误消息（可以使用Toast或Snackbar）
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
        
        // 发送按钮点击事件
        binding.btnSend.setOnClickListener {
            // 调用ViewModel发送录音
            viewModel.sendRecording()
            // 跳转到成功页面
            navigateToSuccess()
        }
    }
    
    /**
     * 返回上一页
     */
    private fun navigateBack() {
        try {
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
        } catch (e: Exception) {
            Log.e("Floater3Fragment", "Error navigating back", e)
            // 安全退出
            activity?.finish()
        }
    }

    /**
     * 跳转到发送成功页面
     */
    private fun navigateToSuccess() {
        try {
            if (isAdded && !parentFragmentManager.isDestroyed) {
                val transaction = parentFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.slide_in_right, R.anim.slide_out_left,
                        R.anim.slide_in_left, R.anim.slide_out_right
                    )
                    .hide(this) // 隐藏当前Fragment
                    .add(R.id.fragment_container, ReleaseSuccessFragment(), "release_success")
                    .addToBackStack("release_success")
                transaction.commit()
            }
        } catch (e: Exception) {
            Log.e("Floater3Fragment", "Error navigating to success", e)
            // 显示错误提示
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