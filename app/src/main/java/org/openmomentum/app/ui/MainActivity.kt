package org.openmomentum.app.ui

import android.Manifest
import android.app.Activity
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
import org.openmomentum.app.model.HeadphoneState
import org.openmomentum.app.repository.MomentumRepository

class MainActivity : Activity() {
    private lateinit var repository: MomentumRepository
    private lateinit var statusText: TextView
    private lateinit var batteryText: TextView
    private lateinit var modeText: TextView
    private lateinit var levelText: TextView
    private lateinit var slider: SeekBar
    private lateinit var refreshButton: Button
    private val controlButtons = mutableListOf<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = MomentumRepository.get(this)
        setContentView(buildContent())
        render(repository.cachedState())
        ensurePermissionAndRefresh()
    }

    override fun onResume() {
        super.onResume()
        if (hasBluetoothPermission()) refresh()
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
        root.addView(text(
            "Pair the headphones in Android settings first. The app talks directly to them over Bluetooth; it uses no account, cloud service, root, or Shizuku.",
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
        if (requestCode == REQUEST_BLUETOOTH && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            refresh()
        } else if (requestCode == REQUEST_BLUETOOTH) {
            Toast.makeText(this, "Nearby devices permission is needed to control the headphones", Toast.LENGTH_LONG).show()
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
    }
}
