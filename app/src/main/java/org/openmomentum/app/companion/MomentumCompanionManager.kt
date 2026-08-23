package org.openmomentum.app.companion

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.regex.Pattern

class MomentumCompanionManager(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(CompanionDeviceManager::class.java)
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    data class Status(
        val supported: Boolean,
        val associated: Boolean,
        val message: String,
    )

    fun status(): Status {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return Status(false, false, "Automatic connection integration needs Android 12 or newer")
        }
        if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) {
            return Status(false, false, "Companion-device integration is unavailable on this phone")
        }
        val associated = findAssociation() != null
        return if (associated) {
            Status(true, true, "Enabled · Android watches the headset connection")
        } else {
            Status(true, false, "Not enabled · one system confirmation required")
        }
    }

    @SuppressLint("MissingPermission")
    fun associate(
        onApprovalRequired: (IntentSender) -> Unit,
        onCreated: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            onFailure("Automatic integration needs Android 12 or newer")
            return
        }
        if (!hasBluetoothPermission()) {
            onFailure("Nearby devices permission is required")
            return
        }

        val pairedAddress = appContext.getSystemService(BluetoothManager::class.java)
            .adapter
            ?.bondedDevices
            ?.singleOrNull { it.name?.contains("MOMENTUM 4", ignoreCase = true) == true }
            ?.address

        val filterBuilder = BluetoothDeviceFilter.Builder()
            .setNamePattern(Pattern.compile(".*MOMENTUM 4.*", Pattern.CASE_INSENSITIVE))
        if (pairedAddress != null) filterBuilder.setAddress(pairedAddress)
        val request = AssociationRequest.Builder()
            .addDeviceFilter(filterBuilder.build())
            .setSingleDevice(pairedAddress != null)
            .build()

        val callback = object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                onApprovalRequired(intentSender)
            }

            @Suppress("DEPRECATION")
            override fun onDeviceFound(intentSender: IntentSender) {
                onApprovalRequired(intentSender)
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                save(associationInfo.id, associationInfo.deviceMacAddress?.toString())
                startObserving()
                onCreated()
            }

            override fun onFailure(error: CharSequence?) {
                onFailure(error?.toString() ?: "Android could not associate the headset")
            }
        }

        @Suppress("DEPRECATION")
        manager.associate(request, callback, Handler(Looper.getMainLooper()))
    }

    fun captureAssociationFromSystem(): Boolean {
        val association = findAssociation() ?: return false
        save(association.id, association.address)
        startObserving()
        return true
    }

    @SuppressLint("MissingPermission")
    fun startObserving(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val association = findAssociation() ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= 36 && association.id >= 0) {
                manager.startObservingDevicePresence(
                    ObservingDevicePresenceRequest.Builder()
                        .setAssociationId(association.id)
                        .build(),
                )
            } else {
                @Suppress("DEPRECATION")
                manager.startObservingDevicePresence(
                    association.address ?: error("The companion association has no Bluetooth address"),
                )
            }
            true
        }.getOrDefault(false)
    }

    private data class StoredAssociation(val id: Int, val address: String?)

    @SuppressLint("MissingPermission")
    private fun findAssociation(): StoredAssociation? {
        if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val savedId = preferences.getInt(KEY_ID, -1)
            val associations = manager.myAssociations
            val match = associations.firstOrNull { it.id == savedId }
                ?: associations.firstOrNull { info ->
                    info.displayName?.toString()?.contains("MOMENTUM 4", ignoreCase = true) == true ||
                        info.deviceMacAddress?.toString() == preferences.getString(KEY_ADDRESS, null)
                }
                ?: associations.singleOrNull()
            match?.let { StoredAssociation(it.id, it.deviceMacAddress?.toString()) }
        } else {
            @Suppress("DEPRECATION")
            val addresses = manager.associations
            val savedAddress = preferences.getString(KEY_ADDRESS, null)
            val address = savedAddress?.takeIf { it in addresses } ?: addresses.singleOrNull()
            address?.let { StoredAssociation(-1, it) }
        }
    }

    private fun save(id: Int, address: String?) {
        preferences.edit()
            .putInt(KEY_ID, id)
            .putString(KEY_ADDRESS, address)
            .apply()
    }

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val PREFERENCES = "momentum_companion"
        private const val KEY_ID = "association_id"
        private const val KEY_ADDRESS = "device_address"
    }
}
