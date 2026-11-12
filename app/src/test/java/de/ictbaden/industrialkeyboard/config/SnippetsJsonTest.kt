package de.ictbaden.industrialkeyboard.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** `snippets` in the JSON format: defaults, round trip (also the compact QR form) and every validation message. */
class SnippetsJsonTest {

    private fun error(json: String): String {
        try {
            ConfigJson.parse(json)
        } catch (e: ConfigException) {
            return e.message.orEmpty()
        }
        throw AssertionError("accepted: $json")
    }

    /** `snippets.items` with [items] as the third entry (index 2), after two valid ones. */
    private fun third(item: String) =
        """{"snippets":{"items":[{"id":"a","label":"A","text":"a"},{"id":"b","label":"B","text":"b"},$item]}}"""

    private val example = KeyboardConfig(
        snippets = SnippetSettings(
            enabled = true,
            shortcuts = false,
            button = true,
            allowUser = false,
            items = listOf(
                Snippet("damaged", "Ware beschädigt", "Ware bei Anlieferung beschädigt, Foto folgt.", "wb"),
                Snippet("regards", "Mit freundlichen Grüßen", "Mit freundlichen Grüßen{ENTER}Lager Süd", "mfg"),
                Snippet("bin", "Lagerplatz A-01", "  A-01-03{TAB} "),
            ),
        ),
    )

    @Test
    fun `defaults without snippets`() {
        assertEquals(SnippetSettings(), ConfigJson.parse("{}").snippets)
        assertEquals(SnippetSettings(), ConfigJson.parse("""{"snippets":{}}""").snippets)
        assertEquals(SnippetSettings(), ConfigJson.parse("""{"snippets":{"items":null}}""").snippets)
        val d = SnippetSettings()
        assertTrue(d.enabled)
        assertTrue(d.shortcuts)
        assertFalse(d.button)
        assertTrue(d.allowUser)
        assertTrue(d.items.isEmpty())
    }

    @Test
    fun `round trip keeps every field, the text untrimmed`() {
        val back = ConfigJson.parse(ConfigJson.write(example))
        assertEquals(example, back)
        assertEquals("  A-01-03{TAB} ", back.snippets.items[2].text)
    }

    @Test
    fun `shortcut is left out when not set`() {
        val written = JSONObject(ConfigJson.write(example)).getJSONObject("snippets").getJSONArray("items")
        assertEquals(setOf("id", "label", "text", "shortcut"), written.getJSONObject(0).keys().asSequence().toSet())
        assertEquals(setOf("id", "label", "text"), written.getJSONObject(2).keys().asSequence().toSet())
    }

    @Test
    fun `round trip in the compact QR form`() {
        val defaults = ConfigJson.write(KeyboardConfig())
        val compact = ConfigCodec.compact(ConfigJson.write(example), defaults)
        assertEquals(example, ConfigJson.parse(compact))
        assertEquals(example, ConfigJson.parse(ConfigCodec.decode(ConfigCodec.encode(compact))))
        // The defaults leave nothing behind.
        assertFalse(JSONObject(ConfigCodec.compact(defaults, defaults)).has("snippets"))
    }

    @Test
    fun `ids default to s1, s2`() {
        val items = ConfigJson.parse("""{"snippets":{"items":[{"label":"A","text":"a"},{"label":"B","text":"b"}]}}""").snippets.items
        assertEquals(listOf("s1", "s2"), items.map { it.id })
    }

    @Test
    fun `label and shortcut are trimmed, an empty shortcut is none`() {
        val s = ConfigJson.parse("""{"snippets":{"items":[{"label":"  A  ","text":" a ","shortcut":"  "},{"label":"B","text":"b","shortcut":" Mfg "}]}}""").snippets.items
        assertEquals("A", s[0].label)
        assertEquals(" a ", s[0].text)
        assertEquals(null, s[0].shortcut)
        assertEquals("Mfg", s[1].shortcut)
    }

    @Test
    fun `admin snippets may use every macro`() {
        val text = "{SCAN}{SHOW:numeric}{CTRL+C}{F13}"
        assertEquals(text, ConfigJson.parse("""{"snippets":{"items":[{"label":"A","text":"$text"}]}}""").snippets.items[0].text)
    }

    @Test
    fun `validation messages`() {
        val many = (1..101).joinToString(",") { """{"label":"L$it","text":"t"}""" }
        assertEquals("snippets.items: at most 100 snippets", error("""{"snippets":{"items":[$many]}}"""))
        assertEquals("snippets.items[2]: object expected", error(third("\"text\"")))
        assertEquals("snippets.items[2].id: \"Foo\" may only contain a-z, 0-9, - and _ (max. 24)", error(third("""{"id":"Foo","label":"C","text":"c"}""")))
        assertEquals("snippets.items: id \"bin\" is used twice", error("""{"snippets":{"items":[{"id":"bin","label":"A","text":"a"},{"id":"bin","label":"B","text":"b"}]}}"""))
        assertEquals("snippets.items[2].label is missing", error(third("""{"text":"c"}""")))
        assertEquals("snippets.items[2].label is missing", error(third("""{"label":"  ","text":"c"}""")))
        assertEquals("snippets.items[2].label: at most 40 characters", error(third("""{"label":"${"x".repeat(41)}","text":"c"}""")))
        assertEquals("snippets.items[2].text is missing", error(third("""{"label":"C"}""")))
        assertEquals("snippets.items[2].text is missing", error(third("""{"label":"C","text":""}""")))
        assertEquals("snippets.items[2].text: at most 2000 characters", error(third("""{"label":"C","text":"${"x".repeat(2001)}"}""")))
        assertEquals("snippets.items[2].text: unknown token {TABB}", error(third("""{"label":"C","text":"A{TABB}"}""")))
        assertEquals("snippets.items[2].shortcut: \"lp1\" must be 2–16 letters", error(third("""{"label":"C","text":"c","shortcut":"lp1"}""")))
        assertEquals("snippets.items[2].shortcut: \"a\" must be 2–16 letters", error(third("""{"label":"C","text":"c","shortcut":"a"}""")))
        assertEquals(
            "snippets.items: shortcut \"mfg\" is used twice",
            error("""{"snippets":{"items":[{"label":"A","text":"a","shortcut":"MFG"},{"label":"B","text":"b","shortcut":"mfg"}]}}"""),
        )
        assertEquals("snippets.items: array expected", error("""{"snippets":{"items":{"label":"A"}}}"""))
    }

    @Test
    fun `limits are exact`() {
        val hundred = (1..100).joinToString(",") { """{"label":"L$it","text":"t"}""" }
        assertEquals(100, ConfigJson.parse("""{"snippets":{"items":[$hundred]}}""").snippets.items.size)
        val edge = ConfigJson.parse(
            """{"snippets":{"items":[{"label":"${"x".repeat(40)}","text":"${"y".repeat(2000)}","shortcut":"${"ä".repeat(16)}"}]}}""",
        ).snippets.items[0]
        assertEquals(40, edge.label.length)
        assertEquals(2000, edge.text.length)
    }

    @Test
    fun `the example file is read`() {
        val dir = listOf(java.io.File("../examples"), java.io.File("examples")).first { it.isDirectory }
        val s = ConfigJson.parse(java.io.File(dir, "snippets.json").readText()).snippets
        assertTrue(s.button)
        assertEquals(listOf("damaged", "regards", "bin"), s.items.map { it.id })
        assertEquals("mfg", s.items[1].shortcut)
    }
}
