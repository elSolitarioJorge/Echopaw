package com.example.echopaw.phonograph

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
import com.example.echopaw.databinding.FragmentPhonographBinding
import com.example.echopaw.navigation.MainActivity
import com.example.echopaw.utils.ToastUtils
import kotlinx.coroutines.launch

/**
 * 留声机主页面Fragment
 * 
 * 该Fragment作为留声机功能的主入口，提供：
 * 1. 日历功能入口 - 跳转到PhonographFragment2
 * 2. 邮箱功能入口 - 跳转到PhonographFragment5
 * 3. 留音功能入口 - 跳转到PhonographFragment3
 * 4. 返回按钮功能
 */
class PhonographFragment : Fragment() {
    
    /**
     * ViewBinding实例，可为空
     * 使用私有可变属性确保在Fragment销毁时正确清理
     */
    private var _binding: FragmentPhonographBinding? = null
    
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
        _binding = FragmentPhonographBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * 视图创建完成后的初始化
     * 
     * 该方法在视图创建完成后执行初始化操作：
     * 1. 设置各个按钮的点击事件
     * 2. 配置页面转场动画
     * 3. 设置系统窗口插入处理
     * 
     * @param view 创建的视图
     * @param savedInstanceState 保存的实例状态
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 设置日历按钮点击事件 - 跳转到PhonographFragment2
        binding.btnCalender.setOnClickListener {
            navigateToFragment(PhonographFragment2())
        }

        // 设置邮箱按钮点击事件 - 跳转到PhonographFragment5
        binding.btnPostbox.setOnClickListener {
            navigateToFragment(PhonographFragment5())
        }

        // 设置留音按钮点击事件 - 跳转到PhonographFragment3
        binding.btnLiuYin.setOnClickListener {
            navigateToFragment(PhonographFragment3())
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
                            updateUI(state)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PhonographFragment", "Error observing ViewModel", e)
        }
    }
    
    /**
     * 根据状态更新UI
     */
    private fun updateUI(state: PhonographViewModel.PhonographUiState) {
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
        } catch (e: Exception) {
            android.util.Log.e("PhonographFragment", "Error navigating to fragment", e)
            ToastUtils.showNavigationError(context)
        }
    }

    /**
     * 返回上一页
     * 
     * 该方法处理返回操作：
     * 1. 如果在PhonographActivity中，直接finish Activity
     * 2. 如果在MainActivity中，返回到主页面
     */
    private fun navigateBack() {
        try {
            if (isAdded && !parentFragmentManager.isDestroyed) {
                if (parentFragmentManager.backStackEntryCount > 0) {
                    parentFragmentManager.popBackStack()
                } else {
                    if (activity is PhonographActivity) {
                        // 如果在PhonographActivity中，直接finish Activity
                        activity?.finish()
                    } else {
                        // 如果在MainActivity中，返回到主页面
                        parentFragmentManager.beginTransaction()
                            .remove(this)
                            .commit()
                        // 只有在真正返回MainActivity时才显示底部导航栏
                        try {
                            if (isAdded && !isDetached && activity != null) {
                                (activity as? MainActivity)?.showBottomNavigation()
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("PhonographFragment", "Error showing bottom navigation", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PhonographFragment", "Error in navigateBack", e)
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
        // 不在这里自动显示底部导航栏，避免在Fragment切换时意外显示
        // 底部导航栏的显示应该由具体的业务逻辑控制
        _binding = null
    }
}