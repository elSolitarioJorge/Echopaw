package com.example.echopaw.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.echopaw.R
import com.example.echopaw.databinding.FragmentHomeBinding
import com.example.echopaw.databinding.FragmentMapBinding
import com.example.echopaw.floater.FloaterFragment
import com.example.echopaw.navigation.MainActivity
import com.example.echopaw.phonograph.PhonographFragment
import me.everything.android.ui.overscroll.OverScrollDecoratorHelper

/**
 * 首页Fragment
 * 
 * 该Fragment作为应用的主页面，提供：
 * 1. 主要功能入口和导航
 * 2. 心情录制功能的快速访问
 * 3. 滚动视图的过度滚动效果
 * 
 * 使用ViewBinding进行视图绑定，确保类型安全和性能优化
 */
class HomeFragment : Fragment() {
    
    /**
     * ViewBinding实例，可为空
     * 使用私有可变属性确保在Fragment销毁时正确清理
     */
    private var _binding: FragmentHomeBinding? = null
    
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
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * 视图创建完成后的初始化
     * 
     * 该方法在视图创建完成后执行初始化操作：
     * 1. 设置滚动视图的过度滚动效果
     * 2. 配置心情录制按钮的点击事件
     * 3. 设置页面转场动画
     * 
     * @param view 创建的视图
     * @param savedInstanceState 保存的实例状态
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 设置滚动视图的过度滚动效果
        val scrollView = view.findViewById<ScrollView>(R.id.sv_home)
        OverScrollDecoratorHelper.setUpOverScroll(scrollView)

        // 设置心情录制按钮点击事件
        binding.ibMood.setOnClickListener {
            startActivity(Intent(requireContext(), RecordActivity::class.java)).also {
                // 添加缩放转场动画效果
                requireActivity().overridePendingTransition(R.anim.zoom_in, R.anim.zoom_out)
            }
        }

        // 设置许愿按钮点击事件 - 改为Activity启动模式
        binding.ibWish.setOnClickListener {
            startActivity(Intent(requireContext(), com.example.echopaw.phonograph.PhonographActivity::class.java)).also {
                // 添加滑入转场动画效果
                requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

        // 设置留声瓶按钮点击事件 - 改为Activity启动模式
        binding.ibVoice.setOnClickListener {
            startActivity(Intent(requireContext(), com.example.echopaw.floater.FloaterActivity::class.java)).also {
                // 添加滑入转场动画效果
                requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            WindowInsetsCompat.CONSUMED
        }
    }

    // 移除了navigateToPhonograph()和navigateToFloater()方法
    // 现在使用Activity启动模式，不再需要Fragment导航

    /**
     * Fragment视图销毁时的清理工作
     * 
     * 该方法在Fragment视图被销毁时执行清理：
     * 1. 调用父类的销毁方法
     * 2. 清空ViewBinding引用，防止内存泄漏
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
