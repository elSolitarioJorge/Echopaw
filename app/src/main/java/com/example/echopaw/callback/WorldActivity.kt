package com.example.echopaw.callback

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.view.WindowCompat
import com.example.echopaw.R
import com.example.echopaw.utils.StatusBarUtils

/**
 * 3D地球视图Activity
 * 
 * 该Activity提供3D地球的显示和交互功能：
 * 1. 使用OpenGL ES渲染3D地球模型
 * 2. 支持地球的自动旋转和手动控制
 * 3. 提供菜单选项进行功能切换
 * 4. 支持定位到特定地理位置（如中国）
 * 
 * 主要功能：
 * - 3D地球渲染和显示
 * - 自动旋转开关控制
 * - 地理位置定位功能
 * - OpenGL生命周期管理
 */
class WorldActivity : Activity() {
    
    /**
     * 自定义的OpenGL Surface View
     * 
     * 用于渲染3D地球模型的GLSurfaceView实例：
     * - 管理OpenGL渲染上下文
     * - 处理触摸交互事件
     * - 控制地球的旋转和缩放
     */
    private lateinit var glSurfaceView: EarthGLSurfaceView

    /**
     * Activity创建时的生命周期回调
     * 
     * 该方法在Activity创建时执行：
     * 1. 调用父类的onCreate方法
     * 2. 初始化自定义的GLSurfaceView
     * 3. 设置GLSurfaceView为Activity的内容视图
     * 
     * @param savedInstanceState 保存的实例状态，用于恢复Activity状态
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化自定义GLSurfaceView（保持原有逻辑）
        glSurfaceView = EarthGLSurfaceView(this)
        setContentView(glSurfaceView)
        setupStatusBar()
    }

    private fun setupStatusBar() {
        StatusBarUtils.setupImmersiveStatusBarLegacy(this, lightStatusBar = true)
    }

    /**
     * 创建选项菜单
     * 
     * 该方法用于创建Activity的选项菜单：
     * 1. 从菜单资源文件中加载菜单项
     * 2. 提供地球控制相关的菜单选项
     * 3. 返回true表示菜单创建成功
     * 
     * @param menu 要填充的菜单对象
     * @return 如果菜单应该显示则返回true，否则返回false
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_earth, menu)
        return true
    }

    /**
     * 处理选项菜单项点击事件
     * 
     * 该方法处理用户点击菜单项的事件：
     * 1. 根据菜单项ID执行相应的操作
     * 2. 支持切换自动旋转功能
     * 3. 支持定位到中国位置
     * 4. 使用OpenGL线程队列确保线程安全
     * 
     * @param item 被点击的菜单项
     * @return 如果事件被处理则返回true，否则返回false
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            // 原有：切换自动旋转
            R.id.action_toggle_rotation -> {
                glSurfaceView.queueEvent {
                    glSurfaceView.renderer.toggleAutoRotation()
                }
                glSurfaceView.requestRender()
                true
            }
            // 新增：定位到中国
            R.id.action_locate_china -> {
                glSurfaceView.queueEvent {
                    glSurfaceView.renderer.locateToChina()
                }
                glSurfaceView.requestRender()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Activity恢复时的生命周期回调
     * 
     * 该方法在Activity恢复时执行：
     * 1. 调用父类的onResume方法
     * 2. 恢复GLSurfaceView的渲染
     * 3. 确保OpenGL上下文正确恢复
     */
    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }

    /**
     * Activity暂停时的生命周期回调
     * 
     * 该方法在Activity暂停时执行：
     * 1. 调用父类的onPause方法
     * 2. 暂停GLSurfaceView的渲染
     * 3. 释放OpenGL资源以节省电量
     */
    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
    }
}