package com.flowwidgets.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowwidgets.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlowUiSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun catalogEditorAndSettingsAreReachable() {
        compose.waitForIdle()
        val welcome = compose.onAllNodesWithText("Crear mi espacio  ↗").fetchSemanticsNodes()
        if (welcome.isNotEmpty())
            compose.onNodeWithText("Crear mi espacio  ↗").performScrollTo().performClick()
        compose.onNodeWithText("Pequeños widgets.\nGrandes posibilidades.").assertIsDisplayed()
        compose.onNodeWithText("Personalizar reloj  ↗").performScrollTo().performClick()
        compose.onNodeWithText("Hazlo tuyo.").assertIsDisplayed()
        compose.onNodeWithText("Nombre del widget").performTextInput("Mi reloj")
        compose.onNodeWithText("Familia visual").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("One Style").performScrollTo().performClick()
        compose.onNodeWithText("← Volver").performClick()
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithText("A tu manera").assertIsDisplayed()
        compose.onNodeWithText("Oscuro").performClick()
        compose.onNodeWithText("Claro").performClick()
        compose.onNodeWithText("Sistema").performClick()
        compose.onNodeWithText("Tú decides qué compartir").performScrollTo().assertIsDisplayed()
    }
}
