package com.example.echopaw.phonograph

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.echopaw.R
import com.example.echopaw.databinding.FragmentPhonograph4Binding
import com.example.echopaw.home.ReleaseSuccessFragment
import com.example.echopaw.utils.ToastUtils

/**
 * 留声机发送页面Fragment
 * 
 * 该Fragment提供录音发送功能，包括：
 * 1. 日历功能入口
 * 2. 邮箱功能入口
 * 3. 发送按钮 - 跳转到发布成功页面
 * 4. 返回按钮功能
 */
class PhonographFragment4 : Fragment() {
    
    /**
     * ViewBinding实例，可为空
     * 使用私有可变属性确保在Fragment销毁时正确清理
     */
    private var _binding: FragmentPhonograph4Binding? = null
    
    /**
     * ViewBinding访问器
     * 提供非空的binding实例访问，简化视图操作
     */
    private val binding get() = _binding!!

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
        _binding = FragmentPhonograph4Binding.inflate(inflater, container, false)
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

        // 设置发送按钮点击事件 - 跳转到发布成功页面
        binding.btnSend.setOnClickListener {
            navigateToReleaseSuccess()
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
    }

    /**
     * 导航到指定Fragment
     * 
     * 该方法使用Fragment事务进行页面跳转：
     * 1. 替换当前Fragment到正确的容器
     * 2. 添加到回退栈以支持返回操作
     * 3. 设置转场动画
     * 
     * @param fragment 目标Fragment实例
     */
    private fun navigateToFragment(fragment: Fragment) {
        try {
            if (isAdded && !parentFragmentManager.isDestroyed) {
                // 根据当前Activity选择正确的容器ID
                val containerId = if (activity is PhonographActivity) {
                    R.id.fragment_container
                } else {
                    R.id.fcv_navigation
                }
                
                parentFragmentManager.beginTransaction()
                    .replace(containerId, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        } catch (e: Exception) {
            android.util.Log.e("PhonographFragment4", "Error navigating to fragment", e)
            ToastUtils.showNavigationError(requireContext())
        }
    }

    /**
     * 导航到发布成功页面
     * 
     * 该方法跳转到ReleaseSuccessFragment：
     * 1. 使用Fragment事务替换当前Fragment
     * 2. 添加到回退栈以支持返回操作
     */
    private fun navigateToReleaseSuccess() {
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
                    .commit()
            }
        } catch (e: Exception) {
            android.util.Log.e("PhonographFragment4", "Error navigating to ReleaseSuccess", e)
            ToastUtils.showNavigationError(requireContext())
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
            android.util.Log.e("PhonographFragment4", "Error in navigateBack", e)
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