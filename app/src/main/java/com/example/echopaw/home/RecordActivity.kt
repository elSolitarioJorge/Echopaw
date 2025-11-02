package com.example.echopaw.home


import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.echopaw.R
import com.example.echopaw.utils.StatusBarUtils

/**
 * 录制Activity
 * 
 * 该Activity负责心情录制功能的主要界面：
 * 1. 承载RecordFragment进行录制操作
 * 2. 配置沉浸式状态栏效果
 * 3. 提供全屏录制体验
 * 
 * 使用Fragment容器模式，便于功能模块化和复用
 */
class RecordActivity : AppCompatActivity() {

    /**
     * Activity创建时的初始化
     * 
     * 该方法执行Activity的基本设置：
     * 1. 设置布局文件
     * 2. 首次启动时添加RecordFragment
     * 3. 配置沉浸式状态栏
     * 
     * @param savedInstanceState 保存的实例状态，用于Activity重建时恢复状态
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record)
        
        // 如果第一次进入，添加 RecordFragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container_view, RecordFragment())
                .commit()
        }
        setupStatusBar()
    }
    
    /**
     * 设置沉浸式状态栏
     * 
     * 该方法配置状态栏的显示效果：
     * 1. Android 5.0+：设置透明状态栏颜色
     * 2. Android 6.0+：设置深色状态栏文字（适配浅色背景）
     * 3. 启用全屏布局模式，内容延伸到状态栏区域
     * 
     * 通过版本检查确保兼容性，提供最佳的视觉体验
     */
    private fun setupStatusBar() {
        StatusBarUtils.setupImmersiveStatusBarLegacy(this, lightStatusBar = true)
    }
}
