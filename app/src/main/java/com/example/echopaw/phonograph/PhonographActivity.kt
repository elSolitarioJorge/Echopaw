package com.example.echopaw.phonograph

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.echopaw.R
import com.example.echopaw.utils.LogUtils
import com.example.echopaw.utils.StatusBarUtils

/**
 * 留声机功能的主Activity
 * 
 * 该Activity作为留声机功能的容器，负责：
 * 1. 承载PhonographFragment进行留声机操作
 * 2. 配置沉浸式状态栏效果
 * 3. 管理Fragment的生命周期和异常处理
 * 
 * 架构设计：
 * - Activity作为容器，负责系统级配置
 * - Fragment负责具体的UI逻辑和用户交互
 * - 包含完善的错误处理机制
 * 
 * @author EchoPaw Team
 * @since 1.0
 */
class PhonographActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phonograph)
        
        // 设置沉浸式状态栏
        StatusBarUtils.setupDefaultStatusBar(this)
        
        // 首次启动时添加PhonographFragment
        if (savedInstanceState == null) {
            try {
                // 检查Fragment容器是否存在
                val containerView = findViewById<View>(R.id.fragment_container)
                if (containerView != null && !isDestroyed && !supportFragmentManager.isDestroyed) {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, PhonographFragment())
                        .commit()
                } else {
                    LogUtils.Navigation.navigationError("PhonographActivity", "Fragment container not found or activity destroyed")
                    finish()
                }
            } catch (e: Exception) {
                LogUtils.Navigation.navigationError("PhonographActivity", "Error adding PhonographFragment: ${e.message}", e)
                finish()
            }
        }
    }
}