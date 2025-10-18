package com.example.echopaw.mine

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.echopaw.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.util.List

/**
 * 我的页面Fragment
 * 
 * 该Fragment用于显示用户个人信息和记录管理界面：
 * 1. 管理多个子页面的切换显示
 * 2. 配置ViewPager2和TabLayout
 * 3. 提供不同类型的记录查看方式
 * 4. 支持标签页导航功能
 * 
 * 主要功能：
 * - 多标签页面管理
 * - ViewPager2滑动切换
 * - TabLayout标签导航
 * - Fragment状态管理
 * - 用户记录分类展示
 * 
 * 标签页类型：
 * - 全部记录：显示所有用户记录
 * - 时间线：按时间顺序展示记录
 * - 地图视图：在地图上显示记录位置
 * - 我的收藏：显示用户收藏的内容
 * - 回应记录：显示用户的回应和互动
 * 
 * UI组件：
 * - ViewPager2：页面滑动容器
 * - TabLayout：标签导航栏
 * - PagerAdapter：页面适配器
 * - TabLayoutMediator：标签与页面的关联器
 */
class MineFragment : Fragment() {
    
    /**
     * 创建Fragment的视图
     * 
     * 该方法负责初始化我的页面界面：
     * 1. 加载fragment_mine布局
     * 2. 创建5个ViewPagerFragment实例
     * 3. 配置ViewPager2和适配器
     * 4. 设置TabLayout和标签文本
     * 5. 建立标签与页面的关联
     * 
     * ViewPager2配置：
     * - 使用PagerAdapter管理Fragment
     * - 支持左右滑动切换页面
     * - 自动处理Fragment生命周期
     * 
     * TabLayout配置：
     * - 提供5个标签页
     * - 每个标签对应不同的功能模块
     * - 支持点击切换和滑动同步
     * 
     * 标签页设置：
     * - 位置0：全部记录
     * - 位置1：时间线
     * - 位置2：地图视图
     * - 位置3：我的收藏
     * - 位置4：回应记录
     * 
     * TabLayoutMediator功能：
     * - 自动同步标签选中状态与页面位置
     * - 处理标签点击事件
     * - 管理标签文本显示
     * - 提供平滑的切换动画
     * 
     * @param inflater 用于加载布局的LayoutInflater
     * @param container 父容器ViewGroup，可能为null
     * @param savedInstanceState 保存的实例状态，可能为null
     * @return 创建的View对象
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_mine, container, false)

        val fragmentList = List.of<Fragment>(
            ViewPagerFragment(),
            ViewPagerFragment(),
            ViewPagerFragment(),
            ViewPagerFragment(),
            ViewPagerFragment()
        )

        val adapter = PagerAdapter(
            requireActivity(),
            fragmentList
        )
        val viewPager2 = view.findViewById<ViewPager2>(R.id.viewPager2)
        viewPager2.adapter = adapter

        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayout)

        TabLayoutMediator(
            tabLayout, viewPager2
        ) { tab: TabLayout.Tab, position: Int ->
            when (position) {
                0 -> tab.setText("全部记录")
                1 -> tab.setText("时间线")
                2 -> tab.setText("地图视图")
                3 -> tab.setText("我的收藏")
                4 -> tab.setText("回应记录")
                else -> tab.setText("未知")
            }
        }.attach()

        return view
    }
}