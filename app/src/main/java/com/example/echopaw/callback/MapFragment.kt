package com.example.echopaw.callback

import android.Manifest
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.LocationSource
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.geocoder.GeocodeQuery
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import com.example.echopaw.R
import com.example.echopaw.databinding.FragmentMapBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout

/**
 * 地图Fragment
 * 
 * 该Fragment提供完整的地图功能，包括：
 * 1. 高德地图显示和交互
 * 2. 定位服务和位置监听
 * 3. 地址搜索和地理编码
 * 4. POI搜索和标记显示
 * 5. 地图点击和长按事件处理
 * 6. 底部搜索面板的展开/收起动画
 * 7. 权限管理和状态栏适配
 * 
 * 实现的接口：
 * - AMapLocationListener: 处理定位回调
 * - LocationSource: 提供定位数据源
 * - PoiSearch.OnPoiSearchListener: 处理POI搜索结果
 * - AMap.OnMapLongClickListener: 处理地图长按事件
 * - AMap.OnMapClickListener: 处理地图点击事件
 * - GeocodeSearch.OnGeocodeSearchListener: 处理地理编码搜索结果
 */
class MapFragment : Fragment(), AMapLocationListener, LocationSource,
    PoiSearch.OnPoiSearchListener, AMap.OnMapLongClickListener,
    AMap.OnMapClickListener, GeocodeSearch.OnGeocodeSearchListener {

    /** ViewBinding实例，用于安全的视图访问 */
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    
    /** 日志标签 */
    private val TAG = "MapFragment"

    // Location related
    /** 高德定位客户端 */
    private var mLocationClient: AMapLocationClient? = null
    
    /** 定位配置选项 */
    private var mLocationOption: AMapLocationClientOption? = null
    
    /** 高德地图实例 */
    private var aMap: AMap? = null
    
    /** 位置变化监听器 */
    private var mListener: LocationSource.OnLocationChangedListener? = null

    // Search related
    /** 地理编码搜索实例 */
    private var geocodeSearch: GeocodeSearch? = null
    
    /** 解析成功状态码 */
    private val PARSE_SUCCESS_CODE = 1000
    
    /** 当前城市名称 */
    private var city: String? = null
    
    /** 城市编码 */
    private var cityCode: String? = null
    
    /** 地图标记列表 */
    private val markerList = mutableListOf<Marker>()
    
    /** 行政区域代码 */
    private var adcode: String? = null
    
    /** 当前位置描述 */
    private var location: String? = null
    
    /** 地址列表 */
    private var addresses: List<String> = emptyList()

    // UI related
    /** 自动过渡动画 */
    private var autoTransition: AutoTransition? = null
    
    /** 展开显示动画 */
    private var bigShowAnim: Animation? = null
    
    /** 收起隐藏动画 */
    private var smallHideAnim: Animation? = null
    
    /** 屏幕宽度 */
    private var width = 0
    
    /** 搜索面板是否展开 */
    private var isOpen = false
    
    /** 底部面板行为控制器 */
    private var bottomSheetBehavior: BottomSheetBehavior<*>? = null

    /**
     * 位置数据更新监听接口
     * 
     * 用于向外部组件传递位置和地址选择事件
     */
    interface OnLocationDataListener {
        /**
         * 位置数据更新回调
         * 
         * @param location 位置描述
         * @param city 城市名称
         * @param adcode 行政区域代码
         */
        fun onLocationDataUpdated(location: String, city: String, adcode: String)
        
        /**
         * 地址选择回调
         * 
         * @param address 选择的地址
         * @param latLng 对应的经纬度
         */
        fun onAddressSelected(address: String, latLng: LatLng)
    }

    /** 位置数据监听器，用于向外部传递位置和地址信息 */
    private var locationDataListener: OnLocationDataListener? = null

    /**
     * 权限请求结果处理器
     * 
     * 使用ActivityResultContracts处理定位权限请求结果：
     * 1. 权限授予时显示成功消息
     * 2. 权限拒绝时显示拒绝消息
     * 3. 记录权限请求结果到日志
     */
    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        Log.d(TAG, "Permission request result: $result")
        if (result) {
            Log.d(TAG, "Permission granted")
            showMsg("Permission granted")
        } else {
            Log.d(TAG, "Permission denied")
            showMsg("Permission denied")
        }
    }

    /**
     * 创建Fragment视图
     * 
     * 该方法负责初始化Fragment的布局：
     * 1. 使用ViewBinding创建布局实例
     * 2. 返回根视图供Fragment使用
     * 
     * @param inflater 布局填充器
     * @param container 父容器
     * @param savedInstanceState 保存的实例状态
     * @return Fragment的根视图
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * 视图创建完成回调
     * 
     * 该方法在视图创建完成后进行初始化：
     * 1. 设置状态栏透明
     * 2. 调整键盘适配
     * 3. 初始化定位服务
     * 4. 初始化地图组件
     * 5. 初始化UI界面
     * 6. 初始化搜索功能
     * 
     * @param view 创建的视图
     * @param savedInstanceState 保存的实例状态
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 设置状态栏透明
        makeStatusBarTransparent()

        // 调整布局以避免状态栏覆盖内容
        adjustForKeyboard()
        binding.mapView.onCreate(savedInstanceState)
        initLocation()
        initMap()
        initView()
        initSearch()

        ViewCompat.setOnApplyWindowInsetsListener(binding.map) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

    }

    /**
     * 设置状态栏透明
     * 
     * 该方法根据Android版本设置状态栏透明效果：
     * 1. Android 5.0+：使用新的状态栏API设置透明
     * 2. Android 4.4+：使用半透明状态栏
     * 3. 设置全屏布局标志，让内容延伸到状态栏下方
     */
    private fun makeStatusBarTransparent() {
        activity?.window?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                statusBarColor = Color.TRANSPARENT
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            }
        }
    }

    /**
     * 调整键盘适配
     * 
     * 该方法设置窗口插入监听器来处理系统栏：
     * 1. 监听系统栏的插入变化
     * 2. 调整根视图的顶部内边距
     * 3. 确保内容不被状态栏遮挡
     */
    private fun adjustForKeyboard() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, 0)
            insets
        }
    }

    /**
     * 初始化定位服务
     * 
     * 该方法配置高德定位客户端：
     * 1. 创建AMapLocationClient实例
     * 2. 设置定位监听器为当前Fragment
     * 3. 配置定位选项：高精度模式、需要地址信息、超时时间等
     * 4. 应用定位配置
     * 
     * @throws RuntimeException 当定位初始化失败时抛出异常
     */
    private fun initLocation() {
        try {
            mLocationClient = AMapLocationClient(requireContext())
            mLocationClient?.setLocationListener(this)
            mLocationOption = AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                isOnceLocationLatest = true
                isNeedAddress = true
                httpTimeOut = 6000
            }
            mLocationClient?.setLocationOption(mLocationOption)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    /**
     * 初始化地图
     * 
     * 该方法配置高德地图实例：
     * 1. 检查地图是否已初始化，避免重复初始化
     * 2. 设置夜间地图类型
     * 3. 配置定位数据源和我的位置显示
     * 4. 设置默认缩放级别和室内地图显示
     * 5. 注册地图点击和长按事件监听器
     * 6. 配置UI设置：禁用缩放控件，启用比例尺
     */
    private fun initMap() {
        if (aMap == null) {
            aMap = binding.mapView.map.apply {
                mapType = AMap.MAP_TYPE_NIGHT
                setLocationSource(this@MapFragment)
                isMyLocationEnabled = true
                moveCamera(CameraUpdateFactory.zoomTo(15f))
                showIndoorMap(true)

                setOnMapClickListener(this@MapFragment)
                setOnMapLongClickListener(this@MapFragment)

                uiSettings.apply {
                    isZoomControlsEnabled = false
                    isScaleControlsEnabled = true
                }
            }
        }
    }

    /**
     * 初始化视图组件
     * 
     * 该方法初始化UI相关的组件：
     * 1. 获取屏幕尺寸信息
     * 2. 加载展开和收起动画
     * 3. 设置搜索框的文本变化监听
     * 4. 配置底部面板的行为
     * 5. 设置各种点击事件监听器
     */
    private fun initView() {
        val metrics = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(metrics)
        width = metrics.widthPixels
        bigShowAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.scale_big_expand)
        smallHideAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.scale_small_close)
        // 在 onViewCreated 或 onCreate 中
        binding.fabWorld.setOnClickListener {
//                showMsg("世界地图")
            // 方式2：带动画的跳转（推荐）
            val intent = Intent(requireContext(), WorldActivity::class.java).apply {
                putExtra("key_origin", "map_fragment")  // 可传递参数
            }
            startActivity(intent)
            requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        binding.tabLayout.apply {
            // 首先清除XML中定义的Tab
            removeAllTabs()
            // 设置Tab文本样式
            setTabTextColors(ContextCompat.getColor(context, R.color.home_gray),
                ContextCompat.getColor(context, R.color.white))

            // 设置指示条宽度（约为文本宽度的60%）
            post {
                for (i in 0 until tabCount) {
                    val tab = getTabAt(i)
                    val tabView = (tab?.view as? LinearLayout)?.getChildAt(0) as? TextView
                    tabView?.let {
                        val width = it.paint.measureText(it.text.toString())
                        val params = it.layoutParams as ViewGroup.MarginLayoutParams
                        params.width = (width * 0.6).toInt()
                        it.layoutParams = params
                    }
                }
            }
            // 为每个 Tab 设置自定义视图（确保是 TextView）
            repeat(3) { position ->
                val tab = newTab()
                val textView = TextView(context).apply {
                    text = when (position) {
                        0 -> "附近"
                        1 -> "热门"
                        else -> "最新"
                    }
                    setTextColor(ContextCompat.getColor(context, R.color.home_gray))
                    textSize = 14f
                    gravity = Gravity.CENTER
                }
                tab.customView = textView
                addTab(tab)
            }

            // 添加监听器（现在可以安全转换，因为 customView 是 TextView）
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    (tab.customView as? TextView)?.apply {
                        setTextColor(ContextCompat.getColor(context, R.color.white))
                        typeface = Typeface.DEFAULT_BOLD
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {
                    (tab.customView as? TextView)?.apply {
                        setTextColor(ContextCompat.getColor(context, R.color.home_gray))
                        typeface = Typeface.DEFAULT
                    }
                }

                override fun onTabReselected(tab: TabLayout.Tab) {}
            })
        }

        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheetRay).apply {
            isHideable = true
            state = BottomSheetBehavior.STATE_COLLAPSED
            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_COLLAPSED -> {

                        }
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            // Hide search layout when bottom sheet is expanded
                            if (isOpen) {
                                initClose()
                            }
                        }
                        BottomSheetBehavior.STATE_DRAGGING -> {
                            // Handle dragging state if needed
                        }
                        BottomSheetBehavior.STATE_SETTLING -> {
                            // Handle settling state if needed
                        }
                        BottomSheetBehavior.STATE_HIDDEN -> {
                            // Handle hidden state if needed
                        }
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    // Handle slide events if needed
                }
            })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                binding.bottomSheetRay.elevation = 12f  // 确保高于地图
            }
        }
    }

    /**
     * 初始化搜索功能
     * 
     * 该方法初始化高德地图的地理编码搜索服务：
     * 1. 创建GeocodeSearch实例
     * 2. 设置地理编码搜索监听器为当前Fragment
     * 3. 处理初始化过程中可能出现的异常
     * 
     * @throws AMapException 当地图服务初始化失败时抛出异常
     */
    private fun initSearch() {
        try {
            geocodeSearch = GeocodeSearch(requireContext()).apply {
                setOnGeocodeSearchListener(this@MapFragment)
            }
        } catch (e: AMapException) {
            e.printStackTrace()
        }
    }

    /**
     * 将dp值转换为px值
     * 
     * 该方法根据设备屏幕密度将dp（密度无关像素）转换为px（像素）：
     * 1. 获取屏幕密度比例
     * 2. 计算转换后的像素值
     * 3. 四舍五入并返回整数值
     * 
     * @param dpVale 要转换的dp值
     * @return 转换后的px值
     */
    private fun dip2px(dpVale: Float): Int {
        val scale = resources.displayMetrics.density
        return (dpVale * scale + 0.5f).toInt()
    }

    /**
     * 将px值转换为dp值
     * 
     * 该方法根据设备屏幕密度将px（像素）转换为dp（密度无关像素）：
     * 1. 获取屏幕密度比例
     * 2. 计算转换后的dp值
     * 3. 四舍五入并返回整数值
     * 
     * @param pxValue 要转换的px值
     * @return 转换后的dp值
     */
    private fun px2dip(pxValue: Float): Int {
        val scale = requireContext().resources.displayMetrics.density
        return (pxValue / scale + 0.5f).toInt()
    }

    /**
     * 开始延迟过渡动画
     * 
     * 该方法为指定的ViewGroup设置自动过渡动画：
     * 1. 创建AutoTransition实例并设置持续时间
     * 2. 使用TransitionManager开始延迟过渡
     * 3. 适用于Android 4.4及以上版本
     * 
     * @param view 要应用过渡动画的ViewGroup
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private fun beginDelayedTransition(view: ViewGroup) {
        autoTransition = AutoTransition().apply {
            duration = 200
        }
        TransitionManager.beginDelayedTransition(view, autoTransition)
    }

    /**
     * 展开搜索界面
     * 
     * 该方法展开搜索相关的UI组件：
     * 1. 设置展开状态标志为true
     * 2. 显示搜索输入框和关闭按钮
     * 3. 调整搜索布局的宽度和边距
     * 4. 应用过渡动画效果
     * 
     * 注意：部分代码被注释，可能用于未来的功能扩展
     */
    private fun initExpand() {
        if (context != null) {
            isOpen = true
//            binding.edSearch.visibility = View.VISIBLE
//            binding.ivClose.visibility = View.VISIBLE
//
//            (binding.laySearch.layoutParams as? LinearLayout.LayoutParams)?.apply {
//                // Convert width from pixels to dips, subtract 24 dips, then back to pixels
//                val currentWidthDips = px2dip(width.toFloat())
//                val newWidthDips = currentWidthDips - 24f  // Make sure we subtract a Float
//                width = dip2px(newWidthDips)
//
//                setMargins(0, 0, 0, 0)
//            }

//            binding.laySearch.setPadding(14, 0, 14, 0)
//            beginDelayedTransition(binding.laySearch)
        }
    }

    /**
     * 收起搜索界面
     * 
     * 该方法收起搜索相关的UI组件：
     * 1. 设置展开状态标志为false
     * 2. 隐藏搜索输入框和关闭按钮
     * 3. 恢复搜索布局的原始宽度和边距
     * 4. 应用过渡动画效果
     * 5. 隐藏软键盘
     */
    private fun initClose() {
        if (context != null) {
            isOpen = false
//            binding.edSearch.visibility = View.GONE
//            binding.edSearch.text?.clear()
//            binding.ivClose.visibility = View.GONE
//            (binding.laySearch.layoutParams as? LinearLayout.LayoutParams)?.apply {
//                width = dip2px(48f)
//                height = dip2px(48f)
//                setMargins(0, 0, 0, 0)
//            }
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(requireActivity().window.decorView.windowToken, 0)
//            beginDelayedTransition(binding.laySearch)
        }
    }

    /**
     * 执行地址搜索
     * 
     * 该方法处理用户输入的地址搜索请求：
     * 1. 检查输入地址是否为空
     * 2. 如果为空，显示提示信息
     * 3. 如果不为空，隐藏软键盘并执行地理编码搜索
     * 4. 使用高德地图的GeocodeSearch服务进行异步搜索
     * 
     * @param address 要搜索的地址字符串
     */
    private fun performSearch(address: String) {
        if (address.isEmpty()) {
            showMsg("Please enter an address")
        } else {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(requireActivity().window.decorView.windowToken, 0)
            geocodeSearch?.getFromLocationNameAsyn(GeocodeQuery(address, null))
        }
    }

    /**
     * 开始定位
     * 
     * 该方法启动高德定位客户端开始定位：
     * 1. 调用AMapLocationClient的startLocation方法
     * 2. 开始获取用户当前位置信息
     * 3. 定位结果将通过onLocationChanged回调返回
     */
    private fun startLocation() {
        mLocationClient?.startLocation()
    }

    /**
     * 停止定位
     * 
     * 该方法停止高德定位客户端的定位服务：
     * 1. 调用AMapLocationClient的stopLocation方法
     * 2. 停止获取位置信息，节省电量和网络资源
     * 3. 通常在Fragment不可见或销毁时调用
     */
    private fun stopLocation() {
        mLocationClient?.stopLocation()
    }

    /**
     * 显示提示消息
     * 
     * 该方法使用Toast显示短暂的提示信息：
     * 1. 创建Toast实例
     * 2. 设置显示时长为短暂显示
     * 3. 在屏幕上显示消息
     * 
     * @param message 要显示的消息内容
     */
    private fun showMsg(message: CharSequence) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    /**
     * 位置变化回调
     * 
     * 该方法是AMapLocationListener接口的实现，处理定位结果：
     * 1. 检查定位结果是否为空
     * 2. 如果定位成功（errorCode为0），停止定位并处理结果
     * 3. 更新城市、行政区代码和位置坐标信息
     * 4. 通知位置数据监听器更新数据
     * 5. 如果定位失败，显示错误信息并记录日志
     * 
     * @param aMapLocation 高德定位结果对象，包含位置信息和错误码
     */
    override fun onLocationChanged(aMapLocation: AMapLocation?) {
        aMapLocation ?: run {
            showMsg("Location failed, aMapLocation is null")
            return
        }

        if (aMapLocation.errorCode == 0) {
            stopLocation()
            mListener?.onLocationChanged(aMapLocation)
            city = aMapLocation.city
            adcode = aMapLocation.adCode
            location = "%.2f,%.2f".format(aMapLocation.longitude, aMapLocation.latitude)

            // Notify listener about new location data
            locationDataListener?.onLocationDataUpdated(
                location ?: "",
                city ?: "",
                adcode ?: ""
            )
        } else {
            showMsg("Location failed, error: ${aMapLocation.errorInfo}")
            Log.e(TAG, "location Error, ErrCode:${aMapLocation.errorCode}, errInfo:${aMapLocation.errorInfo}")
        }
    }

    /**
     * 激活定位源
     * 
     * 该方法是LocationSource接口的实现，用于激活定位：
     * 1. 保存位置变化监听器
     * 2. 开始定位服务
     * 3. 通常在地图需要显示用户位置时调用
     * 
     * @param listener 位置变化监听器，用于接收定位结果
     */
    override fun activate(listener: LocationSource.OnLocationChangedListener?) {
        mListener = listener
        startLocation()
    }

    /**
     * 停用定位源
     * 
     * 该方法是LocationSource接口的实现，用于停用定位：
     * 1. 清空位置变化监听器
     * 2. 停止定位服务
     * 3. 销毁定位客户端并释放资源
     * 4. 通常在地图不再需要定位时调用
     */
    override fun deactivate() {
        mListener = null
        mLocationClient?.stopLocation()
        mLocationClient?.onDestroy()
        mLocationClient = null
    }

    /**
     * POI搜索结果回调
     * 
     * 该方法是PoiSearch.OnPoiSearchListener接口的实现：
     * 1. 处理POI（兴趣点）搜索结果
     * 2. 遍历搜索到的POI列表
     * 3. 记录每个POI的标题和描述信息
     * 
     * @param poiResult POI搜索结果对象
     * @param i 搜索结果代码
     */
    override fun onPoiSearched(poiResult: PoiResult?, i: Int) {
        poiResult?.pois?.forEach {
            Log.d("MapFragment", " Title:${it.title} Snippet:${it.snippet}")
        }
    }

    /**
     * 地图长按事件回调
     * 
     * 该方法是AMap.OnMapLongClickListener接口的实现：
     * 1. 处理用户长按地图的事件
     * 2. 将经纬度坐标转换为地址信息
     * 3. 在地图上更新标记位置
     * 
     * @param p0 长按位置的经纬度坐标
     */
    override fun onMapLongClick(p0: LatLng?) {
        if (p0 != null) {
            latLonToAddress(p0)
            updateMapmark(p0)
        }
    }

    /**
     * 地图点击事件回调
     * 
     * 该方法是AMap.OnMapClickListener接口的实现：
     * 1. 处理用户点击地图的事件
     * 2. 将经纬度坐标转换为地址信息
     * 3. 在地图上更新标记位置
     * 
     * @param p0 点击位置的经纬度坐标
     */
    override fun onMapClick(p0: LatLng?) {
        if (p0 != null) {
            latLonToAddress(p0)
            updateMapmark(p0)
        }
    }

    /**
     * POI项目搜索回调
     * 
     * 该方法是PoiSearch.OnPoiSearchListener接口的实现：
     * 1. 处理单个POI项目的搜索结果
     * 2. 目前尚未实现具体功能
     * 
     * @param p0 搜索到的POI项目
     * @param p1 搜索结果代码
     */
    override fun onPoiItemSearched(p0: PoiItem?, p1: Int) {
        TODO("Not yet implemented")
    }

    /**
     * 经纬度转地址
     * 
     * 该方法将经纬度坐标转换为地址信息：
     * 1. 格式化并保存位置坐标字符串
     * 2. 创建逆地理编码查询请求
     * 3. 执行异步逆地理编码搜索
     * 4. 搜索结果将通过onRegeocodeSearched回调返回
     * 
     * @param latLng 要转换的经纬度坐标
     */
    private fun latLonToAddress(latLng: LatLng) {
        location = "${latLng.longitude},${latLng.latitude}"
        geocodeSearch?.getFromLocationAsyn(
            RegeocodeQuery(
                LatLonPoint(latLng.latitude, latLng.longitude),
                20f,
                GeocodeSearch.AMAP
            )
        )
    }


    /**
     * 更新地图中心位置
     * 
     * 该方法用于更新地图的中心位置：
     * 1. 使用动画方式移动地图相机到指定位置
     * 2. 设置缩放级别为16，倾斜角度为30度
     * 3. 提供平滑的地图移动体验
     * 
     * @param latLng 要移动到的目标经纬度坐标
     */
    private fun updateMapCenter(latLng: LatLng) {
        aMap?.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition(latLng, 16f, 30f, 0f)
            )
        )
    }

    /**
     * 更新地图标记
     * 
     * 该方法用于在地图上更新标记位置：
     * 1. 清除地图上的所有现有标记
     * 2. 在指定位置添加新的标记
     * 3. 更新地图中心位置到新标记处
     * 
     * @param latLng 标记的经纬度坐标
     */
    private fun updateMapmark(latLng: LatLng) {
        aMap?.clear()
        aMap?.addMarker(MarkerOptions().position(latLng).snippet("DefaultMarker"))
        updateMapCenter(latLng)
    }

    /**
     * 逆地理编码搜索结果回调
     * 
     * 该方法是GeocodeSearch.OnGeocodeSearchListener接口的实现：
     * 1. 处理逆地理编码（坐标转地址）的搜索结果
     * 2. 检查搜索结果状态码
     * 3. 成功时显示地址信息并通知监听器
     * 4. 失败时显示错误信息
     * 
     * @param result 逆地理编码搜索结果
     * @param rCode 搜索结果状态码
     */
    override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
        Log.e("GeoSearch", "rCode = $rCode")
        if (rCode == PARSE_SUCCESS_CODE) {
            result?.regeocodeAddress?.let { address ->
                val query = result.regeocodeQuery
                val point = query.point
                showMsg("Address: ${address.formatAddress}")
                Log.e("GeoSearch", "rCode = $rCode")
                locationDataListener?.onAddressSelected(
                    address.formatAddress ?: "",
                    LatLng(point.latitude, point.longitude)
                )
            }
        } else {
            showMsg("Failed to get address, rCode = $rCode")
        }
    }

    /**
     * 地理编码搜索结果回调
     * 
     * 该方法是GeocodeSearch.OnGeocodeSearchListener接口的实现：
     * 1. 处理地理编码（地址转坐标）的搜索结果
     * 2. 检查搜索结果状态码
     * 3. 成功时更新位置信息和地图标记
     * 4. 失败时显示错误信息
     * 
     * @param geocodeResult 地理编码搜索结果
     * @param rCode 搜索结果状态码
     */
    override fun onGeocodeSearched(geocodeResult: GeocodeResult?, rCode: Int) {
        if (rCode != PARSE_SUCCESS_CODE) {
            showMsg("Failed to get coordinates")
            return
        }

        geocodeResult?.geocodeAddressList?.firstOrNull()?.let {
            val latLonPoint = it.latLonPoint
            location = "${latLonPoint.longitude},${latLonPoint.latitude}"
            adcode = it.adcode

            // Update map marker
            updateMapmark(LatLng(latLonPoint.latitude, latLonPoint.longitude))
            if (isOpen) initClose()
        }
    }

    /**
     * Fragment恢复时的生命周期回调
     * 
     * 该方法在Fragment恢复时执行：
     * 1. 调用父类的onResume方法
     * 2. 恢复地图视图
     * 3. 检查位置权限状态
     * 4. 有权限时启动定位，无权限时请求权限
     */
    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "onResume: Permission already granted")
            startLocation()
        } else {
            requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /**
     * Fragment暂停时的生命周期回调
     * 
     * 该方法在Fragment暂停时执行：
     * 1. 调用父类的onPause方法
     * 2. 暂停地图视图以节省资源
     */
    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    /**
     * 保存Fragment状态的生命周期回调
     * 
     * 该方法在需要保存Fragment状态时执行：
     * 1. 调用父类的onSaveInstanceState方法
     * 2. 保存地图视图的状态信息
     * 
     * @param outState 用于保存状态的Bundle对象
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }

    /**
     * Fragment视图销毁时的生命周期回调
     * 
     * 该方法在Fragment视图销毁时执行：
     * 1. 调用父类的onDestroyView方法
     * 2. 销毁地图视图以释放资源
     * 3. 清空视图绑定引用防止内存泄漏
     */
    override fun onDestroyView() {
        super.onDestroyView()
        binding.mapView.onDestroy()
        _binding = null
    }

    /**
     * 设置位置数据监听器
     * 
     * 该方法用于设置位置数据变化的监听器：
     * 1. 允许外部组件监听位置数据更新
     * 2. 支持地址选择事件的回调
     * 3. 实现Fragment与其他组件的数据通信
     * 
     * @param listener 位置数据监听器实例
     */
    fun setOnLocationDataListener(listener: OnLocationDataListener) {
        this.locationDataListener = listener
    }

}