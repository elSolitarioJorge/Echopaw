package com.example.echopaw.floater

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.echopaw.R
import com.example.echopaw.databinding.FragmentFloaterBinding
import com.example.echopaw.navigation.MainActivity
import com.example.echopaw.utils.ToastUtils
import kotlinx.coroutines.launch

/**
 * 留声漂流瓶首页Fragment
 * 
 * 该Fragment负责显示留声漂流瓶功能的首页，采用MVVM架构：
 * 1. 使用ViewModel管理状态和业务逻辑
 * 2. 通过StateFlow进行响应式UI更新
 * 3. 与RecordFragment保持一致的架构模式
 * 
 * 页面功能：
 * - 点击"记录下你的留言"按钮跳转到录音页面
 * - 点击返回按钮返回上一页
 * - 响应式状态管理
 * 
 * @author EchoPaw Team
 * @since 1.0
 */
class FloaterFragment : Fragment() {
    
    private var _binding: FragmentFloaterBinding? = null
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
        _binding = FragmentFloaterBinding.inflate(inflater, container, false)
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
            android.util.Log.e("FloaterFragment", "Error observing ViewModel", e)
        }
    }
    
    /**
     * 根据状态更新UI
     */
    private fun updateUI(state: FloaterViewModel.FloaterUiState) {
        // 根据状态更新UI元素
        // 这里可以根据需要添加具体的UI更新逻辑
        
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
        
        // 开始录音按钮点击事件
        binding.btnStart.setOnClickListener {
            navigateToFloater2()
        }
    }
    
    /**
     * 返回上一页
     */
    private fun navigateBack() {
        try {
            // 如果在FloaterActivity中，直接finish Activity
            if (activity is FloaterActivity) {
                activity?.finish()
            } else {
                // 如果在MainActivity中，返回到主页面
                if (isAdded && !parentFragmentManager.isDestroyed) {
                    parentFragmentManager.beginTransaction()
                        .remove(this)
                        .commit()
                    // 只有在真正返回MainActivity时才显示底部导航栏
                    (activity as? MainActivity)?.showBottomNavigation()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FloaterFragment", "Error in navigateBack", e)
            // 安全退出
            activity?.finish()
        }
    }
    
    /**
     * 跳转到录音页面
     */
    private fun navigateToFloater2() {
        try {
            if (isAdded && !parentFragmentManager.isDestroyed) {
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.slide_in_right, R.anim.slide_out_left,
                        R.anim.slide_in_left, R.anim.slide_out_right
                    )
                    .replace(R.id.fragment_container, Floater2Fragment())
                    .addToBackStack("Floater2Fragment")
                    .commit()
            }
        } catch (e: Exception) {
            android.util.Log.e("FloaterFragment", "Error navigating to Floater2", e)
            // 显示错误提示
            ToastUtils.showPageJumpError(context)
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