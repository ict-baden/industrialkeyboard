package de.ictbaden.industrialkeyboard.app

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.divider.MaterialDivider
import de.ictbaden.industrialkeyboard.R
import java.text.Normalizer
import java.util.Locale

/**
 * Search in the settings: hides every row that does not match, and the cards and section headers
 * left without rows. Works on any screen built with [Ui] (section headers and cards are tagged
 * there), so new rows are found without registering them. Besides the visible texts (title,
 * summary, chips, lock notes) a row matches synonyms in all app languages and English, e.g.
 * "Vibration" finds "Haptik".
 */
object SettingsSearch {

    /** Words that mean the same thing; already [normalize]d. A query hitting one term finds all. */
    private val SYNONYMS: List<List<String>> = listOf(
        listOf("vibration", "vibrieren", "vibriert", "haptik", "haptic", "haptisch", "vibracion", "vibreur", "vibrer", "振动", "震动"),
        listOf("umlaut", "qwertz", "umlaut-tasten", "umlauttasten", "dieresis", "trema", "deutsch", "german", "变音"),
        listOf("scanner", "scan", "scannen", "barcode", "kamera", "camera", "datawedge", "honeywell", "escaner", "codigo", "code-barres", "camara", "扫描", "相机", "条码"),
        listOf("tab", "tabulator", "p1", "alpha.p1", "programmierbar", "programmable", "programable", "programmable", "可编程"),
        listOf("farbe", "farben", "color", "colour", "design", "theme", "material you", "systemfarben", "kontrast", "contrast", "akzent", "accent", "dunkel", "dark", "hell", "light", "couleur", "theme", "tema", "颜色", "主题", "对比"),
        listOf("sprache", "sprachen", "language", "languages", "tastatursprachen", "idioma", "idiomas", "langue", "langues", "语言"),
        listOf("handschuh", "handschuhe", "glove", "gloves", "schnellschalter", "quick toggle", "quick toggles", "guante", "gant", "手套"),
        listOf("ton", "sound", "klick", "click", "lautstarke", "volume", "sonido", "son", "声音", "音量"),
        listOf("vorschlag", "vorschlage", "wortvorschlage", "suggestion", "suggestions", "autokorrektur", "autocorrect", "korrektur", "worterbuch", "dictionary", "sugerencia", "correction", "建议"),
        listOf("diktat", "dictation", "mikrofon", "microphone", "spracheingabe", "voice", "dictado", "dictee", "听写", "语音"),
        listOf("passwort", "password", "kennwort", "admin", "administrator", "sperre", "gesperrt", "lock", "locked", "contrasena", "mot de passe", "密码"),
        listOf("textbaustein", "textbausteine", "snippet", "snippets", "kurzel", "shortcut", "abreviatura", "raccourci", "片段"),
        listOf("grosse", "hohe", "hoehe", "height", "size", "breite", "width", "tastenhohe", "altura", "taille", "高度"),
    )

    /** Lower case, accents removed (ä → a), ß → ss: what a search compares. */
    fun normalize(text: String): String {
        val lower = text.lowercase(Locale.ROOT).replace("ß", "ss")
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replace(COMBINING, "")
    }

    /** True when every word of [query] is in [text] directly or through a synonym. */
    fun matches(text: String, query: String): Boolean {
        val haystack = normalize(text)
        return words(query).all { word -> haystack.contains(word) || synonymsOf(word).any(haystack::contains) }
    }

    private fun words(query: String) = normalize(query).split(WHITESPACE).filter { it.isNotEmpty() }

    /** Terms of every group one of whose terms starts with [word] (at least 3 letters typed). */
    private fun synonymsOf(word: String): List<String> {
        if (word.length < 3) return emptyList()
        return SYNONYMS.filter { group -> group.any { it.startsWith(word) || word.startsWith(it) && it.length >= 3 } }.flatten()
    }

    /**
     * Shows only matching rows in [content] (children: section headers and cards from [Ui]).
     * Returns the number of visible rows; an empty [query] shows everything again.
     */
    fun filter(content: ViewGroup, query: String): Int {
        val active = words(query).isNotEmpty()
        var visibleRows = 0
        var header: View? = null
        var headerHasRows = false
        fun closeSection() {
            header?.visibility = if (!active || headerHasRows) View.VISIBLE else View.GONE
        }
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            when (child.getTag(R.id.settings_search_role)) {
                ROLE_SECTION -> {
                    closeSection()
                    header = child
                    headerHasRows = false
                }
                ROLE_CARD -> {
                    val rows = filterCard(child, query, active)
                    child.visibility = if (rows > 0 || !active) View.VISIBLE else View.GONE
                    if (rows > 0) headerHasRows = true
                    visibleRows += rows
                }
                ROLE_EMPTY -> child.visibility = View.GONE
                else -> {
                    // Anything else on the screen counts as one row of its section.
                    val show = !active || matches(textOf(child), query)
                    child.visibility = if (show) View.VISIBLE else View.GONE
                    if (show) {
                        headerHasRows = true
                        visibleRows++
                    }
                }
            }
        }
        closeSection()
        return visibleRows
    }

    private fun filterCard(card: View, query: String, active: Boolean): Int {
        val column = (card as? ViewGroup)?.getChildAt(0) as? ViewGroup ?: return if (!active || matches(textOf(card), query)) 1 else 0
        val shown = BooleanArray(column.childCount)
        var count = 0
        for (i in 0 until column.childCount) {
            val row = column.getChildAt(i)
            if (row is MaterialDivider) continue
            shown[i] = !active || matches(textOf(row), query)
            row.visibility = if (shown[i]) View.VISIBLE else View.GONE
            if (shown[i]) count++
        }
        // A divider stays only between two visible rows.
        var seenVisible = false
        for (i in 0 until column.childCount) {
            val row = column.getChildAt(i)
            if (row is MaterialDivider) {
                val nextVisible = (i + 1 until column.childCount).firstOrNull { column.getChildAt(it) !is MaterialDivider }?.let { shown[it] } == true
                row.visibility = if (seenVisible && nextVisible) View.VISIBLE else View.GONE
            } else if (shown[i]) {
                seenVisible = true
            }
        }
        return count
    }

    /** All texts of a row: titles, summaries, chips, lock notes, content descriptions and extra keywords. */
    fun textOf(view: View): String = buildString {
        fun collect(v: View) {
            (v.getTag(R.id.settings_search_keywords) as? String)?.let { append(it).append(' ') }
            v.contentDescription?.let { append(it).append(' ') }
            if (v is TextView) append(v.text).append(' ')
            if (v is ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i))
        }
        collect(view)
    }

    const val ROLE_SECTION = "section"
    const val ROLE_CARD = "card"
    const val ROLE_EMPTY = "empty"
    private val COMBINING = Regex("\\p{Mn}+")
    private val WHITESPACE = Regex("\\s+")
}
