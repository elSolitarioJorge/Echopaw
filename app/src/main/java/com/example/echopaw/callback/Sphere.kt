package com.example.echopaw.callback

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * 3D球体几何体类
 * 
 * 该类用于生成和渲染3D球体模型：
 * 1. 生成球体的顶点和纹理坐标数据
 * 2. 管理OpenGL着色器程序
 * 3. 处理纹理映射和渲染
 * 4. 支持可配置的细分级别
 * 
 * 主要功能：
 * - 球体几何数据生成
 * - OpenGL着色器管理
 * - 纹理坐标映射
 * - 高效的三角形渲染
 * 
 * @param radius 球体半径
 * @param stacks 垂直方向细分数量（影响球体的垂直精度）
 * @param slices 水平方向细分数量（影响球体的水平精度）
 * @param textureId OpenGL纹理ID
 */
class Sphere(
    private val radius: Float,
    private val stacks: Int,    // 垂直方向细分
    private val slices: Int,    // 水平方向细分
    private val textureId: Int
) {
    /**
     * OpenGL着色器程序ID
     * 
     * 编译和链接后的着色器程序标识符
     */
    private val program: Int

    /**
     * 顶点数据缓冲区
     * 
     * 存储球体所有顶点的3D坐标数据
     */
    private val vertexBuffer: FloatBuffer
    
    /**
     * 纹理坐标缓冲区
     * 
     * 存储球体所有顶点对应的纹理坐标
     */
    private val texCoordBuffer: FloatBuffer

    /**
     * 顶点总数量
     * 
     * 用于OpenGL绘制调用的顶点计数
     */
    private val vertexCount: Int

    /**
     * 顶点着色器源代码
     * 
     * 定义顶点变换和纹理坐标传递的GLSL代码：
     * - 接收MVP矩阵进行顶点变换
     * - 传递纹理坐标到片段着色器
     */
    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 vPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    /**
     * 片段着色器源代码
     * 
     * 定义像素颜色计算的GLSL代码：
     * - 从纹理采样获取像素颜色
     * - 输出最终的片段颜色
     */
    private val fragmentShaderCode = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        void main() {
            gl_FragColor = texture2D(uTexture, vTexCoord);
        }
    """.trimIndent()

    init {
        // 编译着色器并创建程序
        val vertexShader = EarthRenderer.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = EarthRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram().apply {
            GLES20.glAttachShader(this, vertexShader)
            GLES20.glAttachShader(this, fragmentShader)
            GLES20.glLinkProgram(this)

            // 检查链接错误
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(this, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val errorMsg = GLES20.glGetProgramInfoLog(this)
                GLES20.glDeleteProgram(this)
                throw RuntimeException("程序链接失败: $errorMsg")
            }
        }

        // 生成球体顶点数据
        val (vertices, texCoords) = generateSphereData()
        vertexCount = vertices.size / 3

        // 初始化顶点缓冲区
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer.position(0)

        // 初始化纹理坐标缓冲区
        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(texCoords)
        texCoordBuffer.position(0)
    }

    /**
     * 生成球体的顶点坐标和纹理坐标
     * 
     * 该方法使用球坐标系统生成球体的几何数据：
     * 1. 按照stacks和slices参数细分球体表面
     * 2. 计算每个顶点的3D坐标
     * 3. 生成对应的纹理坐标
     * 4. 构建三角形网格
     * 
     * 算法说明：
     * - 使用纬度（lat）和经度（lng）参数化球面
     * - 每个四边形被分解为两个三角形
     * - 纹理坐标按照球面投影映射
     * 
     * @return 包含顶点坐标和纹理坐标的数组对
     */
    private fun generateSphereData(): Pair<FloatArray, FloatArray> {
        val vertices = mutableListOf<Float>()
        val texCoords = mutableListOf<Float>()

        for (i in 0 until stacks) {
            val lat0 = Math.PI * (-0.5 + i.toFloat() / stacks)
            val z0 = Math.sin(lat0).toFloat()
            val zr0 = Math.cos(lat0).toFloat()

            val lat1 = Math.PI * (-0.5 + (i + 1).toFloat() / stacks)
            val z1 = Math.sin(lat1).toFloat()
            val zr1 = Math.cos(lat1).toFloat()

            for (j in 0 until slices) {
                val lng0 = 2 * Math.PI * (j.toFloat() / slices)
                val x0 = Math.sin(lng0).toFloat()
                val y0 = Math.cos(lng0).toFloat()

                val lng1 = 2 * Math.PI * ((j + 1).toFloat() / slices)
                val x1 = Math.sin(lng1).toFloat()
                val y1 = Math.cos(lng1).toFloat()

                // 第一个三角形
                vertices.addAll(listOf(
                    radius * x0 * zr0, radius * y0 * zr0, radius * z0,
                    radius * x1 * zr0, radius * y1 * zr0, radius * z0,
                    radius * x1 * zr1, radius * y1 * zr1, radius * z1
                ))

                // 第二个三角形
                vertices.addAll(listOf(
                    radius * x0 * zr0, radius * y0 * zr0, radius * z0,
                    radius * x1 * zr1, radius * y1 * zr1, radius * z1,
                    radius * x0 * zr1, radius * y0 * zr1, radius * z1
                ))

                // 纹理坐标
                val s0 = j.toFloat() / slices
                val s1 = (j + 1).toFloat() / slices
                val t0 = i.toFloat() / stacks
                val t1 = (i + 1).toFloat() / stacks

                texCoords.addAll(listOf(s0, t0, s1, t0, s1, t1))
                texCoords.addAll(listOf(s0, t0, s1, t1, s0, t1))
            }
        }

        return Pair(vertices.toFloatArray(), texCoords.toFloatArray())
    }

    /**
     * 绘制球体
     * 
     * 该方法执行球体的OpenGL渲染：
     * 1. 激活着色器程序
     * 2. 获取着色器变量的位置
     * 3. 设置顶点属性和uniform变量
     * 4. 绑定纹理
     * 5. 执行绘制调用
     * 6. 清理OpenGL状态
     * 
     * @param mvpMatrix 模型-视图-投影变换矩阵
     */
    fun draw(mvpMatrix: FloatArray) {
        // 使用OpenGL程序
        GLES20.glUseProgram(program)

        // 获取属性和uniform位置
        val positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        val mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        val textureHandle = GLES20.glGetUniformLocation(program, "uTexture")

        // 启用顶点属性
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        // 设置顶点数据
        GLES20.glVertexAttribPointer(
            positionHandle, 3, GLES20.GL_FLOAT, false,
            12, vertexBuffer
        )

        // 设置纹理坐标数据
        GLES20.glVertexAttribPointer(
            texCoordHandle, 2, GLES20.GL_FLOAT, false,
            8, texCoordBuffer
        )

        // 设置MVP矩阵
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        // 绑定纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureHandle, 0)

        // 绘制球体
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)

        // 禁用顶点属性
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }
}
