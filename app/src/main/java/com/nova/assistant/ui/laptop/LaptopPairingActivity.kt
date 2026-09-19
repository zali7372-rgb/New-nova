package com.nova.assistant.ui.laptop

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nova.assistant.NovaApplication
import com.nova.assistant.R
import com.nova.assistant.databinding.ActivityLaptopPairingBinding
import com.nova.assistant.system.bridge.LaptopLinkState
import kotlinx.coroutines.launch

class LaptopPairingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLaptopPairingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLaptopPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.laptop_pairing_title)

        val app = application as NovaApplication
        val settings = app.settingsRepository

        binding.inputIp.setText(settings.laptopIp)
        binding.inputPort.setText(settings.laptopPort.toString())
        binding.inputPin.setText(settings.laptopPin)
        binding.switchLaptopMode.isChecked = settings.laptopModeEnabled
        binding.switchMicStreaming.isChecked = settings.laptopMicStreamingEnabled

        binding.switchLaptopMode.setOnCheckedChangeListener { _, checked ->
            settings.laptopModeEnabled = checked
        }
        binding.switchMicStreaming.setOnCheckedChangeListener { _, checked ->
            settings.laptopMicStreamingEnabled = checked
        }

        binding.buttonConnect.setOnClickListener {
            val ip = binding.inputIp.text?.toString()?.trim().orEmpty()
            val port = binding.inputPort.text?.toString()?.trim()?.toIntOrNull() ?: 8765
            val pin = binding.inputPin.text?.toString()?.trim().orEmpty()

            if (ip.isBlank() || pin.isBlank()) {
                binding.textConnectionStatus.text = getString(R.string.laptop_pairing_missing_fields)
                return@setOnClickListener
            }

            settings.laptopIp = ip
            settings.laptopPort = port
            settings.laptopPin = pin

            app.laptopBridgeManager.connect(ip, port, pin, android.os.Build.MODEL ?: "Android telefon")
        }

        binding.buttonDisconnect.setOnClickListener {
            app.laptopBridgeManager.disconnect()
        }

        lifecycleScope.launch {
            app.laptopBridgeManager.state.collect { state -> renderState(state) }
        }
    }

    private fun renderState(state: LaptopLinkState) {
        binding.textConnectionStatus.text = when (state) {
            is LaptopLinkState.Disconnected -> getString(R.string.laptop_status_disconnected)
            is LaptopLinkState.Connecting -> getString(R.string.laptop_status_connecting)
            is LaptopLinkState.Connected -> getString(R.string.laptop_status_connected)
            is LaptopLinkState.Error -> getString(R.string.laptop_status_error, state.message)
        }
        binding.buttonConnect.isEnabled = state !is LaptopLinkState.Connected && state !is LaptopLinkState.Connecting
        binding.buttonDisconnect.isEnabled = state is LaptopLinkState.Connected
    }
}
