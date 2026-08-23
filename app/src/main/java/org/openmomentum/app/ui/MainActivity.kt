package org.openmomentum.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import android.widget.ScrollView
import org.openmomentum.app.companion.MomentumCompanionManager
import org.openmomentum.app.model.HeadphoneState
import org.openmomentum.app.repository.MomentumRepository

class MainActivity : Activity() {
    private lateinit var repository: MomentumRepository
    private lateinit var companionManager: MomentumCompanionManager
    private lateinit var statusText: TextView
    private lateinit var batteryText: TextView
    private lateinit var modeText: TextView
    private lateinit var levelText: TextView
    private lateinit var slider: SeekBar
    private lateinit var refreshButton: Button
    private lateinit var integrationStatusText: TextView
    private lateinit var integrationButton: Button
    private var enableAfterNotificationPermission = false
    private val controlButtons = mutableListOf<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = MomentumRepository.get(this)
        companionManager = MomentumCompanionManager(this)
        setContentView(buildContent())
        render(repository.cachedState())
        updateIntegrationStatus()
        ensurePermissionAndRefresh()
    }

    override fun onResume() {
        super.onResume()
        if (hasBluetoothPermission()) refresh()
        companionManager.startObserving()
        updateIntegrationStatus()
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(32))
            setBackgroundColor(Color.rgb(247, 242, 250))
        }

        root.addView(text("OpenMomentum", 30f, Color.rgb(29, 27, 32), true))
        root.addView(text("A local, unofficial MOMENTUM 4 controller", 15f, Color.rgb(73, 69, 79)).apply {
            setPadding(0, dp(4), 0, dp(28))
        })

        statusText = text("Not checked", 16f, Color.rgb(73, 69, 79))
        batteryText = text("—%", 48f, Color.rgb(29, 27, 32), true)
        modeText = text("Unknown", 22f, Color.rgb(29, 27, 32), true)
        levelText = text("ANC 100  ·  Transparency 0", 14f, Color.rgb(73, 69, 79))

        root.addView(text("HEADPHONES", 12f, Color.rgb(103, 80, 164), true))
        root.addView(statusText)
        root.addView(batteryText.apply { setPadding(0, dp(8), 0, 0) })
        root.addView(modeText)
        root.addView(levelText.apply { setPadding(0, dp(4), 0, dp(16)) })

        slider = SeekBar(this).apply {
            max = 100
            progress = 50
            contentDescription = "ANC to transparency balance"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    levelText.text = balanceLabel(progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    setBusy(true)
                    repository.setNoiseLevel(seekBar?.progress ?: 50, ::operationFinished)
                }
            })
        }
        root.addView(slider, LinearLayout.LayoutParams.MATCH_PARENT, dp(48))

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        buttonRow.addView(controlButton("ANC") { setLevel(0) }, weightedButtonParams())
        buttonRow.addView(controlButton("Balanced") { setLevel(50) }, weightedButtonParams())
        buttonRow.addView(controlButton("Hear") { setLevel(100) }, weightedButtonParams())
        root.addView(buttonRow)

        val offButton = controlButton("Turn noise control off") {
            setBusy(true)
            repository.turnOff(::operationFinished)
        }
        root.addView(offButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(8)
        })

        refreshButton = Button(this).apply {
            text = "Refresh"
            setOnClickListener { refresh() }
        }
        root.addView(refreshButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(16)
        })

        root.addView(Space(this), LinearLayout.LayoutParams(1, dp(24)))
        root.addView(text("ANDROID INTEGRATION", 12f, Color.rgb(103, 80, 164), true))
        integrationStatusText = text("Checking…", 15f, Color.rgb(73, 69, 79)).apply {
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(integrationStatusText)
        integrationButton = Button(this).apply {
            text = "Enable automatic integration"
            isAllCaps = false
            setOnClickListener { enableAndroidIntegration() }
        }
        root.addView(
            integrationButton,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)),
        )

        root.addView(Space(this), LinearLayout.LayoutParams(1, dp(24)))
        root.addView(text(
            "Pair the headphones in Android settings first. Android integration adds automatic connection detection and a live battery/ANC notification after one system confirmation. The app still uses no account, cloud service, root, or Shizuku.",
            14f,
            Color.rgb(73, 69, 79),
        ))

        return ScrollView(this).apply { addView(root) }
    }

    private fun ensurePermissionAndRefresh() {
        if (!hasBluetoothPermission() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_BLUETOOTH)
        } else {
            refresh()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_BLUETOOTH -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    refresh()
                    updateIntegrationStatus()
                } else {
                    Toast.makeText(this, "Nearby devices permission is needed to control the headphones", Toast.LENGTH_LONG).show()
                }
            }
            REQUEST_NOTIFICATIONS -> {
                if (enableAfterNotificationPermission) {
                    enableAfterNotificationPermission = false
                    beginAssociation()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_ASSOCIATION) return
        integrationButton.isEnabled = true
        if (resultCode == RESULT_OK && companionManager.captureAssociationFromSystem()) {
            Toast.makeText(this, "Android integration enabled", Toast.LENGTH_SHORT).show()
            refresh()
        } else if (resultCode != RESULT_OK) {
            Toast.makeText(this, "Headphone association was not completed", Toast.LENGTH_LONG).show()
        }
        updateIntegrationStatus()
    }

    private fun enableAndroidIntegration() {
        val status = companionManager.status()
        if (!status.supported) {
            Toast.makeText(this, status.message, Toast.LENGTH_LONG).show()
            return
        }
        if (status.associated) {
            val observing = companionManager.startObserving()
            Toast.makeText(
                this,
                if (observing) "Automatic connection watching is enabled" else "Could not restart connection watching",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            enableAfterNotificationPermission = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        } else {
            beginAssociation()
        }
    }

    private fun beginAssociation() {
        integrationButton.isEnabled = false
        integrationStatusText.text = "Waiting for Android…"
        companionManager.associate(
            onApprovalRequired = { sender ->
                try {
                    startIntentSenderForResult(sender, REQUEST_ASSOCIATION, null, 0, 0, 0)
                } catch (error: IntentSender.SendIntentException) {
                    integrationButton.isEnabled = true
                    integrationStatusText.text = error.message ?: "Could not open the system confirmation"
                }
            },
            onCreated = {
                integrationButton.isEnabled = true
                updateIntegrationStatus()
                Toast.makeText(this, "Android integration enabled", Toast.LENGTH_SHORT).show()
                refresh()
            },
            onFailure = { message ->
                integrationButton.isEnabled = true
                updateIntegrationStatus()
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            },
        )
    }

    private fun updateIntegrationStatus() {
        val status = companionManager.status()
        integrationStatusText.text = status.message
        integrationButton.isEnabled = status.supported
        integrationButton.text = if (status.associated) {
            "Re-enable connection watching"
        } else {
            "Enable automatic integration"
        }
    }

    private fun refresh() {
        setBusy(true)
        repository.refresh(::operationFinished)
    }

    private fun setLevel(level: Int) {
        slider.progress = level
        setBusy(true)
        repository.setNoiseLevel(level, ::operationFinished)
    }

    private fun operationFinished(state: HeadphoneState) {
        setBusy(false)
        render(state)
        state.error?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
    }

    private fun render(state: HeadphoneState) {
        statusText.text = when {
            state.reachable -> "Connected to the control channel"
            state.error != null -> state.error
            else -> "Not checked"
        }
        batteryText.text = state.batteryPercent?.let { "$it%" } ?: "—%"
        modeText.text = state.noiseMode.displayName
        state.transparencyLevel?.let {
            if (!slider.isPressed) slider.progress = it
            levelText.text = balanceLabel(it)
        } ?: run {
            levelText.text = "ANC —  ·  Transparency —"
        }
    }

    private fun setBusy(busy: Boolean) {
        controlButtons.forEach { it.isEnabled = !busy }
        refreshButton.isEnabled = !busy
        slider.isEnabled = !busy
        if (busy) statusText.text = "Talking to MOMENTUM 4…"
    }

    private fun balanceLabel(level: Int) = "ANC ${100 - level}  ·  Transparency $level"

    private fun controlButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
        controlButtons += this
    }

    private fun weightedButtonParams() = LinearLayout.LayoutParams(0, dp(52), 1f).apply {
        marginStart = dp(2)
        marginEnd = dp(2)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_BLUETOOTH = 1001
        private const val REQUEST_NOTIFICATIONS = 1002
        private const val REQUEST_ASSOCIATION = 1003
    }
}
