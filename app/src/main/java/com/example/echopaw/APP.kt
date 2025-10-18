package com.example.echopaw

import android.app.Application
import android.content.Context
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.amap.api.services.core.ServiceSettings

/**
 * EchoPaw应用程序的Application类
 * 
 * 该类继承自Android的Application类，负责应用程序的全局初始化工作。
 * 主要功能包括：
 * - 初始化高德地图SDK的隐私政策设置
 * - 配置定位、地图和搜索服务的隐私合规
 * 
 * @author EchoPaw Team
 * @since 1.0
 */
class App : Application() {
    
    /**
     * 应用程序创建时的回调方法
     * 
     * 该方法在应用程序启动时被系统调用，用于执行全局初始化操作。
     * 主要完成以下工作：
     * 1. 调用父类的onCreate方法
     * 2. 获取应用程序上下文
     * 3. 配置高德地图SDK的隐私政策合规设置
     * 
     * 隐私政策设置包括：
     * - 定位服务隐私政策的显示和同意状态
     * - 地图服务隐私政策的显示和同意状态  
     * - 搜索服务隐私政策的显示和同意状态
     * 
     * @see Application.onCreate
     */
    override fun onCreate() {
        super.onCreate()
        val mContext: Context = this
        
        // 配置定位服务隐私政策
        // 设置隐私政策已显示和用户已同意的状态
        AMapLocationClient.updatePrivacyShow(mContext, true, true)
        AMapLocationClient.updatePrivacyAgree(mContext, true)
        
        // 配置地图服务隐私政策
        // 设置隐私政策已显示和用户已同意的状态
        MapsInitializer.updatePrivacyShow(mContext, true, true)
        MapsInitializer.updatePrivacyAgree(mContext, true)
        
        // 配置搜索服务隐私政策
        // 设置隐私政策已显示和用户已同意的状态
        ServiceSettings.updatePrivacyShow(mContext, true, true)
        ServiceSettings.updatePrivacyAgree(mContext, true)
    }
}
