package dev.bastian.intervalo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import dev.bastian.intervalo.domain.timer.Phase
import dev.bastian.intervalo.domain.timer.PhaseKind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetContentTest {

    private val size = DpSize(180.dp, 110.dp)

    @Composable
    private fun Rendered(model: WidgetModel) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CompositionLocalProvider(LocalContext provides context) {
            GlanceTheme { WidgetContent(model) }
        }
    }

    @Test
    fun idleWidgetShowsTheLastRoutineAndAStartButton() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(size)
        provideComposable { Rendered(WidgetModel.Idle(3, "Tabata")) }

        onNode(hasText("Tabata")).assertExists()
        onNode(hasText("Ready")).assertExists()
        onNode(hasText("Start")).assertExists()
    }

    @Test
    fun idleWidgetWithoutRoutineHasNoStartButton() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(size)
        provideComposable { Rendered(WidgetModel.Idle(null, null)) }

        onNode(hasText("Intervalo")).assertExists()
        onNode(hasText("Start")).assertDoesNotExist()
    }

    @Test
    fun activeWidgetShowsPhaseAndPause() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(size)
        val phase = Phase(PhaseKind.WORK, "", 20_000, 1)
        provideComposable {
            Rendered(WidgetModel.Active("Tabata", phase, paused = false, endsAtWall = 1_700_000_000_000))
        }

        onNode(hasText("Tabata")).assertExists()
        onNode(hasText("Work")).assertExists()
        onNode(hasText("Pause")).assertExists()
        onNode(hasText("Resume")).assertDoesNotExist()
    }

    @Test
    fun pausedWidgetOffersResume() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(size)
        val phase = Phase(PhaseKind.REST, "Breathe", 10_000, 2)
        provideComposable {
            Rendered(WidgetModel.Active("Tabata", phase, paused = true, endsAtWall = null))
        }

        onNode(hasText("Breathe")).assertExists()
        onNode(hasText("Paused")).assertExists()
        onNode(hasText("Resume")).assertExists()
    }
}
