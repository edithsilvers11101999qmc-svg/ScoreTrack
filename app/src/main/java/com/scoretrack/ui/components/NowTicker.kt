package com.scoretrack.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

/**
 * 跟随时间自动刷新的「当前时刻」。
 *
 * 刷新时机：
 * - 每 [intervalMillis] 毫秒一次（默认 30 秒，足够体现小时 / 分钟变化）；
 * - 应用从后台回到前台（ON_RESUME）时立即刷新，覆盖「切出去很久再回来」的情况；
 * - 用户修改系统时间后回到前台同样会立即刷新。
 */
@Composable
fun rememberNow(intervalMillis: Long = 30_000L): ZonedDateTime {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner, intervalMillis) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) now = ZonedDateTime.now()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        try {
            while (true) {
                now = ZonedDateTime.now()
                delay(intervalMillis)
            }
        } finally {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return now
}
