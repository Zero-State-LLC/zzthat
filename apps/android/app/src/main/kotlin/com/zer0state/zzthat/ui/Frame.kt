package com.zer0state.zzthat.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import llc.zerostate.zzthis.design.ZZType
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.zer0state.zzthat.R
import com.zer0state.zzthat.app.AppModel
import com.zer0state.zzthat.scan.PhotoDecoder
import kotlinx.coroutines.launch
import llc.zerostate.zzthis.design.ZZSpace
import java.io.File

/** The back arrow of a pushed screen (Danny, #43 C3). TalkBack names it `common.back` (zzThis #78). */
class Back(val onClick: () -> Unit)

/**
 * A screen with its title bar: the back arrow on a pushed screen, the title,
 * and text actions such as Account on every tab (runtime.md, Navigation) and
 * Done on Account.
 */
@Composable
fun Frame(title: String?, back: Back? = null, actions: @Composable RowScope.() -> Unit = {}, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Column(modifier = Modifier.fillMaxSize().background(palette.paper)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ZZSpace.SpaceXs),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = if (back != null) ZZSpace.Space2xs else ZZSpace.Gutter, end = ZZSpace.Gutter),
        ) {
            if (back != null) {
                val label = stringResource(R.string.common_back)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .testTag("nav.back")
                        .size(MinTouch)
                        .clickable(role = Role.Button, onClick = back.onClick)
                        .semantics { contentDescription = label },
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = palette.accentInk)
                }
            }
            if (title != null) {
                Text(
                    text = title,
                    style = TextRole.TITLE.style(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            } else {
                Box(modifier = Modifier.weight(1f))
            }
            actions()
        }
        HorizontalDivider(color = palette.rule, thickness = ZZSpace.RuleHair)
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

/** A text action in the title bar, with a 48 dp target. */
@Composable
fun BarAction(text: String, tag: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .defaultMinSize(minWidth = MinTouch, minHeight = MinTouch)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ZZSpace.Space2xs),
    ) {
        Text(text, style = TextRole.LABEL.style().copy(color = LocalPalette.current.accentInk))
    }
}

/**
 * The three tabs: Scan, Create, My codes (runtime.md, Navigation), with the
 * Material icons nearest the iOS symbols (Danny, #43 C3). Scan shows the
 * Material photo_camera icon, copied into the app (PhotoCameraIcon.kt),
 * because the core set has no camera.
 */
enum class Tab(val label: Int, val tag: String, val icon: ImageVector) {
    SCAN(R.string.nav_scan, "tab.scan", PhotoCameraIcon),
    CREATE(R.string.nav_create, "tab.create", Icons.Filled.Create),
    CODES(R.string.nav_my_codes, "tab.codes", Icons.AutoMirrored.Filled.List),
}

@Composable
fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val palette = LocalPalette.current
    Column(modifier = Modifier.background(palette.panel)) {
        HorizontalDivider(color = palette.rule, thickness = ZZSpace.RuleHair)
        Row(modifier = Modifier.fillMaxWidth().height(ZZSpace.NavH).selectableGroup()) {
            for (tab in Tab.entries) {
                val isSelected = tab == selected
                val color = if (isSelected) palette.accentInk else palette.ink2
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .testTag(tab.tag)
                        .weight(1f)
                        .fillMaxSize()
                        .selectable(selected = isSelected, role = Role.Tab) { onSelect(tab) },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                        Text(
                            text = stringResource(tab.label),
                            textAlign = TextAlign.Center,
                            style = TextRole.CAPTION.style().copy(color = color, fontWeight = ZZType.WeightSemibold),
                        )
                    }
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth(0.5f)
                                .height(2.dp)
                                .background(palette.accent),
                        )
                    }
                }
            }
        }
    }
}

/** Share sends the canonical code as text, never a URL, through the system share sheet (FR-020). */
fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

/** Opens a mailto: or https: link the build settings name (FR-047). */
fun openLink(context: Context, uri: String) {
    try {
        context.startActivity(Intent(if (uri.startsWith("mailto:")) Intent.ACTION_SENDTO else Intent.ACTION_VIEW, Uri.parse(uri)))
    } catch (error: ActivityNotFoundException) {
        // No app handles the link on this phone. Nothing to say: the address stays on screen.
    }
}

/** The system camera and the system photo picker for one still photo (runtime.md, Read pipeline step 1). */
class PhotoCapture(val takePhoto: () -> Unit, val pickPhoto: () -> Unit, val cameraAvailable: Boolean)

/**
 * `TakePicture` writes into a FileProvider file in the cache folder, which
 * needs no camera permission (FR-011). `PickVisualMedia` is the system photo
 * picker, offered on every build (FR-012).
 */
@Composable
fun rememberPhotoCapture(app: AppModel, onPhoto: suspend (Bitmap) -> Unit, onUnreadable: () -> Unit, onCameraOff: () -> Unit): PhotoCapture {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pending
        if (saved && file != null) {
            scope.launch { PhotoDecoder.decode(file)?.let { onPhoto(it) } ?: onUnreadable() }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            // A picked photo replaces an earlier camera photo, which is deleted (FR-004).
            app.photos.end()
            scope.launch { PhotoDecoder.decode(context, uri)?.let { onPhoto(it) } ?: onUnreadable() }
        }
    }
    val cameraAvailable = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    return PhotoCapture(
        takePhoto = {
            try {
                val file = app.photos.begin()
                pending = file
                camera.launch(FileProvider.getUriForFile(context, context.packageName + ".photos", file))
            } catch (error: ActivityNotFoundException) {
                onCameraOff()
            }
        },
        pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        cameraAvailable = cameraAvailable,
    )
}
