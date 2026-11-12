package de.ictbaden.industrialkeyboard.ime

import de.ictbaden.industrialkeyboard.ui.ClipItem

/**
 * Copied texts for the clipboard panel, like Gboard – but only in memory (gone when the keyboard
 * process ends), at most [max] entries, unpinned ones for at most [maxAgeMs]. Sensitive clips
 * (passwords) are never added; the caller decides that. Only used with `typing.clipboardHistory`.
 */
class ClipboardHistory(private val max: Int = MAX_ITEMS, private val maxAgeMs: Long = MAX_AGE_MS) {

    private val items = ArrayList<ClipItem>()
    private var nextId = 1L

    /** Texts removed with × since the last copy – the clipboard may still hold one of them. In memory only. */
    private val dismissed = HashSet<String>()

    /** Adds [text] as the newest entry (it was just copied); the same text again moves up and keeps its pin. */
    fun add(text: String, now: Long) {
        if (text.isBlank()) return
        dismissed.clear()
        val existing = items.firstOrNull { it.text == text }
        items.remove(existing)
        items.add(0, ClipItem(existing?.id ?: nextId++, text, existing?.pinned == true, now))
        while (items.size > max) {
            val oldest = items.indexOfLast { !it.pinned }.takeIf { it >= 0 } ?: break
            items.removeAt(oldest)
        }
    }

    /**
     * The text on the clipboard when the panel opens (maybe copied while the keyboard was hidden) –
     * unless the user removed it with ×: it stays removed until something is copied again.
     */
    fun offer(text: String, now: Long) {
        if (text in dismissed || items.any { it.text == text }) return
        add(text, now)
    }

    /** Pinned first, then the rest; newest first; expired unpinned entries are dropped. */
    fun items(now: Long): List<ClipItem> {
        items.removeAll { !it.pinned && now - it.time > maxAgeMs }
        return items.filter { it.pinned } + items.filterNot { it.pinned }
    }

    fun togglePin(id: Long) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) items[i] = items[i].copy(pinned = !items[i].pinned)
    }

    fun remove(id: Long) {
        items.firstOrNull { it.id == id }?.let { dismissed += it.text }
        items.removeAll { it.id == id }
    }

    fun clear() {
        items.clear()
        dismissed.clear()
    }

    companion object {
        const val MAX_ITEMS = 20
        const val MAX_AGE_MS = 60 * 60 * 1000L
    }
}
