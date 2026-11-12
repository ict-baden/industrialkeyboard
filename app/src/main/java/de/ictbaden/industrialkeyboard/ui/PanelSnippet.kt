package de.ictbaden.industrialkeyboard.ui

/** One entry of the snippets panel; [key] is `a:<id>` (administrator) or `u:<id>` (user). [text] is the macro. */
data class PanelSnippet(val key: String, val label: String, val preview: String, val text: String)
