package com.example.echopaw.phonograph

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.echopaw.R
import com.example.echopaw.databinding.FragmentPhonograph5Binding

/**
 * 留声机邮箱页面Fragment
 * 
 * 该Fragment提供声音信箱功能，包括：
 * 1. 显示声音信箱内容
 * 2. 返回按钮功能
 */
class PhonographFragment5 : Fragment() {
    
    /**
     * ViewBinding实例，可为空
     * 使用私有可变属性确保在Fragment销毁时正确清理
     */
    private var _binding: FragmentPhonograph5Binding? = null
    
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
        _binding = FragmentPhonograph5Binding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * 视图创建完成后的初始化
     * 
     * 该方法在视图创建完成后执行初始化操作：
     * 1. 设置返回按钮点击事件
     * 2. 设置系统窗口插入处理
     * 
     * @param view 创建的视图
     * @param savedInstanceState 保存的实例状态
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
            android.util.Log.e("PhonographFragment5", "Error in navigateBack", e)
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