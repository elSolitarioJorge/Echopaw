package com.example.echopaw.home

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.echopaw.R
import com.example.echopaw.databinding.PageReleaseSuccessBinding
import com.example.echopaw.navigation.MainActivity

/**
 * 发布成功页面Fragment，对应布局page_release_success.xml
 * - 点击btn_back返回首页(MainActivity)
 */
class ReleaseSuccessFragment : Fragment() {

    private var _binding: PageReleaseSuccessBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("ReleaseSuccessFragment", "onCreateView called")
        _binding = PageReleaseSuccessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("ReleaseSuccessFragment", "onViewCreated called")

        binding.btnBack.setOnClickListener {
            Log.d("ReleaseSuccessFragment", "Back button clicked")
            // 直接返回首页，清除所有Fragment回退栈
            returnToHome()
        }

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            WindowInsetsCompat.CONSUMED
        }
    }

    /**
     * 返回首页
     */
    private fun returnToHome() {
        Log.d("ReleaseSuccessFragment", "returnToHome called")
        val currentActivity = requireActivity()


        // 如果当前 Activity 就是 MainActivity，直接回首页
        if (currentActivity is MainActivity) {
            Log.d("ReleaseSuccessFragment", "MainActivity found, returning to home")

            // 显示底部导航栏
            currentActivity.showBottomNavigation()
            Log.d("ReleaseSuccessFragment", "Bottom navigation shown")

            // 清除回退栈，这会移除ReleaseSuccessFragment并显示之前的Fragment
            parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            Log.d("ReleaseSuccessFragment", "Back stack popped")

            // 确保回到首页Tab
            currentActivity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bnv_navigation)?.selectedItemId = R.id.navigation_home
            Log.d("ReleaseSuccessFragment", "Home tab selected")
        } else {
            // 否则启动 MainActivity，并清除当前 Activity
            Log.d("ReleaseSuccessFragment", "Current activity is not MainActivity, starting MainActivity")

            val intent = Intent(currentActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            currentActivity.startActivity(intent)
            currentActivity.overridePendingTransition(0, 0) // 禁用切换动画
            currentActivity.finish()

            Log.d("ReleaseSuccessFragment", "Started MainActivity and finished current activity")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 不在这里显示底部导航栏，避免在Fragment切换时意外显示
        _binding = null
    }
}