package com.example.echopaw.message

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.echopaw.R
import com.example.echopaw.message.RecyclerAdapter.MyViewHolder

/**
 * 消息列表RecyclerView适配器
 * 
 * 该适配器用于管理消息列表的显示：
 * 1. 绑定Message数据到列表项视图
 * 2. 管理ViewHolder的创建和复用
 * 3. 处理不同标签的颜色显示
 * 4. 提供高效的列表滚动性能
 * 
 * 主要功能：
 * - 数据绑定和视图管理
 * - 动态标签颜色设置
 * - ViewHolder模式优化
 * - 支持多种消息标签类型
 * 
 * 支持的标签类型：
 * - #神秘：神秘色彩主题
 * - #快乐：快乐色彩主题  
 * - #期待：期待色彩主题
 * - 默认：通用色彩主题
 * 
 * @param dataList 消息数据列表，可能为null
 */
class RecyclerAdapter(private val dataList: List<Message>?) :
    RecyclerView.Adapter<MyViewHolder>() {
    
    /**
     * ViewHolder内部类
     * 
     * 该类用于缓存列表项视图中的UI组件：
     * 1. 避免重复的findViewById调用
     * 2. 提高列表滚动性能
     * 3. 管理单个消息项的所有视图组件
     * 4. 支持视图复用机制
     * 
     * 包含的视图组件：
     * - ivPicture：消息图片ImageView
     * - timeTextView：时间显示TextView
     * - locationTextView：位置显示TextView
     * - tagTextView：标签显示TextView
     * 
     * @param view 列表项的根视图
     */
    class MyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        /** 消息图片显示组件 */
        var ivPicture: ImageView =
            view.findViewById(R.id.iv_picture)
        /** 时间显示组件 */
        var timeTextView: TextView = view.findViewById(R.id.tv_time)
        /** 位置显示组件 */
        var locationTextView: TextView =
            view.findViewById(R.id.tv_location)
        /** 标签显示组件 */
        var tagTextView: TextView = view.findViewById(R.id.tv_tag)
    }

    /**
     * 创建ViewHolder
     * 
     * 该方法在需要新的ViewHolder时被调用：
     * 1. 加载recycler_item布局文件
     * 2. 创建MyViewHolder实例
     * 3. 初始化视图组件的引用
     * 4. 返回可复用的ViewHolder
     * 
     * 布局加载：
     * - 使用LayoutInflater从XML创建视图
     * - 设置正确的布局参数
     * - 避免附加到父容器
     * 
     * @param parent 父ViewGroup容器
     * @param viewType 视图类型（本适配器中未使用）
     * @return 新创建的MyViewHolder实例
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.recycler_item, parent, false)
        return MyViewHolder(view)
    }

    /**
     * 绑定数据到ViewHolder
     * 
     * 该方法将Message数据绑定到ViewHolder的视图组件：
     * 1. 设置消息图片资源
     * 2. 显示时间、位置和标签文本
     * 3. 根据标签类型动态设置背景颜色
     * 4. 处理GradientDrawable的颜色变更
     * 
     * 标签颜色映射：
     * - #神秘 → tag_mystery颜色资源
     * - #快乐 → tag_happy颜色资源
     * - #期待 → tag_expect颜色资源
     * - 其他 → tag_default颜色资源
     * 
     * 颜色设置逻辑：
     * - 获取TextView的背景Drawable
     * - 检查是否为GradientDrawable类型
     * - 根据标签内容选择对应颜色资源
     * - 动态设置背景颜色
     * 
     * @param holder 要绑定数据的ViewHolder
     * @param position 数据在列表中的位置
     */
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val message = dataList!![position]

        holder.ivPicture.setImageResource(message.imageId)
        holder.timeTextView.text = message.time
        holder.locationTextView.text = message.location
        holder.tagTextView.text = message.tag

        val bgDrawable = holder.tagTextView.background.mutate()
        if (bgDrawable is GradientDrawable) {
            val colorResId = when (message.tag) {
                "#神秘" -> R.color.tag_mystery
                "#快乐" -> R.color.tag_happy
                "#期待" -> R.color.tag_expect
                else -> R.color.tag_default
            }

            val color = ContextCompat.getColor(holder.itemView.context, colorResId)
            bgDrawable.setColor(color)
        }
    }

    /**
     * 获取列表项总数
     * 
     * 该方法返回数据列表的大小：
     * 1. 检查dataList是否为null
     * 2. 返回列表大小或0
     * 3. 用于RecyclerView确定需要显示的项目数量
     * 4. 支持空数据列表的安全处理
     * 
     * 安全性：
     * - 使用Elvis操作符处理null情况
     * - 避免NullPointerException
     * - 提供默认值0
     * 
     * @return 数据列表的大小，如果列表为null则返回0
     */
    override fun getItemCount(): Int {
        return dataList?.size ?: 0
    }
}
