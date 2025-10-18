package com.example.echopaw.navigation.contract

import com.example.echopaw.navigation.base.BaseView
import com.example.echopaw.navigation.model.LoadNavigationInfoCallBack
import com.example.echopaw.navigation.model.NavigationInfo

/**
 * 导航模块的契约接口
 * 
 * 该接口定义了导航功能的MVP架构契约，包含：
 * 1. Model层接口 - 负责数据处理和业务逻辑
 * 2. Presenter层接口 - 负责业务逻辑和视图交互
 * 3. View层接口 - 负责UI显示和用户交互
 * 
 * 遵循MVP设计模式，实现各层之间的解耦和职责分离
 */
interface INavigationContract {
    
    /**
     * 导航数据模型接口
     * 
     * 该接口定义了导航数据层的操作规范：
     * 1. 负责获取和处理导航相关数据
     * 2. 通过回调机制返回处理结果
     * 3. 支持泛型以提高代码复用性
     * 
     * @param T 数据类型泛型参数
     */
    interface INavigationModel<T> {
        /**
         * 执行导航数据获取操作
         * 
         * 该方法执行导航信息的获取和处理：
         * 1. 根据传入的数据参数进行相应处理
         * 2. 通过回调接口返回导航信息列表
         * 3. 支持异步操作，避免阻塞主线程
         * 
         * @param data 输入数据，可为空
         * @param callBack 结果回调接口，返回NavigationInfo列表
         */
        fun execute(data: String?, callBack: LoadNavigationInfoCallBack<List<NavigationInfo>>?)
    }

    /**
     * 导航业务逻辑接口
     * 
     * 该接口定义了导航功能的业务逻辑操作：
     * 1. 作为View和Model之间的桥梁
     * 2. 处理用户交互逻辑
     * 3. 协调数据获取和视图更新
     */
    interface INavigationPresenter {
        /**
         * 获取导航信息
         * 
         * 该方法处理导航信息的获取请求：
         * 1. 接收来自View层的请求
         * 2. 调用Model层获取数据
         * 3. 处理获取结果并更新View
         * 
         * @param info 导航信息参数，可为空
         */
        fun getNavigationInfo(info: String?)
    }

    /**
     * 导航视图接口
     * 
     * 该接口继承自BaseView，定义了导航功能的视图操作：
     * 1. 负责显示导航信息
     * 2. 处理错误状态显示
     * 3. 与Presenter进行交互
     */
    interface INavigationView : BaseView<INavigationPresenter?> {
        /**
         * 显示导航信息
         * 
         * 该方法在UI上显示导航信息列表：
         * 1. 接收导航信息数据
         * 2. 更新界面显示
         * 3. 处理空数据情况
         * 
         * @param navigationInfos 导航信息列表，可包含空元素
         */
        fun showNavigationInfomation(navigationInfos: List<NavigationInfo?>?)
        
        /**
         * 显示错误信息
         * 
         * 该方法在发生错误时显示相应的错误提示：
         * 1. 显示用户友好的错误信息
         * 2. 提供重试或其他操作选项
         */
        fun showError()
    }
}
