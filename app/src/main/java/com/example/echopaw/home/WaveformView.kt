package com.example.echopaw.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * 音频波形显示视图
 * 
 * 该自定义View用于实时显示音频录制过程中的波形数据：
 * 1. 以竖条形式显示音频振幅
 * 2. 支持实时更新波形数据
 * 3. 提供上下对称的波形显示效果
 * 4. 自动管理显示点数，保持流畅的动画效果
 * 
 * @param context 上下文
 * @param attrs 属性集合
 * @param defStyleAttr 默认样式属性
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * 波形竖条的画笔
     * 配置为白色填充，启用抗锯齿
     */
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    /**
     * 背景画笔
     * 配置为透明背景，可根据需求修改颜色
     */
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.TRANSPARENT // 透明背景，可根据需求修改
        style = Paint.Style.FILL
    }
    
    /** 存储波形振幅数据的列表 */
    private val waveformData = mutableListOf<Float>()
    
    /** 最大显示点数，控制波形的密度和性能 */
    private val maxPoints = 80  // 减少点数，增大间距
    
    /** 竖条宽度比例，控制竖条的粗细 */
    private val barWidthRatio = 0.4f  // 减小比例，让竖条更窄
    
    /** 间距比例，控制竖条之间的空隙 */
    private val spacingRatio = 0.6f   // 间距比例，控制竖条间空隙

    /**
     * 添加新的振幅点
     * 
     * 该方法用于实时添加音频振幅数据：
     * 1. 如果数据点数超过最大限制，移除最旧的数据点
     * 2. 添加新的振幅值到数据列表末尾
     * 3. 触发视图重绘以显示最新的波形
     * 
     * @param amplitude 音频振幅值，通常在0.0-1.0范围内
     */
    fun addAmplitude(amplitude: Float) {
        if (waveformData.size >= maxPoints) {
            waveformData.removeAt(0)
        }
        waveformData.add(amplitude)
        invalidate()
    }

    /**
     * 更新波形列表
     * 
     * 该方法用于批量更新波形数据：
     * 1. 清空当前所有波形数据
     * 2. 添加新的数据列表，最多保留maxPoints个点
     * 3. 触发视图重绘以显示新的波形
     * 
     * @param data 新的振幅数据列表
     */
    fun updateWaveform(data: List<Float>) {
        waveformData.clear()
        waveformData.addAll(data.takeLast(maxPoints))
        invalidate()
    }

    /**
     * 清空波形
     * 
     * 该方法清除所有波形数据：
     * 1. 清空波形数据列表
     * 2. 触发视图重绘，显示空白状态
     * 
     * 通常在录音结束或重置时调用
     */
    fun clear() {
        waveformData.clear()
        invalidate()
    }

    /**
     * 绘制波形视图
     * 
     * 该方法负责在画布上绘制音频波形：
     * 1. 检查是否有波形数据，无数据时直接返回
     * 2. 计算视图尺寸和中心轴位置
     * 3. 根据数据点数计算每个竖条的宽度和间距
     * 4. 绘制透明背景
     * 5. 遍历波形数据，为每个振幅值绘制上下对称的竖条
     * 6. 振幅值被映射到视图高度的一半，形成对称效果
     * 
     * @param canvas 用于绘制的画布
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (waveformData.isEmpty()) return

        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2f  // 水平中心轴
        val barCount = waveformData.size
        // 单格宽度（包含竖条和间距）
        val singleBarWidth = width / barCount
        // 竖条实际宽度
        val barActualWidth = singleBarWidth * barWidthRatio
        // 竖条之间的间距
        val spacing = singleBarWidth * spacingRatio

        // 绘制背景（无虚影，透明背景可根据需求修改颜色）
        canvas.drawRect(0f, 0f, width, height, bgPaint)

        waveformData.forEachIndexed { index, amplitude ->
            val startX = index * singleBarWidth
            // 计算竖条左右坐标，预留间距
            val left = startX + spacing / 2
            val right = startX + singleBarWidth - spacing / 2

            // 振幅映射到 0~centerY（上下对称）
            val barHalfHeight = amplitude.coerceIn(0f, centerY)

            // 绘制上半部分竖条
            canvas.drawRect(
                left,
                centerY - barHalfHeight,
                right,
                centerY,
                barPaint
            )
            // 绘制下半部分竖条（轴对称）
            canvas.drawRect(
                left,
                centerY,
                right,
                centerY + barHalfHeight,
                barPaint
            )
        }
    }
}