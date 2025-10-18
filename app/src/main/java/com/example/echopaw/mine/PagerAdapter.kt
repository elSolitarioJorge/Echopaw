package com.example.echopaw.mine

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * ViewPager2的Fragment状态适配器
 * 
 * 该适配器用于管理ViewPager2中的Fragment页面：
 * 1. 继承FragmentStateAdapter提供Fragment管理
 * 2. 支持Fragment的创建和状态保存
 * 3. 处理页面滑动和Fragment生命周期
 * 4. 提供高效的内存管理和页面复用
 * 
 * 主要功能：
 * - Fragment实例管理
 * - 页面状态保存和恢复
 * - 内存优化和页面回收
 * - 支持动态页面数量
 * 
 * 适配器特性：
 * - 基于FragmentStateAdapter实现
 * - 自动处理Fragment生命周期
 * - 支持页面状态保存
 * - 提供平滑的页面切换体验
 * 
 * 使用场景：
 * - 多标签页面切换
 * - Fragment容器管理
 * - 用户记录分类展示
 * - 个人信息页面导航
 * 
 * @param fragmentActivity 宿主FragmentActivity，用于Fragment管理
 * @param fragmentList Fragment列表，包含要显示的所有Fragment实例，可能为null
 */
class PagerAdapter(fragmentActivity: FragmentActivity, private val fragmentList: List<Fragment>?) :
    FragmentStateAdapter(fragmentActivity) {
    
    /**
     * 创建指定位置的Fragment
     * 
     * 该方法根据位置参数返回对应的Fragment实例：
     * 1. 从fragmentList中获取指定位置的Fragment
     * 2. 由ViewPager2在需要时调用
     * 3. 支持Fragment的懒加载创建
     * 4. 确保每个位置都有对应的Fragment
     * 
     * 调用时机：
     * - ViewPager2首次显示页面时
     * - 用户滑动到新页面时
     * - Fragment被回收后重新创建时
     * 
     * 注意事项：
     * - 假设fragmentList不为null且包含足够的元素
     * - 位置参数由ViewPager2保证在有效范围内
     * - 返回的Fragment将被ViewPager2管理生命周期
     * 
     * @param position 页面位置索引，从0开始
     * @return 指定位置的Fragment实例
     */
    override fun createFragment(position: Int): Fragment {
        return fragmentList!![position]
    }

    /**
     * 获取页面总数
     * 
     * 该方法返回ViewPager2中的页面总数：
     * 1. 检查fragmentList是否为null
     * 2. 返回Fragment列表的大小
     * 3. 用于ViewPager2确定可滑动的页面数量
     * 4. 支持动态页面数量变化
     * 
     * 安全性：
     * - 使用Elvis操作符处理null情况
     * - 避免NullPointerException
     * - 提供默认值0表示无页面
     * 
     * 返回值影响：
     * - ViewPager2的滑动范围
     * - TabLayout的标签数量
     * - 页面指示器的显示
     * 
     * @return Fragment列表的大小，如果列表为null则返回0
     */
    override fun getItemCount(): Int {
        return fragmentList?.size ?: 0
    }
}
