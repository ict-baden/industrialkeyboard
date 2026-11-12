package de.ictbaden.industrialkeyboard.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/** `appProfiles` in the JSON format: parsing, writing, the compact QR form and every validation message. */
class AppProfilesJsonTest {

    private fun example(): String {
        val dir = listOf(File("../examples"), File("examples")).first { it.isDirectory }
        return File(dir, "app-profiles.json").readText()
    }

    private fun error(json: String): String {
        try {
            ConfigJson.parse(json)
        } catch (e: ConfigException) {
            return e.message.orEmpty()
        }
        fail("accepted: $json")
        throw AssertionError()
    }

    /** One profile with [body] (inner JSON of the object) in an otherwise empty configuration. */
    private fun profileError(body: String) = error("""{"appProfiles":[{$body}]}""")

    private fun profile(body: String): AppProfile = ConfigJson.parse("""{"appProfiles":[{$body}]}""").appProfiles.single()

    @Test
    fun `the example is read completely`() {
        val c = ConfigJson.parse(example())
        assertEquals(4, c.appProfiles.size)
        assertEquals(
            AppProfile(
                name = "WMS quantity",
                apps = listOf("com.example.wms"),
                hints = listOf("*menge*", "*qty*", "*quantity*"),
                page = Tabs.NUMERIC,
                numericOrder = NumericOrder.CALCULATOR,
            ),
            c.appProfiles[0],
        )
        assertEquals(
            AppProfile(
                name = "WMS",
                apps = listOf("com.example.wms"),
                alphabet = Alphabet.QWERTZ,
                emojiKey = false,
                suggestions = false,
                autocorrect = false,
                scanSuffix = "{TAB}",
            ),
            c.appProfiles[1],
        )
        assertEquals(
            AppProfile(
                name = "SAP ITSmobile",
                apps = listOf("com.symbol.enterprisebrowser", "com.android.chrome"),
                fields = setOf(FieldKind.TEXT, FieldKind.NONE),
                page = Tabs.ALPHA,
                topBar = TopBar.TABS,
                emojiKey = false,
                suggestions = false,
                autocorrect = false,
                scanSuffix = "{ENTER}",
            ),
            c.appProfiles[2],
        )
        assertEquals(
            AppProfile(
                name = "Terminal emulation",
                apps = listOf("com.wavelink.*", "com.staylinked.*"),
                page = Tabs.FUNCTION,
                alphabet = Alphabet.QWERTY,
                topBar = TopBar.TABS,
                emojiKey = false,
                suggestions = false,
                autocorrect = false,
                scanSuffix = "{ENTER}",
            ),
            c.appProfiles[3],
        )
        // Profiles never change the stored values themselves.
        assertEquals(KeyboardConfig().typing, c.typing)
        assertEquals(KeyboardConfig().layout, c.layout)
    }

    @Test
    fun `round trip, also in the compact QR form`() {
        val c = ConfigJson.parse(example()).copy(
            appProfiles = ConfigJson.parse(example()).appProfiles + AppProfile(fields = setOf(FieldKind.URL, FieldKind.EMAIL), scanSuffix = ""),
        )
        assertEquals(c, ConfigJson.parse(ConfigJson.write(c)))
        val defaults = ConfigJson.write(KeyboardConfig())
        assertEquals(c, ConfigJson.parse(ConfigCodec.compact(ConfigJson.write(c), defaults)))
        assertEquals(c, ConfigJson.parse(ConfigCodec.decode(ConfigCodec.encode(ConfigCodec.compact(ConfigJson.write(c), defaults)))))
    }

    @Test
    fun `only what is set is written, fields sorted`() {
        val json = ConfigJson.write(KeyboardConfig(appProfiles = listOf(AppProfile(fields = setOf(FieldKind.NONE, FieldKind.TEXT), scanSuffix = "", suggestions = true))))
        val written = org.json.JSONObject(json).getJSONArray("appProfiles").getJSONObject(0)
        assertEquals(setOf("fields", "suggestions", "scanSuffix"), written.keys().asSequence().toSet())
        assertEquals("[\"text\",\"none\"]", written.getJSONArray("fields").toString())
        assertEquals("", written.getString("scanSuffix"))
    }

    @Test
    fun `missing means none, and write leaves it out`() {
        assertEquals(emptyList<AppProfile>(), ConfigJson.parse("{}").appProfiles)
        assertEquals(emptyList<AppProfile>(), ConfigJson.parse("""{"appProfiles":null}""").appProfiles)
        assertEquals(emptyList<AppProfile>(), ConfigJson.parse("""{"appProfiles":[]}""").appProfiles)
        assertFalse(ConfigJson.write(KeyboardConfig()).contains("appProfiles"))
    }

    @Test
    fun `validation messages`() {
        assertEquals("appProfiles: array expected", error("""{"appProfiles":{}}"""))
        assertEquals("appProfiles: array expected", error("""{"appProfiles":"com.sap.*"}"""))
        val many = (1..51).joinToString(",") { """{"apps":["a"],"page":"alpha"}""" }
        assertEquals("appProfiles: at most 50 profiles", error("""{"appProfiles":[$many]}"""))
        assertEquals("appProfiles[0]: object expected", error("""{"appProfiles":["com.sap.*"]}"""))
        assertEquals("appProfiles[0].name: at most 40 characters", profileError(""""name":"${"n".repeat(41)}","apps":["a"],"page":"alpha""""))
        assertEquals("appProfiles[0].apps: array expected", profileError(""""apps":"com.sap.*","page":"alpha""""))
        assertEquals("appProfiles[0].fields: array expected", profileError(""""fields":"text","page":"alpha""""))
        assertEquals("appProfiles[0].hints: array expected", profileError(""""hints":"x","page":"alpha""""))
        val patterns = (1..21).joinToString(",") { "\"a$it\"" }
        assertEquals("appProfiles[0].apps: at most 20 entries", profileError(""""apps":[$patterns],"page":"alpha""""))
        assertEquals("appProfiles[0].hints: at most 20 entries", profileError(""""hints":[$patterns],"page":"alpha""""))
        val kinds = (1..21).joinToString(",") { "\"text\"" }
        assertEquals("appProfiles[0].fields: at most 20 entries", profileError(""""fields":[$kinds],"page":"alpha""""))
        assertEquals("appProfiles[0].apps[1]: text expected", profileError(""""apps":["a",3],"page":"alpha""""))
        assertEquals(
            "appProfiles[0].apps[1]: \"x y\" is not a package name or pattern (letters, digits, _, ., * and ?)",
            profileError(""""apps":["a"," x y "],"page":"alpha""""),
        )
        assertEquals(
            "appProfiles[0].apps[0]: \"${"a".repeat(101)}\" is not a package name or pattern (letters, digits, _, ., * and ?)",
            profileError(""""apps":["${"a".repeat(101)}"],"page":"alpha""""),
        )
        assertEquals(
            "appProfiles[0].fields[1]: unknown field type \"foo\" (text, number, phone, datetime, email, url, none)",
            profileError(""""fields":["text","foo"],"page":"alpha""""),
        )
        assertEquals("appProfiles[0].hints[0]: 1–100 characters expected", profileError(""""hints":["  "],"page":"alpha""""))
        assertEquals("appProfiles[0].hints[1]: 1–100 characters expected", profileError(""""hints":["a","${"h".repeat(101)}"],"page":"alpha""""))
        assertEquals("appProfiles[0]: needs at least one of apps, fields or hints", profileError(""""name":"x","page":"alpha""""))
        assertEquals("appProfiles[0]: needs at least one of apps, fields or hints", profileError(""""apps":[],"page":"alpha""""))
        assertEquals(
            "appProfiles[0].page: unknown tab \"fnkeys\" (auto, alpha, numeric, symbols, function, scan or a custom layout id)",
            profileError(""""apps":["a"],"page":"fnkeys""""),
        )
        assertEquals(
            "appProfiles[0].alphabet: unknown value \"x\" (auto, qwerty, qwertz, azerty, nordic, spanish)",
            profileError(""""apps":["a"],"alphabet":"x""""),
        )
        assertEquals("appProfiles[0].numericOrder: unknown value \"x\" (phone, calculator)", profileError(""""apps":["a"],"numericOrder":"x""""))
        assertEquals("appProfiles[0].topBar: unknown value \"x\" (toolbar, tabs)", profileError(""""apps":["a"],"topBar":"x""""))
        assertEquals("appProfiles[0].suggestions: true or false expected", profileError(""""apps":["a"],"suggestions":"no""""))
        assertEquals("appProfiles[0].autocorrect: true or false expected", profileError(""""apps":["a"],"autocorrect":1"""))
        assertEquals("appProfiles[0].emojiKey: true or false expected", profileError(""""apps":["a"],"emojiKey":"flase""""))
        assertEquals("appProfiles[0].scanSuffix: unknown token {ENTRR}", profileError(""""apps":["a"],"scanSuffix":"{ENTRR}""""))
        assertEquals(
            "appProfiles[0]: sets nothing (page, alphabet, numericOrder, topBar, emojiKey, suggestions, autocorrect or scanSuffix)",
            profileError(""""apps":["a"],"Page":"numeric""""),
        )
        // The index names the profile that is wrong.
        assertEquals("appProfiles[1].page: unknown tab \"x\" (auto, alpha, numeric, symbols, function, scan or a custom layout id)",
            error("""{"appProfiles":[{"apps":["a"],"page":"alpha"},{"apps":["a"],"page":"x"}]}"""))
    }

    @Test
    fun `tabs preferred keeps its message`() {
        assertEquals(
            "tabs.preferred: unknown tab \"x\" (auto, alpha, numeric, symbols, function, scan or a custom layout id)",
            error("""{"tabs":{"preferred":"x"}}"""),
        )
    }

    @Test
    fun `page may be a custom layout id`() {
        val c = ConfigJson.parse(example().replace("\"page\": \"function\"", "\"page\": \"host\""))
        assertEquals("host", c.appProfiles[3].page)
    }

    @Test
    fun `a profile page whose custom layout is gone is rejected cleanly`() {
        // An import that drops the custom layout but keeps the profile pointing at it.
        val json = """{"appProfiles":[{"apps":["com.wavelink.*"],"page":"host"}]}"""
        assertEquals(
            "appProfiles[0].page: unknown tab \"host\" (auto, alpha, numeric, symbols, function, scan or a custom layout id)",
            error(json),
        )
    }

    @Test
    fun `booleans also as text`() {
        val p = profile(""""apps":["a"],"suggestions":"false","autocorrect":"true","emojiKey":true""")
        assertEquals(false, p.suggestions)
        assertEquals(true, p.autocorrect)
        assertEquals(true, p.emojiKey)
        // null = inherit, like a missing key.
        assertEquals(null, profile(""""apps":["a"],"suggestions":null,"page":"alpha"""").suggestions)
    }

    @Test
    fun `unknown keys inside a profile are ignored`() {
        val p = profile(""""apps":["a"],"page":"numeric","future":{"x":1},"activity":"Main"""")
        assertEquals(AppProfile(apps = listOf("a"), page = Tabs.NUMERIC), p)
    }

    @Test
    fun `apps and hints are trimmed and de-duplicated`() {
        val p = profile(""""name":"  x  ","apps":["com.a"," com.a ","com.b"],"hints":["Menge","Menge"],"fields":["TEXT","text"],"page":"alpha"""")
        assertEquals("x", p.name)
        assertEquals(listOf("com.a", "com.b"), p.apps)
        assertEquals(listOf("Menge"), p.hints)
        assertEquals(setOf(FieldKind.TEXT), p.fields)
    }

    @Test
    fun `an empty scan suffix is kept`() {
        val p = profile(""""apps":["a"],"scanSuffix":""""")
        assertEquals("", p.scanSuffix)
        assertTrue(ConfigJson.write(KeyboardConfig(appProfiles = listOf(p))).contains("\"scanSuffix\": \"\""))
    }
}
