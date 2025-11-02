package com.example.echopaw.utils

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 状态栏工具类
 * 
 * 提供统一的状态栏配置方法，避免重复代码。
 * 支持现代API和传统API两种实现方式，确保在不同Android版本上的兼容性。
 * 
 * 主要功能：
 * - 设置沉浸式状态栏
 * - 配置状态栏和导航栏透明度
 * - 控制状态栏文字和图标颜色
 * - 兼容Android 6.0以下版本
 * 
 * 使用示例：
 * ```kotlin
 * // 使用默认配置（推荐）
 * StatusBarUtils.setupDefaultStatusBar(this)
 * 
 * // 自定义配置
 * StatusBarUtils.setupImmersiveStatusBar(this, lightStatusBar = true, lightNavigationBar = true)
 * 
 * // 兼容旧版本
 * StatusBarUtils.setupImmersiveStatusBarLegacy(this, lightStatusBar = true)
 * ```
 * 
 * @author EchoPaw Team
 * @since 1.0
 */
object StatusBarUtils {
    
    /**
     * 设置沉浸式状态栏（推荐使用，兼容性更好）
     * 使用现代API实现状态栏配置
     * 
     * @param activity 目标Activity
     * @param lightStatusBar 是否使用浅色状态栏（深色文字）
     * @param lightNavigationBar 是否使用浅色导航栏（深色图标）
     */
    fun setupImmersiveStatusBar(
        activity: Activity,
        lightStatusBar: Boolean = true,
        lightNavigationBar: Boolean = true
    ) {
        val window = activity.window
        
        // 启用边到边显示
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // 设置状态栏和导航栏透明
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        // 获取窗口插入控制器
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        
        // 设置状态栏图标颜色
        windowInsetsController.isAppearanceLightStatusBars = lightStatusBar
        
        // 设置导航栏图标颜色
        windowInsetsController.isAppearanceLightNavigationBars = lightNavigationBar
        
        // 设置系统栏行为
        windowInsetsController.systemBarsBehavior = 
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    
    /**
     * 设置沉浸式状态栏（兼容旧版本）
     * 使用传统API实现状态栏配置，兼容Android 6.0以下版本
     * 
     * @param activity 目标Activity
     * @param lightStatusBar 是否使用浅色状态栏（深色文字）
     */
    @Suppress("DEPRECATION")
    fun setupImmersiveStatusBarLegacy(
        activity: Activity,
        lightStatusBar: Boolean = true
    ) {
        val window = activity.window
        
        // 沉浸式布局：让内容延伸到状态栏区域
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }

        // 设置浅色背景对应的深色文字（仅支持 Android 6.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val decorView = window.decorView
            var flags = decorView.systemUiVisibility
            flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            if (lightStatusBar) {
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            decorView.systemUiVisibility = flags
        } else {
            // 低版本只设置沉浸，不支持文字颜色
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }
    
    /**
     * 简化的状态栏设置方法
     * 适用于大多数场景的快速配置
     * 
     * @param activity 目标Activity
     */
    fun setupDefaultStatusBar(activity: Activity) {
        setupImmersiveStatusBar(activity, lightStatusBar = true, lightNavigationBar = true)
    }
}