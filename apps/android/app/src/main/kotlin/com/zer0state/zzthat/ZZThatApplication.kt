package com.zer0state.zzthat

import android.app.Application
import com.zer0state.zzthat.app.AppModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The process entry (runtime.md, Modules: App): one AppModel for the process. */
class ZZThatApplication : Application() {
    val app: AppModel by lazy { AppModel.launch(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun refreshDiscovery() {
        scope.launch { app.refreshDiscovery() }
    }
}
