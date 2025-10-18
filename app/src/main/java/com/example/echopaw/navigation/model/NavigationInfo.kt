package com.example.echopaw.navigation.model

import androidx.fragment.app.Fragment
import java.util.Objects

/**
 * 导航信息数据类
 * 
 * 该类用于封装导航相关的信息，包含Fragment的名称和实例。
 * 主要用于导航系统中管理和识别不同的Fragment页面。
 * 
 * @property fragmentName Fragment的名称标识符，用于区分不同的Fragment
 * @property fragment Fragment的实例对象，用于实际的页面显示
 * 
 * @constructor 创建一个NavigationInfo实例
 * @param fragmentName Fragment的名称，不能为空
 * @param fragment Fragment的实例，不能为null
 * 
 * @author EchoPaw Team
 * @since 1.0
 */
class NavigationInfo(var fragmentName: String, var fragment: Fragment) {
    
    /**
     * 返回对象的字符串表示形式
     * 
     * 该方法重写了Object类的toString方法，提供了NavigationInfo对象的
     * 可读性较好的字符串表示，包含fragmentName和fragment的信息。
     * 
     * @return 包含fragmentName和fragment信息的格式化字符串
     * 格式：NavigationInfo{fragmentName='名称', fragment=Fragment实例}
     */
    override fun toString(): String {
        return "NavigationInfo{" +
                "fragmentName='" + fragmentName + '\'' +
                ", fragment=" + fragment +
                '}'
    }

    /**
     * 判断两个NavigationInfo对象是否相等
     * 
     * 该方法重写了Object类的equals方法，用于比较两个NavigationInfo对象
     * 是否相等。比较规则：
     * 1. 如果是同一个对象引用，返回true
     * 2. 如果对象为null或类型不同，返回false
     * 3. 比较fragmentName和fragment是否都相等
     * 
     * @param o 要比较的对象，可以为null
     * @return 如果两个对象相等返回true，否则返回false
     */
    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as NavigationInfo
        return fragmentName == that.fragmentName && fragment == that.fragment
    }

    /**
     * 计算对象的哈希码
     * 
     * 该方法重写了Object类的hashCode方法，基于fragmentName和fragment
     * 计算哈希码。确保相等的对象具有相同的哈希码，用于在HashMap、HashSet
     * 等集合中正确存储和查找对象。
     * 
     * @return 基于fragmentName和fragment计算得出的哈希码值
     */
    override fun hashCode(): Int {
        return Objects.hash(fragmentName, fragment)
    }
}
