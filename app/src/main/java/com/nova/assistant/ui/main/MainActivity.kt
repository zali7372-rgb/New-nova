package com.nova.assistant.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nova.assistant.NovaApplication
import com.nova.assistant.databinding.ActivityMainBinding
import com.nova.assistant.service.NovaListeningService
import com.nova.assistant.ui.settings.SettingsActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private val adapter = ChatAdapter()

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onMicTapped()
        } else {
            showPermissionDeniedMessage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as NovaApplication
        viewModel = ViewModelProvider(this, MainViewModel.Factory(app))[MainViewModel::class.java]

        (application as NovaApplication).voiceManager.initialize()

        binding.recyclerConversation.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.recyclerConversation.adapter = adapter

        binding.buttonMic.setOnClickListener { onMicClicked() }
        binding.buttonSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.inputText.setOnEditorActionListener { _, _, _ ->
            submitTypedText()
            true
        }
        binding.buttonSend.setOnClickListener { submitTypedText() }

        observeState(app)
    }

    private fun observeState(app: NovaApplication) {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                adapter.submitList(state.messages) {
                    binding.recyclerConversation.scrollToPosition(state.messages.size - 1)
                }
                binding.textStatus.text = statusLabel(state.status) +
                    if (state.laptopConnected) " · Laptop" else ""
                binding.buttonMic.isEnabled = state.voiceAvailable
            }
        }
        lifecycleScope.launch {
            app.settingsRequests.collect {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }
    }

    private fun onMicClicked() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.onMicTapped()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun submitTypedText() {
        val text = binding.inputText.text?.toString().orEmpty()
        if (text.isBlank()) return
        viewModel.onTextSubmitted(text)
        binding.inputText.text?.clear()
    }

    private fun showPermissionDeniedMessage() {
        com.google.android.material.snackbar.Snackbar.make(
            binding.root,
            getString(com.nova.assistant.R.string.mic_permission_denied),
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        ).show()
    }

    private fun statusLabel(status: NovaStatus): String = when (status) {
        NovaStatus.ONLINE -> getString(com.nova.assistant.R.string.status_online)
        NovaStatus.LISTENING -> getString(com.nova.assistant.R.string.status_listening)
        NovaStatus.THINKING -> getString(com.nova.assistant.R.string.status_thinking)
        NovaStatus.SPEAKING -> getString(com.nova.assistant.R.string.status_speaking)
        NovaStatus.OFFLINE -> getString(com.nova.assistant.R.string.status_offline)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            (application as NovaApplication).voiceManager.shutdown()
        }
    }
}
