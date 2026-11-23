package io.openvault.app

import android.app.Application
import io.openvault.app.core.database.OpenVaultDatabase
import io.openvault.app.core.network.BitwardenApiClient
import io.openvault.app.core.security.AppPreferences
import io.openvault.app.core.security.SensitiveClipboard
import io.openvault.app.core.security.VaultSessionManager
import io.openvault.app.data.VaultRepository

class AppContainer(application: Application) {
    val preferences = AppPreferences(application)
    val database = OpenVaultDatabase.create(application)
    val sessionManager = VaultSessionManager(preferences).also {
        it.registerProcessLifecycle()
    }
    val clipboard = SensitiveClipboard(application, preferences)
    val apiClient = BitwardenApiClient(preferences)
    val repository = VaultRepository(
        api = apiClient,
        dao = database.vaultDao(),
        session = sessionManager,
        preferences = preferences,
    )
}

class OpenVaultApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
