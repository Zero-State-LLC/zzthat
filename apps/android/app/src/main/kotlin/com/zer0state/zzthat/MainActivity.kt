package com.zer0state.zzthat

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zer0state.zzthat.app.Root
import com.zer0state.zzthat.app.TabModels
import com.zer0state.zzthat.scan.MlKitReader
import com.zer0state.zzthat.ui.ZZTheme

/** The tab models and the reader live across configuration changes. */
class RootViewModel : ViewModel() {
    val models = TabModels()
    val reader = MlKitReader()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = (application as ZZThatApplication).app
        val reducedMotion = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        setContent {
            val root = viewModel<RootViewModel>()
            LaunchedEffect(Unit) { app.start() }
            // GET /v1 again on return to the foreground (spec.md, API contract).
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { (application as ZZThatApplication).refreshDiscovery() }
            ZZTheme {
                Root(app, root.models, root.reader, reducedMotion)
            }
        }
    }
}
