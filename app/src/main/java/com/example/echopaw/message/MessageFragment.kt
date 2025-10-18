// MessageFragment.java
package com.example.echopaw.message

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.echopaw.R
import me.everything.android.ui.overscroll.OverScrollDecoratorHelper

import java.util.List

/**
 * 消息页面Fragment
 * 
 * 该Fragment用于显示消息列表界面：
 * 1. 管理消息列表的显示
 * 2. 配置RecyclerView和适配器
 * 3. 设置列表分割线和滚动效果
 * 4. 提供模拟消息数据
 * 
 * 主要功能：
 * - 消息列表展示
 * - RecyclerView配置
 * - 分割线装饰
 * - 过度滚动效果
 * - 模拟数据生成
 * 
 * UI组件：
 * - RecyclerView：消息列表容器
 * - LinearLayoutManager：线性布局管理器
 * - DividerItemDecoration：分割线装饰器
 * - OverScrollDecoratorHelper：过度滚动效果
 */
class MessageFragment : Fragment() {
    
    /**
     * 创建Fragment的视图
     * 
     * 该方法负责初始化消息列表界面：
     * 1. 加载fragment_message布局
     * 2. 配置RecyclerView的布局管理器
     * 3. 设置分割线装饰器
     * 4. 创建模拟消息数据
     * 5. 设置适配器和过度滚动效果
     * 
     * RecyclerView配置：
     * - 使用LinearLayoutManager进行垂直线性布局
     * - 添加自定义分割线装饰
     * - 启用垂直方向的过度滚动效果
     * 
     * 数据源：
     * - 包含11条模拟消息数据
     * - 每条消息包含图片、时间、位置和标签信息
     * - 支持#神秘、#快乐、#期待等不同标签类型
     * 
     * @param inflater 用于加载布局的LayoutInflater
     * @param container 父容器ViewGroup，可能为null
     * @param savedInstanceState 保存的实例状态，可能为null
     * @return 创建的View对象
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_message, container, false)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        val dividerItemDecoration = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
        val drawable = requireContext().getDrawable(R.drawable.divider)
        if (drawable != null) {
            dividerItemDecoration.setDrawable(drawable)
        }
        recyclerView.addItemDecoration(dividerItemDecoration)

        val dataList = List.of(
            Message(R.drawable.pic1, "2025.04.27 11:11", "中国•浙江省•杭州市", "#神秘"),
            Message(R.drawable.pic2, "2025.03.10 08:15", "中国•浙江省•杭州市", "#快乐"),
            Message(R.drawable.pic3, "2025.02.02 11:11", "中国•浙江省•杭州市", "#快乐"),
            Message(R.drawable.pic4, "2025.02.10 11:11", "中国•浙江省•杭州市", "#神秘"),
            Message(R.drawable.pic5, "2025.01.10 08:15", "中国•浙江省•杭州市", "#神秘"),
            Message(R.drawable.pic6, "2025.12.27 11:11", "中国•浙江省•杭州市", "#期待"),
            Message(R.drawable.pic1, "2025.11.20 12:11", "中国•浙江省•杭州市", "#神秘"),
            Message(R.drawable.pic2, "2025.05.10 08:14", "中国•浙江省•杭州市", "#期待"),
            Message(R.drawable.pic3, "2025.02.18 09:29", "中国•浙江省•杭州市", "#神秘"),
            Message(R.drawable.pic4, "2025.07.01 08:21", "中国•浙江省•杭州市", "#快乐"),
            Message(R.drawable.pic5, "2025.11.22 15:30", "中国•浙江省•杭州市", "#神秘")
        )
        recyclerView.adapter = RecyclerAdapter(dataList)
        OverScrollDecoratorHelper.setUpOverScroll(
            recyclerView,
            OverScrollDecoratorHelper.ORIENTATION_VERTICAL
        )

        return view
    }
}