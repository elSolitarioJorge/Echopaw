package com.example.echopaw.floater

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.echopaw.R
import com.example.echopaw.utils.StatusBarUtils

/**
 * 留声漂流瓶Activity
 * 
 * 该Activity作为留声漂流瓶功能的容器，采用与RecordActivity相同的活动管理模式：
 * 1. 作为Fragment容器，承载FloaterFragment
 * 2. 配置沉浸式状态栏效果
 * 3. 管理Fragment的生命周期
 * 
 * 架构设计：
 * - Activity作为容器，负责系统级配置
 * - Fragment负责具体的UI逻辑和用户交互
 * - 保持与RecordActivity一致的设计模式
 * 
 * @author EchoPaw Team
 * @since 1.0
 */
class FloaterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_floater)
        
        // 设置沉浸式状态栏
        StatusBarUtils.setupDefaultStatusBar(this)
        
        // 首次启动时添加FloaterFragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FloaterFragment())
                .commit()
        }
    }
}