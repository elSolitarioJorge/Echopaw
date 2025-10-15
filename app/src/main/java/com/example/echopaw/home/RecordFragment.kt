package com.example.echopaw.home

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Layout
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.AlignmentSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.echopaw.R
import com.example.echopaw.databinding.FragmentRecordBinding
import kotlinx.coroutines.launch

class RecordFragment : Fragment() {

    private var _binding: FragmentRecordBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecordViewModel by viewModels()

    private val REQUEST_RECORD_PERMISSIONS = 101
    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO
    )

    private var isAnimationRunning = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeViewModel()

        // 设置提示文字样式（保持居中效果）
        val editText: EditText = binding.inputBox
        val hintText = "输入分享你此刻的心情..."
        val spannableString = SpannableString(hintText)

        spannableString.setSpan(
            ForegroundColorSpan(0x80FFFFFF.toInt()),
            0,
            hintText.length,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannableString.setSpan(
            RelativeSizeSpan(1.1f),
            0,
            hintText.length,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannableString.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            hintText.length,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        editText.hint = spannableString

        // 关键：让光标显示在最左边
        binding.inputBox.post {
            binding.inputBox.requestFocus()
            binding.inputBox.setSelection(0) // 光标设置到位置0（最前面）
        }

        binding.recordBtn.setOnClickListener {
            when (viewModel.recordingState.value) {
                is RecordViewModel.RecordingState.Recording -> viewModel.stopRecording()
                else -> startRecording()
            }
        }
        binding.back.setOnClickListener {
            requireActivity().finish()
        }
    }

    private fun setupViews() {
        binding.bubble.post {
            binding.bubble.pivotX = binding.bubble.width / 2f
            binding.bubble.pivotY = binding.bubble.height / 2f
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.recordingState.collect { state ->
                        updateUIState(state)
                    }
                }
                launch {
                    viewModel.recordingTime.collect { time ->
                        binding.recordingTime.text = time
                    }
                }
                launch {
                    viewModel.waveformData.collect { data ->
                        binding.waveform.updateWaveform(data)
                    }
                }
            }
        }
    }

    private fun startRecording() {
        if (hasRequiredPermissions()) {
            viewModel.startRecording(requireContext())
        } else {
            requestPermissions(requiredPermissions, REQUEST_RECORD_PERMISSIONS)
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startRecording()
            } else {
                Toast.makeText(
                    context,
                    "需要录音权限才能使用录音功能",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updateUIState(state: RecordViewModel.RecordingState) {
        when (state) {
            is RecordViewModel.RecordingState.Recording -> {
                binding.bubble.visibility = View.VISIBLE
                binding.waveform.visibility = View.VISIBLE
                binding.recordingTime.visibility = View.VISIBLE
                binding.recordBtn.setImageResource(R.drawable.ic_microphoneing)
                binding.recordingStatus.text = "录音中"
                startBubbleAnimation()
            }
            is RecordViewModel.RecordingState.Idle,
            is RecordViewModel.RecordingState.Completed -> {
                binding.bubble.visibility = View.INVISIBLE
                binding.waveform.visibility = View.INVISIBLE
                binding.recordingTime.visibility = View.INVISIBLE
                binding.recordBtn.setImageResource(R.drawable.ic_microphone)
                binding.recordingStatus.text = "点击录音"
                isAnimationRunning = false
                binding.bubble.clearAnimation()
                if (state is RecordViewModel.RecordingState.Completed) {
                    Toast.makeText(
                        context,
                        "录音已保存: ${state.file.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            is RecordViewModel.RecordingState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                binding.bubble.visibility = View.INVISIBLE
                binding.waveform.visibility = View.INVISIBLE
                binding.recordingTime.visibility = View.INVISIBLE
                binding.recordBtn.setImageResource(R.drawable.ic_microphone)
                binding.recordingStatus.text = "点击录音"
                isAnimationRunning = false
                binding.bubble.clearAnimation()
            }
        }
    }

    private fun startBubbleAnimation() {
        if (!isAdded || _binding == null || isAnimationRunning) {
            return
        }

        isAnimationRunning = true
        animateBubbleLoop()
    }

    private fun animateBubbleLoop() {
        if (!isAdded || _binding == null) {
            isAnimationRunning = false
            return
        }

        binding.bubble.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(600)
            .withEndAction {
                if (!isAdded || _binding == null) {
                    isAnimationRunning = false
                    return@withEndAction
                }
                binding.bubble.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(600)
                    .withEndAction {
                        if (isAdded && _binding != null && isAnimationRunning) {
                            animateBubbleLoop()
                        } else {
                            isAnimationRunning = false
                        }
                    }
                    .start()
            }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isAnimationRunning = false
        if (viewModel.recordingState.value is RecordViewModel.RecordingState.Recording) {
            viewModel.cancelRecording()
        }
        _binding = null
    }
}