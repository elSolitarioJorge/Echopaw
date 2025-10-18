package com.example.echopaw.navigation.model

import com.example.echopaw.callback.MapFragment
import com.example.echopaw.home.HomeFragment
import com.example.echopaw.message.MessageFragment
import com.example.echopaw.mine.MineFragment
import com.example.echopaw.navigation.contract.INavigationContract.INavigationModel

/**
 * 导航数据模型实现类
 * 
 * 该类实现了INavigationModel接口，负责：
 * 1. 创建和管理应用的主要导航Fragment
 * 2. 构建导航信息列表
 * 3. 通过回调机制返回导航数据
 * 
 * 包含四个主要功能模块：
 * - 首页：HomeFragment
 * - 回应：MapFragment  
 * - 消息：MessageFragment
 * - 我的：MineFragment
 */
class NavigationModel : INavigationModel<String> {
    
    /**
     * 执行导航信息构建操作
     * 
     * 该方法创建应用的主要导航结构：
     * 1. 创建各个功能模块的Fragment实例
     * 2. 构建NavigationInfo对象并添加到列表
     * 3. 通过回调接口返回完整的导航信息列表
     * 
     * 导航结构包括：
     * - 首页：提供主要功能入口
     * - 回应：地图相关功能
     * - 消息：消息管理功能
     * - 我的：个人信息和设置
     * 
     * @param data 输入数据参数（当前实现中未使用）
     * @param callBack 结果回调接口，用于返回导航信息列表
     */
    override fun execute(data: String?, callBack: LoadNavigationInfoCallBack<List<NavigationInfo>>?) {
        val list = mutableListOf<NavigationInfo>()

        // 创建首页Fragment
        val homeFragment = HomeFragment()
        list.add(NavigationInfo("首页", homeFragment))

        // 创建回应（地图）Fragment
        val callbackFragment = MapFragment()
        list.add(NavigationInfo("回应", callbackFragment))

        // 创建消息Fragment
        val messageFragment = MessageFragment()
        list.add(NavigationInfo("消息", messageFragment))

        // 创建我的Fragment
        val mineFragment = MineFragment()
        list.add(NavigationInfo("我的", mineFragment))

        // 通过回调返回导航信息列表
        callBack?.onSuccess(list)
    }
}
