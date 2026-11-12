package de.ictbaden.industrialkeyboard.ime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.Tabs
import de.ictbaden.industrialkeyboard.layout.AlphaVariant
import de.ictbaden.industrialkeyboard.layout.EditAction
import de.ictbaden.industrialkeyboard.layout.KeyOutput
import de.ictbaden.industrialkeyboard.layout.Layouts
import de.ictbaden.industrialkeyboard.layout.TextEditState
import de.ictbaden.industrialkeyboard.ui.KeyboardRootView
import de.ictbaden.industrialkeyboard.ui.ShiftState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The text editing panel takes the place of the keys: same height, stable layout, state passed on. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "de")
class TextEditingPanelTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val letters = Page.Alpha(AlphaVariant.TEXT)
    private val selecting = TextEditState(selecting = true, hasSelection = true, password = false, editor = true)

    private fun state(toolPanel: String?, textEdit: TextEditState? = null, page: Page = letters) =
        KeyboardRenderer.state(context, KeyboardConfig(), page, ShiftState.OFF, null, toolPanel = toolPanel, textEdit = textEdit)

    @Test
    fun `the panel replaces the keys in the same height`() {
        val panel = state(Tabs.TOOL_EDIT, selecting)
        val keys = state(null)
        assertEquals(Layouts.TEXT_EDITING_ID, panel.layout?.id)
        assertEquals(keys.heightRows, panel.heightRows)
        assertEquals(keys.rowHeight, panel.rowHeight)
        assertEquals(selecting, panel.textEdit)
        // Also from the scan pad and the number pad: the panel is drawn by the key view.
        assertEquals(Layouts.TEXT_EDITING_ID, state(Tabs.TOOL_EDIT, page = Page.Scan).layout?.id)
        assertEquals(Layouts.TEXT_EDITING_ID, state(Tabs.TOOL_EDIT, page = Page.Numeric(de.ictbaden.industrialkeyboard.layout.NumericVariant.NUMBER)).layout?.id)
    }

    @Test
    fun `the layout stays equal when the selection changes`() {
        val none = state(Tabs.TOOL_EDIT, selecting.copy(selecting = false, hasSelection = false))
        assertEquals(none.layout, state(Tabs.TOOL_EDIT, selecting).layout)
        assertEquals(none.layout, state(Tabs.TOOL_EDIT, selecting).layout)
    }

    @Test
    fun `labels in the keyboard language`() {
        val labels = state(Tabs.TOOL_EDIT).layout!!.rows.flatMap { it.keys }.mapNotNull { it.label }
        assertEquals(listOf("Pos1", "Ende", "Alles auswählen", "Auswählen", "Kopieren", "Entf", "Einfügen", "ABC", "Ausschneiden"), labels)
    }

    @Test
    fun `the state reaches the key view without a new layout`() {
        val root = KeyboardRootView(context)
        root.render(state(Tabs.TOOL_EDIT, selecting.copy(hasSelection = false)))
        val layout = root.keyboard.layout
        root.render(state(Tabs.TOOL_EDIT, selecting))
        assertSame("a new selection must not set a new layout (it would stop the key repeat)", layout, root.keyboard.layout)
        assertEquals(selecting, root.keyboard.editState)
        root.render(state(null, selecting))
        assertNull("only while the panel is open", root.keyboard.editState)
        assertEquals(Tabs.ALPHA, root.keyboard.layout?.id)
    }

    @Test
    fun `cut sits on the bottom row next to abc`() {
        val bottom = state(Tabs.TOOL_EDIT).layout!!.rows.last().keys.map { it.output }
        assertEquals(listOf(KeyOutput.Edit(EditAction.CLOSE), KeyOutput.Edit(EditAction.CUT)), bottom)
    }
}
