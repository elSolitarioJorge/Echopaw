package com.example.echopaw.navigation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentManager
import com.example.echopaw.R
import com.example.echopaw.databinding.ActivityMainBinding
import com.example.echopaw.home.RecordActivity
import com.example.echopaw.navigation.contract.INavigationContract
import com.example.echopaw.navigation.model.NavigationInfo
import com.example.echopaw.navigation.model.NavigationModel
import com.example.echopaw.navigation.presenter.NavigationPresenter
import com.oguzdev.circularfloatingactionmenu.library.FloatingActionMenu
import com.oguzdev.circularfloatingactionmenu.library.SubActionButton

/**
 * 主Activity类 - 应用程序的主要导航界面
 * 
 * 该Activity实现了MVP架构模式中的View层，负责：
 * 1. 管理底部导航栏和Fragment切换
 * 2. 处理悬浮操作按钮(FAB)和圆形菜单
 * 3. 管理窗口状态栏和系统UI
 * 4. 处理返回键双击退出逻辑
 * 5. 提供各种动画效果和用户交互
 * 
 * @author EchoPaw Team
 * @since 1.0
 */
class MainActivity : AppCompatActivity(), INavigationContract.INavigationView {

    /** ViewBinding实例，用于访问布局中的视图组件 */
    private lateinit var binding: ActivityMainBinding
    
    /** MVP架构中的Presenter实例，处理业务逻辑 */
    private lateinit var mPresenter: INavigationContract.INavigationPresenter

    /**
     * 设置Presenter实例
     * 
     * 该方法是INavigationView接口的实现，用于在MVP架构中
     * 建立View和Presenter之间的连接。
     * 
     * @param presenter Presenter实例，可以为null
     */
    override fun setPresenter(presenter: INavigationContract.INavigationPresenter?) {
        presenter?.let { mPresenter = it }
    }

    /** 圆形悬浮操作菜单实例 */
    private var floatingActionMenu: FloatingActionMenu? = null
    
    /** 记录第一次按返回键的时间戳，用于双击退出功能 */
    private var firstBackPressedTime: Long = 0
    
    /** Fragment管理器，用于管理Fragment的显示和隐藏 */
    private lateinit var fragmentManager: FragmentManager
    
    /** 主线程Handler，用于处理延时任务和UI更新 */
    private lateinit var handler: Handler

    /**
     * 伴生对象 - 定义常量
     * 
     * 包含应用中使用的各种时间常量和尺寸常量
     */
    companion object {
        /** 双击退出的时间间隔（毫秒） */
        private const val DOUBLE_CLICK_TIME_DELAY = 2000L
        
        /** 动画延迟时间（毫秒） */
        private const val ANIMATION_DELAY = 200L
        
        /** 子按钮尺寸（dp） */
        private const val SUB_BUTTON_SIZE_DP = 100
        
        // 动画时长优化
        /** 导航容器上滑退出动画时长（毫秒） */
        private const val EXIT_ANIM_DURATION = 500L
        
        /** 协调器布局下滑动画时长（毫秒） */
        private const val COORDINATOR_ANIM_DURATION = 500L
        
        /** 回弹动画时长（毫秒） */
        private const val RETURN_ANIM_DURATION = 700L
    }

    /**
     * Activity创建时的回调方法
     * 
     * 该方法在Activity被创建时调用，负责初始化各种组件和设置：
     * 1. 初始化ViewBinding
     * 2. 设置Handler
     * 3. 配置窗口插入边距
     * 4. 设置状态栏样式
     * 5. 初始化导航系统
     * 6. 设置返回键处理
     * 7. 配置悬浮按钮监听器
     * 
     * @param savedInstanceState 保存的实例状态，用于恢复Activity状态
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handler = Handler(Looper.getMainLooper())
        setupWindowInsets()
        setupStatusBar()
        setupNavigation()
        setupBackPressHandler()
        setupFabBaseListener()
    }

    /**
     * 设置窗口插入边距
     * 
     * 该方法配置窗口的系统栏插入边距，确保内容不会被状态栏、
     * 导航栏等系统UI遮挡。使用WindowInsetsCompat来处理不同
     * Android版本的兼容性问题。
     */
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    /**
     * 设置状态栏样式
     * 
     * 该方法配置状态栏的外观，包括：
     * 1. 设置状态栏为透明色（API 21+）
     * 2. 设置状态栏文字为深色（API 23+）
     * 3. 配置全屏显示模式，内容延伸到状态栏下方
     * 
     * 通过版本检查确保在不同Android版本上的兼容性。
     */
    private fun setupStatusBar() {
        // Android 5.0（API 21）及以上支持设置状态栏颜色
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.transparent)
            // Android 6.0（API 23）及以上支持设置状态栏文字颜色（深色/浅色）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // 如果状态栏背景是浅色（如透明），建议将文字设置为深色，避免看不清
                // 反之，若背景是深色，可设置为浅色文字（View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR 不设置即可）
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    /**
     * 设置导航系统
     * 
     * 该方法初始化MVP架构的导航系统：
     * 1. 禁用底部导航栏的图标着色
     * 2. 创建NavigationModel数据层
     * 3. 创建NavigationPresenter业务逻辑层
     * 4. 设置Presenter并获取导航信息
     */
    private fun setupNavigation() {
        binding.bnvNavigation.itemIconTintList = null
        val navigationModel = NavigationModel()
        setPresenter(NavigationPresenter(navigationModel, this))
        mPresenter.getNavigationInfo("开！")
    }

    /**
     * 设置返回键处理器
     * 
     * 该方法注册一个返回键回调处理器，实现双击返回键退出应用的功能。
     * 使用OnBackPressedDispatcher来处理返回键事件，确保在不同
     * Android版本上的兼容性。
     */
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleDoubleBackPress()
            }
        })
    }

    /**
     * 处理双击返回键退出逻辑
     * 
     * 该方法实现双击返回键退出应用的功能：
     * 1. 记录第一次按返回键的时间
     * 2. 如果在指定时间内再次按返回键，则退出应用
     * 3. 如果超时，则重置计时器并提示用户
     * 
     * 提供良好的用户体验，避免误触退出应用。
     */
    private fun handleDoubleBackPress() {
        val currentTime = System.currentTimeMillis()
        if (firstBackPressedTime == 0L) {
            firstBackPressedTime = currentTime
            showToast("再按一次退出")
        } else {
            if (currentTime - firstBackPressedTime < DOUBLE_CLICK_TIME_DELAY) {
                finish()
            } else {
                firstBackPressedTime = 0
                showToast("再按一次退出")
            }
        }
    }

    /**
     * 显示Toast消息
     * 
     * 该方法是一个便捷方法，用于显示短时间的Toast消息。
     * 
     * @param message 要显示的消息文本
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * 显示导航信息
     * 
     * 该方法是INavigationView接口的实现，用于显示从Presenter
     * 获取的导航信息。主要功能包括：
     * 1. 初始化FragmentManager
     * 2. 将所有Fragment添加到容器中并隐藏
     * 3. 设置Fragment切换动画
     * 4. 配置底部导航栏
     * 5. 初始化悬浮操作按钮菜单
     * 
     * @param navigationInfos 导航信息列表，包含Fragment名称和实例
     */
    override fun showNavigationInfomation(navigationInfos: List<NavigationInfo?>?) {
        fragmentManager = supportFragmentManager
        val initialTransaction = fragmentManager.beginTransaction()
        navigationInfos?.forEach { info ->
            info?.let {
                initialTransaction.add(binding.fcvNavigation.id, it.fragment)
                initialTransaction.hide(it.fragment)
            }
        }
        initialTransaction.setCustomAnimations(
            R.anim.slide_in_right,
            R.anim.slide_out_left,
            R.anim.slide_in_left,
            R.anim.slide_out_right
        ).commit()

        setupBottomNavigation(navigationInfos)
        if (floatingActionMenu == null) initFloatActionButton()
    }

    /**
     * 设置底部导航栏
     * 
     * 该方法配置底部导航栏的行为：
     * 1. 过滤掉null的导航信息
     * 2. 设置菜单项选择监听器
     * 3. 默认选中第一个菜单项
     * 
     * @param navigationInfos 导航信息列表
     */
    private fun setupBottomNavigation(navigationInfos: List<NavigationInfo?>?) {
        val nonNullInfos = navigationInfos?.filterNotNull() ?: emptyList()
        binding.bnvNavigation.setOnItemSelectedListener { menuItem ->
            handleMenuItemSelection(menuItem, nonNullInfos)
            true
        }
        if (binding.bnvNavigation.menu.size() > 0) {
            binding.bnvNavigation.selectedItemId = binding.bnvNavigation.menu.getItem(0).itemId
        }
    }

    /**
     * 处理菜单项选择
     * 
     * 该方法处理底部导航栏菜单项的选择事件：
     * 1. 显示对应的Fragment
     * 2. 关闭悬浮操作菜单（如果已打开）
     * 
     * @param menuItem 被选中的菜单项
     * @param navigationInfos 导航信息列表
     */
    private fun handleMenuItemSelection(menuItem: MenuItem, navigationInfos: List<NavigationInfo>) {
        showSelectedFragment(menuItem.title.toString(), navigationInfos)
        floatingActionMenu?.takeIf { it.isOpen }?.close(true)
    }

    /**
     * 显示选中的Fragment
     * 
     * 该方法根据标题显示对应的Fragment，隐藏其他Fragment。
     * 使用Fragment事务来管理Fragment的显示状态。
     * 
     * @param title Fragment的标题名称
     * @param navigationInfos 导航信息列表
     */
    private fun showSelectedFragment(title: String, navigationInfos: List<NavigationInfo>) {
        fragmentManager.beginTransaction().apply {
            navigationInfos.forEach { info ->
                if (title == info.fragmentName) show(info.fragment) else hide(info.fragment)
            }
            commit()
        }
    }

    /**
     * 设置悬浮操作按钮基础监听器
     * 
     * 该方法为主悬浮操作按钮设置点击监听器：
     * 1. 添加按钮缩放动画效果
     * 2. 切换圆形菜单的开关状态
     * 3. 如果菜单不存在则初始化
     */
    private fun setupFabBaseListener() {
        binding.fabNavigation.setOnClickListener {
            startFabScaleAnimation(1f, 1.2f) // 放大
            handler.postDelayed({ startFabScaleAnimation(1.2f, 1f) }, 150L) // 缩回原始大小
            floatingActionMenu?.let { menu ->
                if (menu.isOpen) menu.close(true) else menu.open(true)
            } ?: run { initFloatActionButton() }
        }
    }

    /**
     * 初始化悬浮操作按钮菜单
     * 
     * 该方法创建圆形悬浮操作菜单，包含三个子按钮：
     * 1. 心情录按钮 - 跳转到录音功能
     * 2. 留声瓶按钮 - 留声瓶功能（待实现）
     * 3. 许愿按钮 - 许愿功能（待实现）
     * 
     * 菜单配置：
     * - 起始角度：220度
     * - 结束角度：320度
     * - 半径：120dp
     */
    private fun initFloatActionButton() {
        if (floatingActionMenu != null) return

        val builder = SubActionButton.Builder(this)

        // 创建心情录按钮
        val recordButton = createCircleImageSubActionButton(builder, R.drawable.ic_r_record) {
            handleActionButtonClick("心情录")
        }
        // 创建留声瓶按钮
        val bottleButton = createCircleImageSubActionButton(builder, R.drawable.ic_r_bottle) {
            handleActionButtonClick("留声瓶")
        }
        // 创建许愿按钮
        val wishingButton = createCircleImageSubActionButton(builder, R.drawable.ic_r_wishing) {
            handleActionButtonClick("许愿")
        }
        floatingActionMenu = FloatingActionMenu.Builder(this)
            .setStartAngle(220)
            .setEndAngle(320)
            .setRadius(dpToPx(120))
            .addSubActionView(wishingButton)
            .addSubActionView(recordButton)
            .addSubActionView(bottleButton)
            .attachTo(binding.fabNavigation)
            .build()
    }

    /**
     * 创建圆形图像子操作按钮
     * 
     * 该方法创建一个圆形的子操作按钮，具有以下特性：
     * 1. 设置图像资源和缩放类型
     * 2. 配置圆形裁剪效果
     * 3. 添加点击和触摸反馈效果
     * 4. 设置透明背景
     * 
     * @param builder SubActionButton构建器
     * @param imageRes 图像资源ID
     * @param clickListener 点击事件监听器
     * @return 配置完成的SubActionButton实例
     */
    private fun createCircleImageSubActionButton(
        builder: SubActionButton.Builder,
        imageRes: Int,
        clickListener: () -> Unit
    ): SubActionButton {
        val imageView = ImageView(this).apply {
            setImageResource(imageRes)
            scaleType = ImageView.ScaleType.FIT_XY
            background = null
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            clipToOutline = true
        }
        val button = builder.setContentView(imageView)
            .setLayoutParams(FrameLayout.LayoutParams(dpToPx(SUB_BUTTON_SIZE_DP), dpToPx(SUB_BUTTON_SIZE_DP)))
            .setBackgroundDrawable(null)
            .build()

        button.setBackgroundColor(Color.TRANSPARENT)
        return button.apply {
                setOnClickListener { clickListener() }
                setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> view.alpha = 0.7f
                        MotionEvent.ACTION_UP -> view.alpha = 1f
                    }
                    false
                }
            }
    }

    /**
     * 处理操作按钮点击事件
     * 
     * 该方法处理悬浮操作菜单中子按钮的点击事件：
     * 1. 关闭悬浮操作菜单
     * 2. 执行退出动画
     * 3. 根据按钮名称执行相应操作
     * 4. 延迟执行返回动画
     * 
     * 支持的操作：
     * - "心情录"：跳转到录音Activity，带缩放转场动画
     * - "许愿"：许愿功能（待实现）
     * - "留声瓶"：留声瓶功能（待实现）
     * 
     * @param actionName 操作按钮的名称
     */
    private fun handleActionButtonClick(actionName: String) {
        floatingActionMenu?.close(true)
        startExitAnimation {
            when (actionName) {
                "心情录" -> startActivity(Intent(this, RecordActivity::class.java)).also {
                    overridePendingTransition(R.anim.zoom_in, R.anim.zoom_out)
                }
                "许愿" -> { /* TODO */ }
                "留声瓶" -> { /* TODO */ }
            }
            handler.postDelayed({ startReturnAnimation() }, ANIMATION_DELAY)
        }
    }

//    private fun startExitAnimation(onEnd: (() -> Unit)? = null) {
//        ObjectAnimator.ofFloat(binding.fcvNavigation, "translationY", 0f, -binding.fcvNavigation.height.toFloat()).apply {
//            duration = EXIT_ANIM_DURATION
//            interpolator = AccelerateDecelerateInterpolator()
//            addListener(object : AnimatorListenerAdapter() {
//                override fun onAnimationEnd(animation: Animator) {
//                    startCoordinatorExitAnimation()
//                    onEnd?.invoke()
//                }
//            })
//            start()
//        }
//    }


    /**
     * 开始退出动画
     * 
     * 该方法执行页面退出时的动画效果：
     * 1. Fragment容器向上滑动退出屏幕
     * 2. 协调器布局向下滑动退出屏幕
     * 3. 两个动画同时执行，创建分离效果
     * 4. Fragment容器动画结束后执行回调函数
     * 
     * @param onEnd 动画结束后的回调函数，可选参数
     */
    private fun startExitAnimation(onEnd: (() -> Unit)? = null) {
        // fcvNavigation 上滑
        val fcvAnim = ObjectAnimator.ofFloat(
            binding.fcvNavigation,
            "translationY",
            0f,
            -binding.fcvNavigation.height.toFloat()
        ).apply {
            duration = EXIT_ANIM_DURATION
            interpolator = AccelerateDecelerateInterpolator()
        }

        // coordinatorLayout 下滑
        val coordinatorAnim = ObjectAnimator.ofFloat(
            binding.coordinatorLayout,
            "translationY",
            0f,
            binding.coordinatorLayout.height.toFloat()
        ).apply {
            duration = COORDINATOR_ANIM_DURATION
            interpolator = AccelerateDecelerateInterpolator()
        }

        fcvAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onEnd?.invoke()
            }
        })

        // 同时开始动画
        fcvAnim.start()
        coordinatorAnim.start()
    }

    /**
     * 开始返回动画
     * 
     * 该方法执行页面返回时的动画效果：
     * 1. 协调器布局从底部回弹到原位置
     * 2. 使用弹性插值器创建回弹效果
     * 3. 动画结束后触发Fragment容器的返回动画
     */
    private fun startReturnAnimation() {
        // coordinatorLayout 回弹
        val coordinatorReturn = ObjectAnimator.ofFloat(
            binding.coordinatorLayout,
            "translationY",
            binding.coordinatorLayout.height.toFloat(),
            0f
        ).apply {
            duration = RETURN_ANIM_DURATION
            interpolator = OvershootInterpolator(1.2f) // 弹性效果
        }

        coordinatorReturn.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                startFcvReturnAnimation()
            }
        })

        coordinatorReturn.start()
    }

    /**
     * Fragment容器返回动画
     * 
     * 该方法执行Fragment容器的返回动画：
     * 1. Fragment容器从顶部回弹到原位置
     * 2. 使用弹性插值器创建回弹效果
     * 3. 与协调器布局的返回动画形成连贯的动画序列
     */
    private fun startFcvReturnAnimation() {
        // fcvNavigation 回弹
        ObjectAnimator.ofFloat(
            binding.fcvNavigation,
            "translationY",
            -binding.fcvNavigation.height.toFloat(),
            0f
        ).apply {
            duration = RETURN_ANIM_DURATION
            interpolator = OvershootInterpolator(1.2f)
            start()
        }
    }

    /**
     * 协调器退出动画
     * 
     * 该方法执行协调器布局的退出动画：
     * 1. 协调器布局向下滑动退出屏幕
     * 2. 使用加速减速插值器创建平滑效果
     * 
     * 注意：此方法目前未被使用，保留用于备用动画方案
     */
    private fun startCoordinatorExitAnimation() {
        ObjectAnimator.ofFloat(binding.coordinatorLayout, "translationY", 0f, binding.coordinatorLayout.height.toFloat()).apply {
            duration = COORDINATOR_ANIM_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }
//
//    private fun startReturnAnimation() {
//        ObjectAnimator.ofFloat(binding.coordinatorLayout, "translationY", binding.coordinatorLayout.height.toFloat(), 0f).apply {
//            duration = RETURN_ANIM_DURATION
//            interpolator = AccelerateDecelerateInterpolator()
//            addListener(object : AnimatorListenerAdapter() {
//                override fun onAnimationEnd(animation: Animator) {
//                    startFcvReturnAnimation()
//                }
//            })
//            start()
//        }
//    }
//
//    private fun startFcvReturnAnimation() {
//        ObjectAnimator.ofFloat(binding.fcvNavigation, "translationY", -binding.fcvNavigation.height.toFloat(), 0f).apply {
//            duration = RETURN_ANIM_DURATION
//            interpolator = AccelerateDecelerateInterpolator()
//            start()
//        }
//    }

    /**
     * 开始悬浮操作按钮缩放动画
     * 
     * 该方法为悬浮操作按钮创建缩放动画效果：
     * 1. 使用ValueAnimator在指定的缩放值之间进行动画
     * 2. 同时应用X轴和Y轴的缩放效果
     * 3. 使用加速减速插值器创建平滑的缩放效果
     * 
     * @param fromScale 起始缩放值
     * @param toScale 结束缩放值
     */
    private fun startFabScaleAnimation(fromScale: Float, toScale: Float) {
        ValueAnimator.ofFloat(fromScale, toScale).apply {
            duration = 300L  // FAB 缩放动画稍微慢一点
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val scale = animation.animatedValue as Float
                binding.fabNavigation.scaleX = scale
                binding.fabNavigation.scaleY = scale
            }
            start()
        }
    }

    /**
     * 将dp值转换为px值
     * 
     * 该方法根据设备的显示密度将dp单位转换为px单位：
     * 1. 获取设备的显示密度
     * 2. 执行dp到px的转换计算
     * 3. 四舍五入到最近的整数
     * 
     * @param dp 需要转换的dp值
     * @return 转换后的px值
     */
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density + 0.5f).toInt()

    /**
     * 显示错误信息
     * 
     * 该方法实现INavigationContract.INavigationView接口的showError方法：
     * 1. 当导航加载失败时显示Toast提示
     * 2. 提供用户友好的错误信息
     */
    override fun showError() {
        Toast.makeText(this, "加载导航失败，请重试", Toast.LENGTH_SHORT).show()
    }

    /**
     * Activity销毁时的清理工作
     * 
     * 该方法在Activity销毁时执行必要的清理：
     * 1. 调用父类的onDestroy方法
     * 2. 移除Handler中所有的回调和消息，防止内存泄漏
     */
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
