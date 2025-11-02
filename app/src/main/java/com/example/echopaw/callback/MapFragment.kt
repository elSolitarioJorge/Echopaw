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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import com.example.echopaw.utils.ToastUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
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
import com.example.echopaw.network.AuthManager
import com.example.echopaw.network.AudioRecord
import com.example.echopaw.network.AudioDetail
import com.example.echopaw.network.RetrofitClient
import com.example.echopaw.service.LocationService
import com.example.echopaw.callback.MapViewModel
import com.example.echopaw.audio.AudioPlayerManager
import com.example.echopaw.audio.EnhancedAudioMarkerManager
import com.example.echopaw.utils.TimeUtils
import com.example.echopaw.utils.LocationUtils
import com.example.echopaw.utils.MapInteractionController
import com.example.echopaw.config.MapInteractionConfig
import com.example.echopaw.monitor.PerformanceMonitor
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import java.lang.Thread.sleep

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

    // ViewModel和音频相关
    private val mapViewModel: MapViewModel by viewModels()
    
    // 音频功能管理器
    /** 音频播放管理器 */
    private lateinit var audioPlayerManager: AudioPlayerManager
    
    /** 音频标记管理器 */
    private lateinit var audioMarkerManager: EnhancedAudioMarkerManager
    
    /** 地图交互控制器 */
    private lateinit var mapInteractionController: MapInteractionController
    
    /** 性能监控器 */
    private lateinit var performanceMonitor: PerformanceMonitor
    
    // 控制面板相关
    /** 音频播放控制面板 */
    private var audioControlPanel: View? = null
    
    /** 当前显示的音频详情 */
    private var currentAudioDetail: AudioDetail? = null
    
    /** 时间更新任务 */
    private var timeUpdateJob: Job? = null

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
    // private var bottomSheetBehavior: BottomSheetBehavior<*>? = null
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<RelativeLayout>
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
            Log.d(TAG, "Permission granted, starting location")
            showMsg("位置权限已授予")
            startLocation()
        } else {
            Log.d(TAG, "Permission denied")
            showMsg("位置权限被拒绝，无法获取附近音频")
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

        // 初始化性能监控器
        performanceMonitor = PerformanceMonitor(MapInteractionConfig.getInstance(requireContext()))

        // 调整布局以避免状态栏覆盖内容
        adjustForKeyboard()
        binding.mapView.onCreate(savedInstanceState)
        initLocation()
        initMap()
        initView()
        initSearch()
        initAudioManagers()
        initAudioControlPanel()
        observeAudioData()

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, 0)
            WindowInsetsCompat.CONSUMED
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
        // 开始监控定位初始化性能
        val timer = performanceMonitor.startTimer("location_initialization")
        
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
            
            // 结束定位初始化性能监控
            val duration = timer.stopAndRecord(performanceMonitor)
            Log.d(TAG, "Location initialization completed in ${duration}ms")
        } catch (e: Exception) {
            // 记录初始化失败
            timer.stopAndRecord(performanceMonitor)
            Log.e(TAG, "Location initialization failed", e)
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
            // 开始监控地图初始化性能
            val timer = performanceMonitor.startTimer("map_initialization")
            
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
            
            // 初始化地图交互控制器
            initMapInteractionController()
            
            // 结束地图初始化性能监控
            val duration = timer.stopAndRecord(performanceMonitor)
            Log.d(TAG, "Map initialization completed in ${duration}ms")
        }
    }
    
    /**
     * 初始化地图交互控制器
     * 
     * 使用优化的地图交互控制器替换原有的简单相机变化监听器，
     * 提供防抖机制、距离阈值控制等优化功能
     */
    private fun initMapInteractionController() {
        // 获取配置实例
        val config = MapInteractionConfig.getInstance(requireContext())
        
        // 创建地图交互控制器
        mapInteractionController = MapInteractionController(config)
        
        // 设置地图交互监听器
        mapInteractionController.setMapInteractionListener(object : MapInteractionController.MapInteractionListener {
            override fun onMapPositionChanged(newPosition: CameraPosition, oldPosition: CameraPosition?) {
                // 地图位置发生有效变化时的回调
                Log.d(TAG, "Map position changed with optimization: ${newPosition.target.latitude}, ${newPosition.target.longitude}")
            }
            
            override fun onDataLoadRequired(position: CameraPosition) {
                // 防抖延迟结束，需要加载数据时的回调
                Log.d(TAG, "Data load required after debounce: ${position.target.latitude}, ${position.target.longitude}")
                if (AuthManager.isLoggedIn()) {
                    lifecycleScope.launch {
                        mapViewModel.loadNearbyAudioRecords(
                            position.target.latitude, 
                            position.target.longitude,
                            forceRefresh = false
                        )
                    }
                }
            }
        })
        
        // 设置地图相机变化监听器，使用优化的控制器处理
        aMap?.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(cameraPosition: CameraPosition?) {
                // 相机正在变化时，传递给控制器处理
                cameraPosition?.let { position ->
                    mapInteractionController.onCameraPositionChanged(position)
                }
            }
            
            override fun onCameraChangeFinish(cameraPosition: CameraPosition?) {
                // 相机变化完成时，也传递给控制器处理
                cameraPosition?.let { position ->
                    mapInteractionController.onCameraPositionChanged(position)
                }
            }
        })
        
        Log.d(TAG, "MapInteractionController initialized with config: ${config.getConfigSummary()}")
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
                        // 折叠状态
                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            binding.ibBack.visibility = View.GONE
                            // 显示底部导航
                            showBottomNavigation()
                        }
                        // 展开状态
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            binding.ibBack.visibility = View.GONE
                            // 显示底部导航
                            showBottomNavigation()
                            // Hide search layout when bottom sheet is expanded
                            if (isOpen) {
                                initClose()
                            }
                        }
                        // 拖动中
                        BottomSheetBehavior.STATE_DRAGGING -> {
                            // Handle dragging state if needed
                        }
                        // 动画过渡中
                        BottomSheetBehavior.STATE_SETTLING -> {
                            // Handle settling state if needed
                        }
                        // 已隐藏
                        BottomSheetBehavior.STATE_HIDDEN -> {
                            // 显示返回按钮
                            binding.ibBack.visibility = View.VISIBLE
                            // 隐藏底部导航
                            hideBottomNavigation()
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

        binding.ibBack.setOnClickListener {
            // 点击后展开底部面板
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
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
     * 初始化音频管理器
     * 
     * 该方法初始化音频播放和标记管理器：
     * 1. 创建音频播放管理器并设置生命周期观察
     * 2. 创建音频标记管理器并设置点击监听
     * 3. 设置播放状态回调
     */
    private fun initAudioManagers() {
        try {
            // 初始化音频播放管理器
            audioPlayerManager = AudioPlayerManager(requireContext(), lifecycleScope)
            lifecycle.addObserver(audioPlayerManager)
            
            // 设置播放状态监听
            audioPlayerManager.setPlaybackListener(object : AudioPlayerManager.PlaybackListener {
                override fun onPlaybackStarted(audioId: String) {
                    Log.d(TAG, "Audio playback started: $audioId")
                    audioMarkerManager.updateMarkerPlayingState(audioId, true)
                    updatePlayButtonState(true)
                }

                override fun onPlaybackPaused(audioId: String) {
                    Log.d(TAG, "Audio playback paused: $audioId")
                    audioMarkerManager.updateMarkerPlayingState(audioId, false)
                    updatePlayButtonState(false)
                }

                override fun onPlaybackCompleted(audioId: String) {
                    Log.d(TAG, "Audio playback completed: $audioId")
                    audioMarkerManager.updateMarkerPlayingState(audioId, false)
                    updatePlayButtonState(false)
                }

                override fun onPlaybackError(audioId: String, error: String) {
                    Log.e(TAG, "Audio playback error for $audioId: $error")
                    audioMarkerManager.updateMarkerPlayingState(audioId, false)
                    updatePlayButtonState(false)
                    showToast("播放失败: $error")
                }

                override fun onProgressUpdate(audioId: String, currentPosition: Int, duration: Int) {
                    // 可以在这里更新播放进度
                }

                override fun onBufferingUpdate(audioId: String, percent: Int) {
                    // 可以在这里更新缓冲进度
                }
            })
            
            // 初始化音频标记管理器
            initAudioMarkerManagerWhenMapReady()
            
            Log.d(TAG, "Audio managers initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing audio managers: ${e.message}", e)
            showToast("音频功能初始化失败")
        }
    }

    /**
     * 当地图准备就绪时初始化EnhancedAudioMarkerManager
     */
    private fun initAudioMarkerManagerWhenMapReady() {
        if (aMap != null) {
            Log.d(TAG, "Initializing EnhancedAudioMarkerManager with aMap")
            try {
                val config = MapInteractionConfig.getInstance(requireContext())
                audioMarkerManager = EnhancedAudioMarkerManager(requireContext(), aMap!!, config)
                audioMarkerManager.setupMapMarkerClickListener()
                
                // 设置标记点击监听
                audioMarkerManager.setMarkerClickListener(object : EnhancedAudioMarkerManager.MarkerClickListener {
                    override fun onMarkerClicked(audioRecord: AudioRecord, marker: Marker) {
                        Log.d(TAG, "Audio marker clicked: ${audioRecord.audioId}")
                        handleAudioMarkerClick(audioRecord)
                    }
                })
                 Log.d(TAG, "EnhancedAudioMarkerManager initialized successfully")
                 
                 // 如果已经有音频数据，立即添加标记
                 mapViewModel.audioRecordsWithLocation.value?.let { audioRecordsWithLocation ->
                     if (audioRecordsWithLocation.isNotEmpty()) {
                         Log.d(TAG, "Adding existing audio records to map: ${audioRecordsWithLocation.size} records")
                         audioMarkerManager.updateAudioMarkersIncremental(audioRecordsWithLocation)
                     }
                 }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing EnhancedAudioMarkerManager: ${e.message}", e)
                // 如果初始化失败，延迟重试
                Handler(Looper.getMainLooper()).postDelayed({
                    initAudioMarkerManagerWhenMapReady()
                }, 200)
            }
        } else {
            Log.d(TAG, "aMap not ready, retrying EnhancedAudioMarkerManager initialization in 100ms")
            // 如果aMap还没有准备好，延迟100ms后重试
            Handler(Looper.getMainLooper()).postDelayed({
                initAudioMarkerManagerWhenMapReady()
            }, 100)
        }
    }

    /**
     * 初始化音频控制面板
     * 
     * 该方法初始化音频播放控制面板的UI：
     * 1. 获取控制面板容器引用
     * 2. 动态加载控制面板布局
     * 3. 设置按钮点击监听器
     * 4. 初始化面板状态
     */
    private fun initAudioControlPanel() {
        try {
            // 获取控制面板容器
            val audioControlContainer = binding.root.findViewById<FrameLayout>(R.id.audio_control_container)
            
            audioControlContainer?.let { container ->
                // 动态加载控制面板布局
                val inflater = LayoutInflater.from(requireContext())
                audioControlPanel = inflater.inflate(R.layout.audio_player_control_panel, container, false)
                
                // 将控制面板添加到容器中
                container.addView(audioControlPanel)
                
                audioControlPanel?.let { panel ->
                    // 设置返回按钮点击监听
                    panel.findViewById<View>(R.id.btn_back)?.setOnClickListener {
                        hideAudioControlPanel()
                    }
                    
                    // 设置收起按钮点击监听
                    panel.findViewById<View>(R.id.btn_shou_qi)?.setOnClickListener {
                        hideAudioControlPanel()
                    }
                    
                    // 设置播放/暂停按钮点击监听
                    panel.findViewById<View>(R.id.btn_play)?.setOnClickListener {
                        handlePlayButtonClick()
                    }
                    
                    // 设置回复按钮点击监听
                    panel.findViewById<View>(R.id.btn_reply)?.setOnClickListener {
                        handleReplyButtonClick()
                    }
                    
                    // 初始状态为隐藏
                    panel.visibility = View.GONE
                }
            }
            
            Log.d(TAG, "Audio control panel initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing audio control panel: ${e.message}", e)
        }
    }

    /**
     * 观察音频数据变化
     * 
     * 该方法设置对ViewModel中音频数据的观察：
     * 1. 观察附近音频记录列表变化
     * 2. 观察加载状态变化
     * 3. 观察错误消息变化
     */
    private fun observeAudioData() {
        try {
            // 观察带有位置信息的音频记录
            mapViewModel.audioRecordsWithLocation.observe(viewLifecycleOwner) { audioRecordsWithLocation ->
                Log.d(TAG, "Audio records with location updated: ${audioRecordsWithLocation.size} records")
                if (::audioMarkerManager.isInitialized) {
                    // 使用增量更新方法，提高性能
                    audioMarkerManager.updateAudioMarkersIncremental(audioRecordsWithLocation)
                }
            }
            
            // 观察加载状态
            mapViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
                Log.d(TAG, "Loading state changed: $isLoading")
                // 可以在这里显示/隐藏加载指示器
            }
            
            // 观察错误消息
            mapViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
                errorMessage?.let { message ->
                    Log.e(TAG, "Audio data error: $message")
                    showToast(message)
                }
            }
            
            Log.d(TAG, "Audio data observation setup successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up audio data observation: ${e.message}", e)
        }
    }

    /**
     * 处理音频标记点击事件
     * 
     * 该方法在用户点击地图上的音频标记时被调用：
     * 1. 获取音频详情信息
     * 2. 显示音频控制面板
     * 3. 更新标记状态
     * 
     * @param audioRecord 被点击的音频记录
     */
    private fun handleAudioMarkerClick(audioRecord: AudioRecord) {
        try {
            Log.d(TAG, "Handling audio marker click for: ${audioRecord.audioId}")
            
            // 获取音频详情
            lifecycleScope.launch {
                try {
                    val response = RetrofitClient.apiService.getAudioDetail(audioRecord.audioId)
                    if (response.isSuccessful && response.body()?.code == 200) {
                        val audioDetail = response.body()?.data
                        audioDetail?.let { detail ->
                            currentAudioDetail = detail
                            showAudioControlPanel(detail)
                            
                            // 标记已被点击，显示音频控制面板
                        }
                    } else {
                        Log.e(TAG, "Failed to get audio detail: ${response.body()?.message}")
                        showToast("获取音频详情失败")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting audio detail: ${e.message}", e)
                    showToast("网络错误，请重试")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling audio marker click: ${e.message}", e)
            showToast("操作失败")
        }
    }

    /**
     * 显示音频控制面板
     * 
     * 该方法显示音频播放控制面板并填充数据：
     * 1. 设置音频详情信息
     * 2. 格式化时间显示
     * 3. 解析并显示位置信息
     * 4. 启动时间更新任务
     * 5. 隐藏其他UI元素（bottom_sheet_ray、ib_back、fabWorld）
     * 
     * @param audioDetail 音频详情数据
     */
    private fun showAudioControlPanel(audioDetail: AudioDetail) {
        try {
            audioControlPanel?.let { panel ->
                
                // 设置时间显示
                updateTimeDisplay(audioDetail)
                
                // 设置位置信息
                updateLocationDisplay(audioDetail)
                
                // 重置播放按钮状态
                updatePlayButtonState(false)
                
                // 隐藏其他UI元素
                hideOtherUIElements()
                
                // 显示面板
                panel.visibility = View.VISIBLE
                
                // 启动时间更新任务
                startTimeUpdateTask(audioDetail)
                
                Log.d(TAG, "Audio control panel shown successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing audio control panel: ${e.message}", e)
            showToast("显示控制面板失败")
        }
    }

    /**
     * 隐藏音频控制面板
     * 
     * 该方法隐藏音频播放控制面板并重置状态：
     * 1. 隐藏控制面板
     * 2. 停止音频播放
     * 3. 重置标记状态
     * 4. 停止时间更新任务
     * 5. 显示其他UI元素（bottom_sheet_ray、ib_back、fabWorld）
     */
    private fun hideAudioControlPanel() {
        try {
            audioControlPanel?.visibility = View.GONE
            
            // 停止音频播放
            if (::audioPlayerManager.isInitialized) {
                audioPlayerManager.stopAudio()
            }
            
            // 重置标记状态
            currentAudioDetail?.let { detail ->
                if (::audioMarkerManager.isInitialized) {
                    audioMarkerManager.updateMarkerPlayingState(detail.audioId, false)
                }
            }
            
            // 停止时间更新任务
            timeUpdateJob?.cancel()
            timeUpdateJob = null
            
            // 显示其他UI元素
            showOtherUIElements()
            
            // 清空当前音频详情
            currentAudioDetail = null
            
            Log.d(TAG, "Audio control panel hidden successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding audio control panel: ${e.message}", e)
        }
    }

    /**
     * 隐藏其他UI元素
     * 
     * 当音频控制面板显示时，隐藏以下UI元素：
     * 1. bottom_sheet_ray - 底部抽屉
     * 2. ib_back - 返回按钮
     * 3. fabWorld - 世界按钮
     */
    private fun hideOtherUIElements() {
        try {
            // 隐藏底部抽屉
            binding.bottomSheetRay.visibility = View.GONE

            // 隐藏底部导航栏
            hideBottomNavigation()

            // 隐藏返回按钮
            binding.ibBack.visibility = View.GONE

            // 隐藏世界按钮
            binding.fabWorld.visibility = View.GONE
            
            Log.d(TAG, "Other UI elements hidden successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding other UI elements: ${e.message}", e)
        }
    }

    /**
     * 显示其他UI元素
     * 
     * 当音频控制面板隐藏时，显示以下UI元素：
     * 1. bottom_sheet_ray - 底部抽屉
     * 2. ib_back - 返回按钮
     * 3. fabWorld - 世界按钮
     */
    private fun showOtherUIElements() {
        try {
            // 显示底部抽屉
            binding.bottomSheetRay.visibility = View.VISIBLE
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

            // 显示世界按钮
            binding.fabWorld?.visibility = View.VISIBLE

            Log.d(TAG, "Other UI elements shown successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing other UI elements: ${e.message}", e)
        }
    }

    /**
     * 处理播放按钮点击事件
     * 
     * 该方法处理播放/暂停按钮的点击：
     * 1. 检查当前播放状态
     * 2. 执行播放或暂停操作
     * 3. 更新按钮状态
     */
    private fun handlePlayButtonClick() {
        try {
            currentAudioDetail?.let { detail ->
                if (::audioPlayerManager.isInitialized) {
                    if (audioPlayerManager.isPlaying()) {
                        // 当前正在播放，执行暂停
                        audioPlayerManager.pauseAudio()
                        Log.d(TAG, "Audio playback paused")
                    } else {
                        // 当前未播放，开始播放
                        detail.audioUrl?.let { audioUrl ->
                            audioPlayerManager.playAudio(detail.audioId, audioUrl)
                            Log.d(TAG, "Audio playback started: ${detail.audioId}")
                        } ?: run {
                            showToast("音频文件不存在")
                            Log.e(TAG, "Audio URL is null for: ${detail.audioId}")
                        }
                    }
                } else {
                    showToast("音频播放器未初始化")
                    Log.e(TAG, "AudioPlayerManager not initialized")
                }
            } ?: run {
                showToast("音频详情不存在")
                Log.e(TAG, "Current audio detail is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling play button click: ${e.message}", e)
            showToast("播放操作失败")
        }
    }

    /**
     * 处理回复按钮点击事件
     * 
     * 该方法处理回复按钮的点击：
     * 1. 隐藏控制面板
     * 2. 可以在这里添加回复功能的逻辑
     */
    private fun handleReplyButtonClick() {
        try {
            Log.d(TAG, "Reply button clicked")
            hideAudioControlPanel()
            
            // 这里可以添加回复功能的逻辑
            // 例如：打开回复对话框或跳转到回复页面
            currentAudioDetail?.let { detail ->
                showToast("回复功能待实现")
                // TODO: 实现回复功能
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling reply button click: ${e.message}", e)
        }
    }

    /**
     * 更新播放按钮状态
     * 
     * 该方法根据播放状态更新播放按钮的显示：
     * 1. 更新按钮图标
     * 2. 更新按钮文本（如果有的话）
     * 
     * @param isPlaying 是否正在播放
     */
    private fun updatePlayButtonState(isPlaying: Boolean) {
        try {
            val playButton = audioControlPanel?.findViewById<ImageView>(R.id.btn_play)
            if (playButton != null) {
                // 根据播放状态设置不同的图标
                val iconRes = if (isPlaying) {
                    R.drawable.ic_pause // 播放时显示暂停图标
                } else {
                    R.drawable.ic_play // 暂停时显示播放图标
                }
                playButton.setImageResource(iconRes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating play button state: ${e.message}", e)
        }
    }

    /**
     * 更新时间显示
     * 
     * 该方法更新控制面板中的时间显示：
     * 1. 计算并显示相对时间（X分钟前）
     * 2. 格式化并显示发布时间
     * 
     * @param audioDetail 音频详情数据
     */
    private fun updateTimeDisplay(audioDetail: AudioDetail) {
        try {
            audioControlPanel?.let { panel ->
                // 更新相对时间显示
                panel.findViewById<TextView>(R.id.tv_time_ago)?.let { timeAgoView ->
                    val relativeTime = TimeUtils.getRelativeTime(audioDetail.timestamp)
                    timeAgoView.text = "发布于$relativeTime"
                }
                
                // 更新发布时间显示
                panel.findViewById<TextView>(R.id.tv_time_release)?.let { timeReleaseView ->
                    val formattedTime = TimeUtils.formatDisplayTime(audioDetail.timestamp)
                    timeReleaseView.text = formattedTime
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating time display: ${e.message}", e)
        }
    }

    /**
     * 更新位置信息显示
     * 
     * 该方法更新控制面板中的位置信息显示：
     * 1. 解析地理坐标
     * 2. 格式化为"省市•城市•区县"格式
     * 
     * @param audioDetail 音频详情数据
     */
    private fun updateLocationDisplay(audioDetail: AudioDetail) {
        try {
            audioControlPanel?.findViewById<TextView>(R.id.tv_location)?.let { locationView ->
                val locationParts = listOfNotNull(
                    audioDetail.province.takeIf { it.isNotEmpty() },
                    audioDetail.city.takeIf { it.isNotEmpty() },
                    audioDetail.district.takeIf { it.isNotEmpty() }
                )

                if (locationParts.isNotEmpty()) {
                    locationView.text = locationParts.joinToString("•")
                } else {
                    // 最后尝试使用经纬度解析地址
                    lifecycleScope.launch {
                        try {
                            val address = LocationUtils.resolveAddress(
                                requireContext(),
                                audioDetail.location.lat,
                                audioDetail.location.lon
                            )
                            locationView.text = address
                        } catch (e: Exception) {
                            Log.e(TAG, "Error resolving address: ${e.message}", e)
                            locationView.text = "位置信息不可用"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating location display: ${e.message}", e)
        }
    }

    /**
     * 启动时间更新任务
     * 
     * 该方法启动定时任务来更新时间显示：
     * 1. 取消之前的更新任务
     * 2. 启动新的定时更新任务
     * 3. 根据时间间隔动态调整更新频率
     * 
     * @param audioDetail 音频详情数据
     */
    private fun startTimeUpdateTask(audioDetail: AudioDetail) {
        try {
            // 取消之前的任务
            timeUpdateJob?.cancel()
            
            // 启动新的更新任务
            timeUpdateJob = lifecycleScope.launch {
                while (isActive) {
                    try {
                        // 检查是否需要更新
                        updateTimeDisplay(audioDetail)
                        
                        // 获取下次更新的延迟时间，添加额外的timestamp验证
                        val timestamp = audioDetail.timestamp?.takeIf { it.isNotBlank() } ?: ""
                        val delayMs = TimeUtils.getNextUpdateDelay(TimeUtils.parseTimeString(timestamp))
                         delay(delayMs)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in time update task: ${e.message}", e)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting time update task: ${e.message}", e)
        }
    }

    /**
     * 显示Toast消息
     * 
     * @param message 要显示的消息
     */
    private fun showToast(message: String) {
        try {
            ToastUtils.showInfo(requireContext(), message)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing toast: ${e.message}", e)
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
        ToastUtils.showInfo(requireContext(), message.toString())
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
        Log.d(TAG, "onLocationChanged called")
        
        // 开始监控定位处理性能
        val timer = performanceMonitor.startTimer("location_processing")
        
        aMapLocation ?: run {
            Log.e(TAG, "Location failed, aMapLocation is null")
            showMsg("Location failed, aMapLocation is null")
            timer.stopAndRecord(performanceMonitor)
            return
        }

        if (aMapLocation.errorCode == 0) {
            Log.d(TAG, "Location success: lat=${aMapLocation.latitude}, lng=${aMapLocation.longitude}, city=${aMapLocation.city}")
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
            
            // 加载附近的音频记录
            Log.d(TAG, "Calling loadNearbyAudioRecords from onLocationChanged")
            loadNearbyAudioRecords(aMapLocation.latitude, aMapLocation.longitude)
            
            // 结束定位处理性能监控
            val duration = timer.stopAndRecord(performanceMonitor)
            Log.d(TAG, "Location processing completed in ${duration}ms")
        } else {
            Log.e(TAG, "Location failed, ErrCode:${aMapLocation.errorCode}, errInfo:${aMapLocation.errorInfo}")
            showMsg("Location failed, error: ${aMapLocation.errorInfo}")
            timer.stopAndRecord(performanceMonitor)
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
     * 4. 隐藏底部导航栏，提供沉浸式体验
     * 
     * @param p0 长按位置的经纬度坐标
     */
    override fun onMapLongClick(p0: LatLng?) {
        if (p0 != null) {
            latLonToAddress(p0)
            updateMapmark(p0)
            // 长按地图时隐藏底部导航栏，提供沉浸式体验
            hideBottomNavigation()
            // 隐藏底部面板
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            
            // 长按时测试音频加载功能（调试用）
            Log.d(TAG, "Long click detected, testing audio loading at: ${p0.latitude}, ${p0.longitude}")
            if (AuthManager.isLoggedIn()) {
                lifecycleScope.launch {
                    mapViewModel.loadNearbyAudioRecords(p0.latitude, p0.longitude, forceRefresh = true)
                }
            }
        }
    }

    /**
     * 地图点击事件回调
     * 
     * 该方法是AMap.OnMapClickListener接口的实现：
     * 1. 处理用户点击地图的事件
     * 2. 将经纬度坐标转换为地址信息
     * 3. 在地图上更新标记位置
     * 4. 隐藏底部导航栏，方便用户导航
     * 
     * @param p0 点击位置的经纬度坐标
     */
    override fun onMapClick(p0: LatLng?) {
        if (p0 != null) {
            latLonToAddress(p0)
            updateMapmark(p0)
            // 点击地图时显示底部导航栏，方便用户导航
            hideBottomNavigation()
            // 隐藏底部面板
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
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
            // showMsg("Failed to get address, rCode = $rCode")
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
        
        // 清理地图交互控制器资源
        if (::mapInteractionController.isInitialized) {
            mapInteractionController.cleanup()
        }
        
        binding.mapView.onDestroy()
        _binding = null
    }

    /**
     * 显示底部导航栏
     * 
     * 该方法用于显示MainActivity中的coordinatorLayout（底部导航栏）：
     * 1. 获取MainActivity实例
     * 2. 调用MainActivity的showBottomNavigation方法
     * 3. 安全地处理类型转换，避免ClassCastException
     * 
     * 使用场景：
     * - 当需要在地图页面显示底部导航时
     * - 从全屏模式返回正常模式时
     */
    fun showBottomNavigation() {
        try {
            val mainActivity = requireActivity() as? com.example.echopaw.navigation.MainActivity
            mainActivity?.showBottomNavigation()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing bottom navigation: ${e.message}")
        }
    }

    /**
     * 隐藏底部导航栏
     * 
     * 该方法用于隐藏MainActivity中的coordinatorLayout（底部导航栏）：
     * 1. 获取MainActivity实例
     * 2. 调用MainActivity的hideBottomNavigation方法
     * 3. 安全地处理类型转换，避免ClassCastException
     * 
     * 使用场景：
     * - 当需要全屏显示地图时
     * - 提供沉浸式地图浏览体验时
     */
    fun hideBottomNavigation() {
        try {
            val mainActivity = requireActivity() as? com.example.echopaw.navigation.MainActivity
            mainActivity?.hideBottomNavigation()
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding bottom navigation: ${e.message}")
        }
    }

    /**
     * 切换底部导航栏可见性
     * 
     * 该方法用于切换MainActivity中coordinatorLayout的可见性：
     * 1. 获取MainActivity实例
     * 2. 调用MainActivity的toggleBottomNavigation方法
     * 3. 返回切换后的可见性状态
     * 4. 安全地处理类型转换和异常情况
     * 
     * @return true表示切换后为可见状态，false表示切换后为隐藏状态，null表示操作失败
     * 
     * 使用场景：
     * - 双击地图切换导航栏显示状态
     * - 提供快捷的显示/隐藏切换功能
     */
    fun toggleBottomNavigation(): Boolean? {
        return try {
            val mainActivity = requireActivity() as? com.example.echopaw.navigation.MainActivity
            mainActivity?.toggleBottomNavigation()
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling bottom navigation: ${e.message}")
            null
        }
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



    /**
     * 加载附近的音频记录
     * 
     * 该方法在获取到用户位置后调用，用于加载附近的音频记录：
     * 1. 检查用户是否已登录
     * 2. 设置当前位置到ViewModel
     * 3. 触发加载附近音频记录的请求
     * 4. 设置搜索半径（默认1000米）
     * 
     * @param latitude 当前位置的纬度
     * @param longitude 当前位置的经度
     */
    private fun loadNearbyAudioRecords(latitude: Double, longitude: Double) {
        Log.d(TAG, "loadNearbyAudioRecords called with lat: $latitude, lng: $longitude")
        
        Log.d(TAG, "Checking user login status...")
        
        // 确保AuthManager已初始化
        try {
            AuthManager.init(requireContext())
            Log.d(TAG, "AuthManager initialized successfully")
            
            val accessToken = AuthManager.getAccessToken()
            Log.d(TAG, "Access token: ${if (accessToken != null) "exists (${accessToken.take(20)}...)" else "null"}")
            
            val isLoggedIn = AuthManager.isLoggedIn()
            Log.d(TAG, "User login status: $isLoggedIn")
            
            if (!isLoggedIn) {
                Log.w(TAG, "User not logged in, skipping audio records loading")
                showMsg("请先登录以获取附近音频")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking auth status: ${e.message}", e)
            showMsg("认证检查失败")
            return
        }
        
        Log.d(TAG, "User is logged in, proceeding with audio records loading")
        
        // 设置当前位置
        val locationResult = LocationService.LocationResult(
            latitude = latitude,
            longitude = longitude,
            accuracy = 0f,
            address = null,
            errorCode = 0,
            errorInfo = null
        )
        mapViewModel.setCurrentLocation(locationResult)
        
        // 设置搜索半径（1000米）
        mapViewModel.setSearchRadius(1000.0)
        
        Log.d(TAG, "Starting to load nearby audio records...")
        
        // 加载附近的音频记录
        lifecycleScope.launch {
            mapViewModel.loadNearbyAudioRecords(latitude, longitude)
        }
    }

}