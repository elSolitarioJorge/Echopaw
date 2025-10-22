package com.example.echopaw.home


import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.echopaw.R

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
    /*private fun setupStatusBar() {
        // Android 5.0（API 21）及以上支持设置状态栏颜色
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.transparent)
            // Android 6.0（API 23）及以上支持设置状态栏文字颜色（深色/浅色）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // 如果状态栏背景是浅色（如透明），建议将文字设置为深色，避免看不清
                // 反之，若背景是深色，可设置为浅色文字（View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR 不设置即可）
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
        // 设置全屏布局，内容延伸到状态栏区域
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }*/
    private fun setupStatusBar() {
        // 沉浸式布局：让内容延伸到状态栏区域
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT // 底部系统导航栏背景透明
        }

        // 设置浅色背景对应的深色文字（仅支持 Android 6.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val decorView = window.decorView
            var flags = decorView.systemUiVisibility
            flags =
                flags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR // 深色文字
            decorView.systemUiVisibility = flags
        } else {
            // 低版本只设置沉浸，不支持文字颜色
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }
}
