package com.example.echopaw.navigation.base;

/**
 * 基础视图接口
 * 
 * 该接口定义了MVP架构中View层的基础规范：
 * 1. 提供Presenter设置功能
 * 2. 支持泛型以适配不同类型的Presenter
 * 3. 作为所有View接口的基础接口
 * 
 * 遵循MVP设计模式，确保View层与Presenter层的正确关联
 * 
 * @param <T> Presenter类型的泛型参数
 */
public interface BaseView<T> {
    
    /**
     * 设置Presenter实例
     * 
     * 该方法用于建立View与Presenter之间的关联：
     * 1. 接收Presenter实例并保存引用
     * 2. 建立双向通信机制
     * 3. 为后续的业务逻辑交互做准备
     * 
     * @param presenter Presenter实例，可为空
     */
    void setPresenter(T presenter);
}
