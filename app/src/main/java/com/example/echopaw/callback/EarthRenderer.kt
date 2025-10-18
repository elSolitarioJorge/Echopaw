package com.example.echopaw.callback

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.echopaw.R
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 3D地球渲染器
 * 
 * 该类实现GLSurfaceView.Renderer接口，负责3D地球的OpenGL渲染：
 * 1. 管理OpenGL渲染状态和资源
 * 2. 处理地球的旋转、缩放和定位
 * 3. 加载和应用地球纹理
 * 4. 支持自动旋转和手动控制
 * 
 * 主要功能：
 * - 3D地球模型渲染
 * - 矩阵变换和投影
 * - 纹理映射和光照
 * - 交互控制和动画
 */
class EarthRenderer(private val context: Context) : GLSurfaceView.Renderer {
    
    /**
     * 投影矩阵
     * 
     * 用于3D到2D的投影变换，定义视锥体
     */
    private val projectionMatrix = FloatArray(16)
    
    /**
     * 视图矩阵
     * 
     * 定义相机的位置和朝向
     */
    private val viewMatrix = FloatArray(16)
    
    /**
     * 模型-视图-投影矩阵
     * 
     * 组合变换矩阵，用于最终的顶点变换
     */
    private val mvpMatrix = FloatArray(16)
    
    /**
     * 旋转矩阵
     * 
     * 控制地球的旋转变换
     */
    private val rotationMatrix = FloatArray(16)
    
    /**
     * 地球球体对象
     * 
     * 包含地球的几何数据和渲染逻辑
     */
    private lateinit var earth: Sphere
    
    /**
     * 地球纹理ID
     * 
     * OpenGL纹理对象的标识符
     */
    private var textureId: Int = 0
    
    /**
     * X轴旋转角度
     * 
     * 控制地球的上下旋转，使用@Volatile确保线程安全
     */
    @Volatile
    private var xRotation: Float = 0f
    
    /**
     * Y轴旋转角度
     * 
     * 控制地球的左右旋转，使用@Volatile确保线程安全
     */
    @Volatile
    private var yRotation: Float = 0f
    
    /**
     * 缩放比例
     * 
     * 控制地球的大小，使用@Volatile确保线程安全
     */
    @Volatile
    private var scale: Float = 1f
    
    /**
     * 自动旋转开关
     * 
     * 控制地球是否自动旋转
     */
    private var autoRotate: Boolean = true
    
    /**
     * 自动旋转速度
     * 
     * 每帧的旋转角度增量
     */
    private val rotationSpeed: Float = 0.3f

    /**
     * OpenGL表面创建时的回调
     * 
     * 该方法在OpenGL上下文创建时调用：
     * 1. 设置透明背景色
     * 2. 启用深度测试
     * 3. 加载地球纹理
     * 4. 创建地球球体对象
     * 5. 设置相机视图矩阵
     * 
     * @param unused 未使用的GL10对象
     * @param config EGL配置对象
     */
    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
//        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // 关键修改：将清屏颜色的Alpha值设为0（透明）
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)  // 最后一个参数0.0表示完全透明

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        textureId = TextureHelper.loadTexture(context, R.drawable.earth_texture)
        earth = Sphere(0.8f, 128, 128, textureId)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 3f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    /**
     * 绘制帧的回调
     * 
     * 该方法在每一帧渲染时调用：
     * 1. 清除颜色和深度缓冲区
     * 2. 更新自动旋转角度
     * 3. 设置旋转矩阵
     * 4. 计算最终的MVP矩阵
     * 5. 应用缩放变换
     * 6. 绘制地球
     * 
     * @param unused 未使用的GL10对象
     */
    override fun onDrawFrame(unused: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (autoRotate) {
            yRotation += rotationSpeed
        }
        Matrix.setIdentityM(rotationMatrix, 0)
        Matrix.rotateM(rotationMatrix, 0, xRotation, 1f, 0f, 0f)
        Matrix.rotateM(rotationMatrix, 0, yRotation, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, rotationMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)
        val scaledMatrix = FloatArray(16)
        Matrix.scaleM(scaledMatrix, 0, mvpMatrix, 0, scale, scale, scale)
        earth.draw(scaledMatrix)
    }

    /**
     * 表面尺寸改变时的回调
     * 
     * 该方法在屏幕尺寸或方向改变时调用：
     * 1. 设置OpenGL视口
     * 2. 计算屏幕宽高比
     * 3. 设置透视投影矩阵
     * 
     * @param unused 未使用的GL10对象
     * @param width 新的宽度
     * @param height 新的高度
     */
    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 2f, 10f)
    }

    /**
     * 添加旋转角度
     * 
     * 该方法用于响应用户的拖拽手势：
     * 1. 累加X和Y轴的旋转角度
     * 2. 限制X轴旋转角度在-90到90度之间
     * 3. Y轴旋转不限制，可以360度旋转
     * 
     * @param deltaX X轴旋转增量
     * @param deltaY Y轴旋转增量
     */
    fun addRotation(deltaX: Float, deltaY: Float) {
        xRotation += deltaX
        yRotation += deltaY
        if (xRotation > 90) xRotation = 90f
        if (xRotation < -90) xRotation = -90f
    }

    /**
     * 设置缩放比例
     * 
     * 该方法用于响应用户的缩放手势：
     * 1. 更新地球的缩放比例
     * 2. 影响下一帧的渲染
     * 
     * @param scale 新的缩放比例
     */
    fun setScale(scale: Float) {
        this.scale = scale
    }

    /**
     * 切换自动旋转状态
     * 
     * 该方法用于开启或关闭地球的自动旋转：
     * 1. 切换自动旋转标志
     * 2. 影响后续帧的旋转更新
     */
    fun toggleAutoRotation() {
        autoRotate = !autoRotate
    }

    /**
     * 定位到中国区域
     * 
     * 该方法将地球旋转到显示中国区域的最佳角度：
     * 1. 设置X轴旋转30度（北半球偏上）
     * 2. 设置Y轴旋转-60度（亚洲东部居中）
     * 3. 设置缩放比例1.5倍（适合查看中国区域）
     */
    fun locateToChina() {
        xRotation = 30f  // 中国区域上下角度（北半球偏上）
        yRotation = -60f // 中国区域左右角度（亚洲东部居中）
        scale = 1.5f     // 适合查看中国区域的缩放比例
    }

    companion object {
        /**
         * 加载着色器
         * 
         * 该静态方法用于编译OpenGL着色器：
         * 1. 创建指定类型的着色器对象
         * 2. 设置着色器源代码
         * 3. 编译着色器
         * 4. 检查编译状态和错误
         * 5. 返回着色器ID或抛出异常
         * 
         * @param type 着色器类型（顶点着色器或片段着色器）
         * @param shaderCode 着色器源代码
         * @return 编译成功的着色器ID
         * @throws RuntimeException 如果编译失败
         */
        fun loadShader(type: Int, shaderCode: String): Int {
            return GLES20.glCreateShader(type).apply {
                GLES20.glShaderSource(this, shaderCode)
                GLES20.glCompileShader(this)
                val compileStatus = IntArray(1)
                GLES20.glGetShaderiv(this, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
                if (compileStatus[0] == 0) {
                    val errorMsg = GLES20.glGetShaderInfoLog(this)
                    GLES20.glDeleteShader(this)
                    throw RuntimeException("着色器编译失败: $errorMsg")
                }
            }
        }
    }
}