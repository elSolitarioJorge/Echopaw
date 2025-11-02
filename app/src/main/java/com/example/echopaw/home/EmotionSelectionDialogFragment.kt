package com.example.echopaw.home

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.echopaw.R
import com.example.echopaw.network.EmotionType

/**
 * 情绪选择对话框Fragment，对应布局dialog_two.xml
 * 
 * 功能：
 * 1. 初始化显示默认情绪图片（happy）
 * 2. 为5个情绪区域设置点击事件监听器
 * 3. 点击确认按钮后将选中的情绪传递给ReleaseFragment并关闭对话框
 */
class EmotionSelectionDialogFragment : DialogFragment() {

    private val flowViewModel: ReleaseFlowViewModel by activityViewModels()
    
    // UI元素
    private lateinit var ivEmotionSelected: ImageView
    private lateinit var ivPointSelected: ImageView
    private lateinit var btnConfirm: ImageView
    private lateinit var btnBack: ImageView
    
    // 情绪区域View
    private lateinit var viewCurious: View
    private lateinit var viewCalm: View
    private lateinit var viewHappy: View
    private lateinit var viewAngry: View
    private lateinit var viewAnxious: View
    
    // 当前选中的情绪
    private var selectedEmotion: String = "happy"
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_two, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupInitialState()
        setupClickListeners()
        setupDialogWindow()
    }
    
    /**
     * 初始化View引用
     */
    private fun initViews(view: View) {
        ivEmotionSelected = view.findViewById(R.id.iv_emotion_selected)
        ivPointSelected = view.findViewById(R.id.iv_point_selected)
        btnConfirm = view.findViewById(R.id.btn_confirm)
        btnBack = view.findViewById(R.id.btn_back)
        
        viewCurious = view.findViewById(R.id.view_curious)
        viewCalm = view.findViewById(R.id.view_calm)
        viewHappy = view.findViewById(R.id.view_happy)
        viewAngry = view.findViewById(R.id.view_angry)
        viewAnxious = view.findViewById(R.id.view_anxious)
    }
    
    /**
     * 设置初始状态：默认显示happy情绪
     */
    private fun setupInitialState() {
        selectedEmotion = "happy"
        ivEmotionSelected.setImageResource(R.drawable.img_point_happy)
        ivPointSelected.setImageResource(R.drawable.img_point_3)
    }
    
    /**
     * 设置点击事件监听器
     */
    private fun setupClickListeners() {
        // 情绪区域点击事件
        viewCurious.setOnClickListener {
            selectEmotion("curious", R.drawable.img_point_curious, R.drawable.img_point_1)
        }
        
        viewCalm.setOnClickListener {
            selectEmotion("calm", R.drawable.img_point_calm, R.drawable.img_point_2)
        }
        
        viewHappy.setOnClickListener {
            selectEmotion("happy", R.drawable.img_point_happy, R.drawable.img_point_3)
        }
        
        viewAngry.setOnClickListener {
            selectEmotion("angry", R.drawable.img_point_angry, R.drawable.img_point_4)
        }
        
        viewAnxious.setOnClickListener {
            selectEmotion("anxious", R.drawable.img_point_anxious, R.drawable.img_point_4)
        }
        
        // 确认按钮点击事件
        btnConfirm.setOnClickListener {
            confirmSelection()
        }
        
        // 返回按钮点击事件
        btnBack.setOnClickListener {
            dismiss()
        }
    }
    
    /**
     * 选择情绪
     */
    private fun selectEmotion(emotion: String, emotionImageRes: Int, pointImageRes: Int) {
        selectedEmotion = emotion
        ivEmotionSelected.setImageResource(emotionImageRes)
        ivPointSelected.setImageResource(pointImageRes)
    }
    
    /**
     * 确认选择
     */
    private fun confirmSelection() {
        // 根据选中的情绪设置对应的EmotionType
        val emotionType = when (selectedEmotion) {
            "curious" -> EmotionType.CURIOUS
            "calm" -> EmotionType.CALM
            "happy" -> EmotionType.HAPPY
            "angry" -> EmotionType.ANGRY
            "anxious" -> EmotionType.ANXIOUS
            else -> EmotionType.HAPPY
        }
        
        // 将选中的情绪设置到ViewModel中
        flowViewModel.setEmotionType(emotionType)
        
        // 关闭对话框
        dismiss()
        
        // 通知调用者进行下一步操作（导航到ReleaseFragment）
        (parentFragment as? RecordFragment)?.navigateToReleaseAfterEmotionSelection()
    }
    
    /**
     * 设置对话框窗口属性
     */
    private fun setupDialogWindow() {
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.90).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }
    
    companion object {
        const val TAG = "EmotionSelectionDialog"
        
        fun newInstance(): EmotionSelectionDialogFragment {
            return EmotionSelectionDialogFragment()
        }
    }
}