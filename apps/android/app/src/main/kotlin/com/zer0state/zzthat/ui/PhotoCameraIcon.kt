/*
 * Material Icons "photo_camera", filled style, 24 by 24 dp.
 *
 * Copyright Google LLC. Licensed under the Apache License, Version 2.0
 * (https://www.apache.org/licenses/LICENSE-2.0).
 * Upstream: https://github.com/google/material-design-icons,
 * src/image/photo_camera/materialicons/24px.svg. The path data is copied
 * unchanged. It is the icon androidx.compose.material:material-icons-extended
 * ships as Icons.Filled.PhotoCamera; the app copies this one vector instead of
 * depending on that library.
 */
package com.zer0state.zzthat.ui

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/** The Scan tab's icon, in the role of the iOS `camera.viewfinder` symbol. */
val PhotoCameraIcon: ImageVector = materialIcon(name = "Filled.PhotoCamera") {
    materialPath {
        // The lens: a circle of radius 3.2 at the center.
        moveTo(12.0f, 12.0f)
        moveToRelative(-3.2f, 0.0f)
        arcToRelative(3.2f, 3.2f, 0.0f, true, true, 6.4f, 0.0f)
        arcToRelative(3.2f, 3.2f, 0.0f, true, true, -6.4f, 0.0f)
    }
    materialPath {
        // The body, with the lens ring cut out.
        moveTo(9.0f, 2.0f)
        lineTo(7.17f, 4.0f)
        horizontalLineTo(4.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(12.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(16.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineTo(6.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        horizontalLineToRelative(-3.17f)
        lineTo(15.0f, 2.0f)
        horizontalLineTo(9.0f)
        close()
        moveTo(12.0f, 17.0f)
        curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
        reflectiveCurveToRelative(2.24f, -5.0f, 5.0f, -5.0f)
        reflectiveCurveToRelative(5.0f, 2.24f, 5.0f, 5.0f)
        reflectiveCurveToRelative(-2.24f, 5.0f, -5.0f, 5.0f)
        close()
    }
}
