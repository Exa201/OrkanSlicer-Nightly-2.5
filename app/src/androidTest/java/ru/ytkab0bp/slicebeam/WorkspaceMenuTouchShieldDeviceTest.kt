package ru.ytkab0bp.slicebeam

import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Ensures menu gestures stay with their scrollables and transparent gaps remain pass-through. */
@RunWith(AndroidJUnit4::class)
class WorkspaceMenuTouchShieldDeviceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun menuScrollRemainsResponsiveAndTransparentTouchesReachRenderer() {
        val rendererTouchEvents = AtomicInteger()
        val menuClicks = AtomicInteger()
        val menuScroll = java.util.concurrent.atomic.AtomicReference<ScrollState>()

        compose.setContent {
            val scrollState = rememberScrollState().also(menuScroll::set)
            Box(Modifier.size(240.dp)) {
                AndroidView(
                    factory = { context ->
                        View(context).apply {
                            setOnTouchListener { _, event ->
                                if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                                    event.actionMasked == MotionEvent.ACTION_MOVE) {
                                    rendererTouchEvents.incrementAndGet()
                                }
                                true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier.fillMaxSize()
                        .testTag("workspace-menu-touch-shield"),
                ) {
                    Button(
                        onClick = { menuClicks.incrementAndGet() },
                        modifier = Modifier.align(Alignment.TopStart),
                    ) { Text("Menu action") }
                    Column(
                        Modifier.align(Alignment.BottomStart).height(100.dp)
                            .verticalScroll(scrollState).testTag("menu-scroll-child"),
                    ) {
                        repeat(10) { Text("Scrollable row $it", Modifier.height(40.dp)) }
                    }
                }
            }
        }

        compose.onNodeWithTag("menu-scroll-child").performTouchInput { swipeUp() }
        compose.waitUntil(5_000) { requireNotNull(menuScroll.get()).value > 0 }
        compose.runOnIdle {
            assertEquals("menu scroll input must not be routed to the renderer", 0,
                rendererTouchEvents.get())
        }

        compose.onNodeWithTag("workspace-menu-touch-shield").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(210f, 210f))
            moveTo(androidx.compose.ui.geometry.Offset(220f, 220f), 40)
            up()
        }
        compose.runOnIdle {
            assertEquals("an overlay without a pointer handler must not intercept outside touches", 0,
                rendererTouchEvents.get())
        }

        compose.onNodeWithText("Menu action").performClick()
        compose.runOnIdle {
            assertEquals("interactive menu children must keep receiving taps", 1, menuClicks.get())
            assertEquals("menu-button input must stay isolated from the renderer", 0,
                rendererTouchEvents.get())
        }
    }
}
