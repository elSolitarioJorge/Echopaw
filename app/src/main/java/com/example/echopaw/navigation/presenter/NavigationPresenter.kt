package com.example.echopaw.navigation.presenter

import com.example.echopaw.navigation.contract.INavigationContract.INavigationModel
import com.example.echopaw.navigation.contract.INavigationContract.INavigationPresenter
import com.example.echopaw.navigation.contract.INavigationContract.INavigationView
import com.example.echopaw.navigation.model.LoadNavigationInfoCallBack
import com.example.echopaw.navigation.model.NavigationInfo

/**
 * 导航业务逻辑处理器
 * 
 * 该类实现了MVP架构中的Presenter层，负责：
 * 1. 协调Model和View之间的交互
 * 2. 处理导航信息的获取和展示逻辑
 * 3. 实现加载过程的回调处理
 * 
 * 作为Model和View的桥梁，处理业务逻辑并管理数据流向
 * 
 * @param model 导航数据模型接口实例
 * @param view 导航视图接口实例
 */
class NavigationPresenter(
    private val model: INavigationModel<String>,
    private val view: INavigationView
) : INavigationPresenter,
    LoadNavigationInfoCallBack<List<NavigationInfo>> {

    /**
     * 获取导航信息
     * 
     * 该方法处理导航信息的获取请求：
     * 1. 接收来自View层的请求参数
     * 2. 对空值参数进行默认值处理
     * 3. 调用Model层执行数据获取操作
     * 4. 将当前实例作为回调传递给Model
     * 
     * @param info 导航信息参数，可为空，空值时使用空字符串作为默认值
     */
    override fun getNavigationInfo(info: String?) {
        // 如果 info 可能为 null，可以在这里做默认值处理
        model.execute(info ?: "", this)
    }

    /**
     * 数据加载成功回调
     * 
     * 该方法在导航信息加载成功时被调用：
     * 1. 接收Model层返回的导航信息列表
     * 2. 将数据传递给View层进行显示
     * 3. 完成数据从Model到View的传递
     * 
     * @param navigationInfos 加载成功的导航信息列表
     */
    override fun onSuccess(navigationInfos: List<NavigationInfo>) {
        view.showNavigationInfomation(navigationInfos)
    }

    /**
     * 数据加载开始回调
     * 
     * 该方法在导航信息开始加载时被调用：
     * 1. 可用于显示加载指示器
     * 2. 执行加载前的UI准备工作
     * 3. 当前实现为空，可根据需要扩展
     */
    override fun onStart() {
        // 可选：在开始加载时做 UI 提示
    }

    /**
     * 数据加载失败回调
     * 
     * 该方法在导航信息加载失败时被调用：
     * 1. 可用于显示错误信息
     * 2. 执行失败时的UI处理逻辑
     * 3. 当前实现为空，可根据需要扩展
     */
    override fun onFailed() {
        // 可选：失败时的 UI 处理
        view.showError()
    }

    /**
     * 数据加载完成回调
     * 
     * 该方法在导航信息加载完成时被调用（无论成功或失败）：
     * 1. 可用于隐藏加载指示器
     * 2. 执行清理工作
     * 3. 当前实现为空，可根据需要扩展
     */
    override fun onFinish() {
        // 可选：结束时的收尾操作
    }
}
