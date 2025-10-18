package com.example.echopaw.callback

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector

/**
 * 自定义的OpenGL Surface View用于显示3D地球
 * 
 * 该类继承自GLSurfaceView，提供3D地球的渲染和交互功能：
 * 1. 配置OpenGL ES 2.0渲染环境
 * 2. 支持透明背景渲染
 * 3. 处理触摸手势（拖拽旋转、缩放）
 * 4. 管理地球渲染器的生命周期
 * 
 * 主要功能：
 * - 3D地球模型的OpenGL渲染
 * - 手势识别和交互处理
 * - 地球旋转和缩放控制
 * - 透明背景支持
 */
class EarthGLSurfaceView(context: Context) : GLSurfaceView(context) {
    
    /**
     * 地球渲染器实例
     * 
     * 负责实际的OpenGL渲染工作：
     * - 渲染3D地球模型
     * - 处理纹理和光照
     * - 管理旋转和缩放状态
     * - 暴露为public供Activity调用
     */
    val renderer: EarthRenderer
    
    /**
     * 手势检测器
     * 
     * 用于检测和处理用户的手势操作：
     * - 检测拖拽手势进行地球旋转
     * - 处理滚动事件
     * - 管理触摸状态
     */
    private val gestureDetector: GestureDetector
    
    /**
     * 缩放手势检测器
     * 
     * 用于检测和处理缩放手势：
     * - 检测双指缩放操作
     * - 计算缩放比例
     * - 控制地球的放大缩小
     */
    private val scaleDetector: ScaleGestureDetector
    
    /**
     * 上一次触摸的X坐标
     * 
     * 用于计算拖拽距离和方向
     */
    private var previousX: Float = 0f
    
    /**
     * 上一次触摸的Y坐标
     * 
     * 用于计算拖拽距离和方向
     */
    private var previousY: Float = 0f
    
    /**
     * 是否正在拖拽状态
     * 
     * 标记当前是否处于拖拽操作中
     */
    private var isDragging: Boolean = false
    
    /**
     * 当前缩放因子
     * 
     * 控制地球的缩放级别，范围在0.5到3.0之间
     */
    private var scaleFactor: Float = 1f

    init {
        // 关键修改1：配置EGL支持透明（必须在setRenderer前设置）
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)  // 8位RGBA + 16位深度缓冲
        setZOrderOnTop(true)  // 将GLSurfaceView置于顶层，确保透明区域显示下层内容
        holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)  // 设置为透明格式

        setEGLContextClientVersion(2)
        renderer = EarthRenderer(context)  // 初始化保持不变
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY

        // 保留原有手势处理逻辑...
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            /**
             * 处理按下事件
             * 
             * @param e 触摸事件
             * @return 返回true表示消费该事件
             */
            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

            /**
             * 处理滚动事件
             * 
             * 当用户拖拽时调用此方法进行地球旋转：
             * 1. 获取当前触摸位置
             * 2. 调用拖拽处理方法
             * 3. 更新地球旋转状态
             * 
             * @param e1 起始触摸事件
             * @param e2 当前触摸事件
             * @param distanceX X轴滚动距离
             * @param distanceY Y轴滚动距离
             * @return 返回true表示消费该事件
             */
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (e1 != null) {
                    handleDrag(e2.x, e2.y)
                }
                return true
            }
        })

        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            /**
             * 处理缩放事件
             * 
             * 当用户进行双指缩放时调用此方法：
             * 1. 计算新的缩放因子
             * 2. 限制缩放范围在0.5到3.0之间
             * 3. 更新渲染器的缩放状态
             * 4. 请求重新渲染
             * 
             * @param detector 缩放手势检测器
             * @return 返回true表示消费该事件
             */
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 3.0f))
                queueEvent {
                    renderer.setScale(scaleFactor)
                }
                requestRender()
                return true
            }
        })

        setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (!scaleDetector.isInProgress) {
                gestureDetector.onTouchEvent(event)
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    previousX = event.x
                    previousY = event.y
                    isDragging = true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                }
            }
            true
        }
    }

    /**
     * 处理拖拽操作
     * 
     * 该方法处理用户的拖拽手势来旋转地球：
     * 1. 计算当前位置与上一次位置的差值
     * 2. 将差值转换为旋转角度
     * 3. 通过OpenGL线程队列更新渲染器
     * 4. 更新上一次触摸位置
     * 5. 请求重新渲染
     * 
     * @param currentX 当前触摸的X坐标
     * @param currentY 当前触摸的Y坐标
     */
    private fun handleDrag(currentX: Float, currentY: Float) {
        val deltaX = currentX - previousX
        val deltaY = currentY - previousY
        queueEvent {
            renderer.addRotation(deltaY * 0.1f, deltaX * 0.1f)
        }
        previousX = currentX
        previousY = currentY
        requestRender()
    }
}