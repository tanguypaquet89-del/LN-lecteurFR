package com.nahrahviing.lecteurnovel.data.sync

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class P2PSyncManager @Inject constructor() {

    private val SERVICE_ID = "com.nahrahviing.lecteurnovel.p2p.sync"
    private val STRATEGY = Strategy.P2P_POINT_TO_POINT

    private val _p2pState = MutableStateFlow<P2PState>(P2PState.Idle)
    val p2pState: StateFlow<P2PState> = _p2pState

    private var activeEndpointId: String? = null
    private var pendingBackupFile: File? = null
    
    var onFileReceived: ((File) -> Unit)? = null

    // Callbacks pour l'Advertising (Celui qui héberge la connexion et qui va envoyer/recevoir)
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // On accepte automatiquement pour la simplicité, en production on pourrait demander confirmation
            Nearby.getConnectionsClient(context).acceptConnection(endpointId, payloadCallback)
            _p2pState.value = P2PState.Connecting(info.endpointName)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                activeEndpointId = endpointId
                _p2pState.value = P2PState.Connected(endpointId)
                
                // Si on avait un fichier en attente, on l'envoie maintenant
                pendingBackupFile?.let {
                    sendBackupFile(it)
                    pendingBackupFile = null
                }
            } else {
                _p2pState.value = P2PState.Error("Échec de connexion")
            }
        }

        override fun onDisconnected(endpointId: String) {
            activeEndpointId = null
            _p2pState.value = P2PState.Idle
        }
    }

    // Callbacks pour les Payloads (fichiers reçus)
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.FILE) {
                payload.asFile()?.asJavaFile()?.let { receivedFile ->
                    _p2pState.value = P2PState.FileReceived
                    onFileReceived?.invoke(receivedFile)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                if (_p2pState.value is P2PState.Sending) {
                    _p2pState.value = P2PState.Success("Sauvegarde envoyée avec succès")
                }
            }
        }
    }

    private lateinit var context: Context

    fun initialize(appContext: Context) {
        this.context = appContext.applicationContext
    }

    fun startAdvertising(deviceName: String, backupFile: File? = null) {
        pendingBackupFile = backupFile
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(context)
            .startAdvertising(deviceName, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener {
                _p2pState.value = P2PState.Advertising
            }
            .addOnFailureListener { e ->
                _p2pState.value = P2PState.Error(e.message ?: "Erreur de démarrage")
            }
    }

    fun startDiscovery(deviceName: String) {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        
        val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                Nearby.getConnectionsClient(context)
                    .requestConnection(deviceName, endpointId, connectionLifecycleCallback)
            }

            override fun onEndpointLost(endpointId: String) {
            }
        }

        Nearby.getConnectionsClient(context)
            .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener {
                _p2pState.value = P2PState.Discovering
            }
            .addOnFailureListener { e ->
                _p2pState.value = P2PState.Error(e.message ?: "Erreur de découverte")
            }
    }

    fun sendBackupFile(file: File) {
        val endpointId = activeEndpointId
        if (endpointId != null) {
            val payload = Payload.fromFile(file)
            Nearby.getConnectionsClient(context).sendPayload(endpointId, payload)
            _p2pState.value = P2PState.Sending
        } else {
            _p2pState.value = P2PState.Error("Aucun appareil connecté")
        }
    }

    fun stopAll() {
        Nearby.getConnectionsClient(context).stopAdvertising()
        Nearby.getConnectionsClient(context).stopDiscovery()
        Nearby.getConnectionsClient(context).stopAllEndpoints()
        activeEndpointId = null
        _p2pState.value = P2PState.Idle
    }
}

sealed class P2PState {
    object Idle : P2PState()
    object Advertising : P2PState()
    object Discovering : P2PState()
    data class Connecting(val deviceName: String) : P2PState()
    data class Connected(val endpointId: String) : P2PState()
    object Sending : P2PState()
    object FileReceived : P2PState()
    data class Success(val message: String) : P2PState()
    data class Error(val message: String) : P2PState()
}
