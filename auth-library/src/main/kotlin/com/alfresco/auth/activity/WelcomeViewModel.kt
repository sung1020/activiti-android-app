package com.alfresco.auth.activity

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.alfresco.android.aims.BuildConfig
import com.alfresco.android.aims.R
import com.alfresco.auth.AuthType
import com.alfresco.auth.GlobalAuthConfig
import com.alfresco.auth.ui.BaseAuthViewModel
import com.alfresco.auth.ui.PkceAuthUiModel
import com.alfresco.common.SingleLiveEvent
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

class AIMSWelcomeViewModel(private val applicationContext: Context) : BaseAuthViewModel() {

    override var globalAuthConfig = defaultAuthConfig.copy()

    private var _authConfigEditor = AuthConfigEditor()
    val authConfigEditor : AuthConfigEditor get() {
        return _authConfigEditor
    }

    private val _hasNavigation = MutableLiveData<Boolean>()

    val hasNavigation: LiveData<Boolean> get() = _hasNavigation

    private val _authType = MutableLiveData<AuthenticationType>()

    val authType: LiveData<AuthenticationType> get() = _authType

    private val _authResult = MutableLiveData<PkceAuthUiModel>()

    val authResult: LiveData<PkceAuthUiModel> get() = _authResult

    private val _startSSO = SingleLiveEvent<String>()

    val startSSO: LiveData<String> get() = _startSSO

    private val _onShowHelp = SingleLiveEvent<Int>()
    private val _onShowSettings = SingleLiveEvent<Int>()

    val onShowHelp: SingleLiveEvent<Int> = _onShowHelp
    val onShowSettings: SingleLiveEvent<Int> = _onShowSettings

    val endpoint = MutableLiveData<String>()

    private var identityServiceUrl: String? = null
    private var processRepositoryUrl: String? = null

    init {
        loadSavedConfig()

        if (BuildConfig.DEBUG) {
            endpoint.value = "alfresco-identity-service.mobile.dev.alfresco.me"
//            endpoint.value = "activiti.alfresco.com"
        }
    }

    fun cloudAuthType() {
        _authType.value = AuthenticationType.Basic(hostname = "activiti.alfresco.com", withCloud = true)
    }

    override fun handleAuthType(endpoint: String, authType: AuthType) {
        when (authType) {
            AuthType.SSO ->  _authType.value = AuthenticationType.SSO(endpoint)

            AuthType.BASIC -> _authType.value = AuthenticationType.Basic(hostname = endpoint, withCloud = false)

            AuthType.UNKNOWN -> _authType.value = AuthenticationType.Unknown()
        }
    }

    override fun handleSSOTokenResponse(model: PkceAuthUiModel) {
        _authResult.value = model
    }

    fun setHasNavigation(enableNavigation: Boolean) {
        _hasNavigation.value = enableNavigation
    }

    fun ssoLogin(identityServiceUrl: String, processRepositoryUrl: String) {
        this.identityServiceUrl = identityServiceUrl
        this.processRepositoryUrl = processRepositoryUrl

        _startSSO.value = identityServiceUrl
    }

    fun startEditing() {
        _authConfigEditor = AuthConfigEditor()
        _authConfigEditor.reset(globalAuthConfig)
    }

    fun connect() {
        endpoint.value?.let {
            checkAuthType(it)
        }
    }

    fun cloudConnect() {
        cloudAuthType()
    }

    fun showSettings() {
        onShowSettings.value = 0
    }

    fun showWelcomeHelp() {
        onShowHelp.value = R.string.auth_help_identity_body
    }

    fun showSettingsHelp() {
        onShowHelp.value = R.string.auth_help_settings_body
    }

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private fun loadSavedConfig() {
        val sharedPrefs  = applicationContext.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val configJson = sharedPrefs.getString(SHARED_PREFS_CONFIG_KEY, null)

        globalAuthConfig = try {
            if (configJson != null)
                Gson().fromJson(configJson, GlobalAuthConfig::class.java)
            else
                defaultAuthConfig
        } catch (e: JsonSyntaxException) {
            defaultAuthConfig
        }
    }

    fun saveConfigChanges() {
        val config = authConfigEditor.get()

        val sharedPrefs  = applicationContext.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        editor.putString(SHARED_PREFS_CONFIG_KEY, Gson().toJson(config))
        editor.apply()

        globalAuthConfig = config
    }

    fun resetToDefaultConfig() {
        authConfigEditor.reset(defaultAuthConfig)
    }

    companion object {
        private const val SHARED_PREFS_NAME = "org.activiti.aims.android.auth"
        private const val SHARED_PREFS_CONFIG_KEY = "config"

        private val defaultAuthConfig = DefaultAuthConfig.get()
    }

    class AuthConfigEditor() {
        val https = MutableLiveData<Boolean>()
        val port = MutableLiveData<String>()
        val serviceDocuments = MutableLiveData<String>()
        val realm = MutableLiveData<String>()
        val clientId = MutableLiveData<String>()
        val redirectUrl = MutableLiveData<String>()

        init {
            https.observeForever{
                port.value = if(it == true) DEFAULT_HTTPS_PORT else DEFAULT_HTTP_PORT
            }
        }

        fun reset(config: GlobalAuthConfig) {
            https.value = config.https
            port.value = config.port
            serviceDocuments.value = config.serviceDocuments
            realm.value = config.realm
            clientId.value = config.clientId
            redirectUrl.value = config.redirectUrl
        }

        fun get(): GlobalAuthConfig {
            return GlobalAuthConfig(
                    https = https.value ?: false,
                    port = port.value ?: "",
                    serviceDocuments = serviceDocuments.value ?: "",
                    realm = realm.value ?: "",
                    clientId = clientId.value ?: "",
                    redirectUrl = redirectUrl.value ?: ""
            )
        }

        companion object {
            private const val DEFAULT_HTTP_PORT = "80"
            private const val DEFAULT_HTTPS_PORT = "443"
        }
    }
}

sealed class AuthenticationType {

    data class Basic(val hostname: String, val withCloud: Boolean) : AuthenticationType()

    data class SSO(val endpoint: String) : AuthenticationType()

    class Unknown : AuthenticationType()
}

