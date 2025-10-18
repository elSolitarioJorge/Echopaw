package com.example.echopaw.message

/**
 * 消息数据模型类
 * 
 * 该类用于表示应用中的消息项数据结构：
 * 1. 存储消息的基本信息
 * 2. 包含图片、时间、位置和标签等属性
 * 3. 用于RecyclerView的数据源
 * 4. 支持不同类型的标签分类
 * 
 * 主要功能：
 * - 消息数据封装
 * - 支持多种标签类型
 * - 时间和位置信息存储
 * - 图片资源关联
 * 
 * @param imageId 头像图片资源ID，用于显示消息对应的图片
 * @param time 时间戳字符串，记录消息的创建时间
 * @param location 位置信息字符串，记录消息的地理位置
 * @param tag 标签字符串，用于分类和标识消息类型（如#神秘、#快乐、#期待等）
 */
class Message(
    /** 头像图片资源ID */
    val imageId: Int,
    /** 时间戳字符串 */
    val time: String,
    /** 位置信息字符串 */
    val location: String,
    /** 标签字符串 */
    val tag: String
)
