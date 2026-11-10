package de.ictbaden.industrialkeyboard.config

import de.ictbaden.industrialkeyboard.layout.Macro
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

open class ConfigException(message: String) : Exception(message)

/** The JSON configuration format (version 1). */
object ConfigJson {
    const val VERSION = 1

    private val COLOR = Regex("#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})")
    private val ID = Regex("[a-z0-9][a-z0-9_-]{0,23}")
    private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    private val JSON_POSITION = Regex("at character (\\d+)")

    fun parse(text: String): KeyboardConfig {
        val root = try {
            JSONObject(ConfigCodec.decode(text))
        } catch (e: JSONException) {
            // org.json appends the whole input to its message, admin password included: position only.
            val position = e.message?.let { JSON_POSITION.find(it)?.groupValues?.get(1) }
            throw ConfigException(if (position != null) "Invalid JSON at character $position" else "Invalid JSON")
        }
        val version = root.optInt("version", VERSION)
        if (version > VERSION) throw ConfigException("Configuration version $version is newer than this app supports ($VERSION)")
        val defaults = KeyboardConfig()
        val custom = parseCustomLayouts(root.optJSONArray("customLayouts"))
        return KeyboardConfig(
            appearance = parseAppearance(root.optJSONObject("appearance"), defaults.appearance),
            layout = parseLayout(root.optJSONObject("layout"), defaults.layout),
            tabs = parseTabs(root.optJSONObject("tabs"), defaults.tabs, custom),
            keys = parseKeys(root.optJSONObject("keys")),
            typing = parseTyping(root.optJSONObject("typing"), defaults.typing),
            feedback = parseFeedback(root.optJSONObject("feedback"), defaults.feedback),
            scanner = parseScanner(root.optJSONObject("scanner"), defaults.scanner),
            customLayouts = custom,
            adminPasswordHash = passwordHash(root),
            language = language(root.optNonBlank("language")),
        )
    }

    fun write(config: KeyboardConfig): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.putOpt("adminPasswordHash", config.adminPasswordHash)
        root.put("language", config.language)
        root.put("appearance", JSONObject().apply {
            val a = config.appearance
            put("theme", a.theme.key)
            putOpt("accentColor", a.accentColor)
            put("keyHeight", a.keyHeight)
            put("width", a.width)
            put("numberRow", a.numberRow)
            put("functionRow", a.functionRow)
            put("keyHints", a.keyHints)
            put("modifierRow", a.modifierRow)
            put("functionModifiers", a.functionModifiers)
        })
        root.put("layout", JSONObject().apply {
            put("alphabet", config.layout.alphabet.key)
            put("navigation", config.layout.navigation.key)
            put("numericOrder", config.layout.numericOrder.key)
        })
        root.put("tabs", JSONObject().apply {
            val t = config.tabs
            put("alpha", t.alpha)
            put("numeric", t.numeric)
            put("symbols", t.symbols)
            put("function", t.function)
            put("scan", t.scan)
            put("preferred", t.preferred)
        })
        root.put("keys", JSONObject().apply { ProgrammableKey.entries.forEach { put(it.id, config.programmable(it)) } })
        root.put("typing", JSONObject().apply {
            put("autoCapitalize", config.typing.autoCapitalize)
            put("doubleSpacePeriod", config.typing.doubleSpacePeriod)
            put("longPressDelay", config.typing.longPressDelay)
        })
        root.put("feedback", JSONObject().apply {
            val f = config.feedback
            put("keyPopup", f.keyPopup)
            put("sound", f.sound)
            putOpt("soundVolume", f.soundVolume)
            put("vibrate", f.vibrate)
            putOpt("vibrationMs", f.vibrationMs)
        })
        root.put("scanner", JSONObject().apply {
            val s = config.scanner
            put("trigger", s.trigger.key)
            putOpt("intentAction", s.intentAction)
            putOpt("intentPackage", s.intentPackage)
            put("scanKey", s.scanKey)
            put("input", JSONObject().apply {
                put("enabled", s.input.enabled)
                put("action", s.input.action)
                put("extra", s.input.extra)
                put("suffix", s.input.suffix.key)
                putOpt("permission", s.input.permission)
                if (s.input.allowedPackages.isNotEmpty()) put("allowedPackages", JSONArray(s.input.allowedPackages))
            })
            put("camera", JSONObject().apply {
                val c = s.camera
                put("symbologies", JSONArray(c.symbologies.sortedBy { it.ordinal }.map { it.key }))
                put("aim", c.aim.key)
                put("continuous", c.continuous)
                put("duplicateDelayMs", c.duplicateDelayMs)
                put("confirmReads", c.confirmReads)
                put("minLength", c.minLength)
                put("maxLength", c.maxLength)
                put("zoom", c.zoom.toString().toDouble())
                put("torch", c.torch)
                put("inverted", c.inverted)
                put("beep", c.beep)
                put("vibrate", c.vibrate)
                put("timeoutSeconds", c.timeoutSeconds)
            })
            put("data", JSONObject().apply {
                val d = s.data
                put("prefix", d.prefix)
                put("suffix", d.suffix ?: JSONObject.NULL)
                put("trim", d.trim)
                put("removeSpaces", d.removeSpaces)
                put("case", d.case.key)
                put("stripLeading", d.stripLeading)
                put("stripTrailing", d.stripTrailing)
                put("replace", writeReplacements(d.replace))
                put("gs1", d.gs1.key)
                put("gsReplacement", d.gsReplacement ?: JSONObject.NULL)
                put("rules", JSONArray().apply {
                    d.rules.forEach { r ->
                        put(JSONObject().apply {
                            if (r.name.isNotEmpty()) put("name", r.name)
                            if (r.symbologies.isNotEmpty()) put("symbologies", JSONArray(r.symbologies.sortedBy { it.ordinal }.map { it.key }))
                            if (r.minLength > 0) put("minLength", r.minLength)
                            if (r.maxLength > 0) put("maxLength", r.maxLength)
                            if (r.startsWith.isNotEmpty()) put("startsWith", r.startsWith)
                            if (r.pattern.isNotEmpty()) put("pattern", r.pattern)
                            put("action", r.action.key)
                            putOpt("prefix", r.prefix)
                            putOpt("suffix", r.suffix)
                            putOpt("case", r.case?.key)
                            putOpt("stripLeading", r.stripLeading)
                            putOpt("stripTrailing", r.stripTrailing)
                            putOpt("removeSpaces", r.removeSpaces)
                            r.replace?.let { put("replace", writeReplacements(it)) }
                        })
                    }
                })
                put("unmatched", d.unmatched.key)
            })
        })
        if (config.customLayouts.isNotEmpty()) {
            root.put("customLayouts", JSONArray().apply {
                config.customLayouts.forEach { layout ->
                    put(JSONObject().apply {
                        put("id", layout.id)
                        put("label", layout.label)
                        put("rows", JSONArray().apply {
                            layout.rows.forEach { row ->
                                put(JSONArray().apply {
                                    row.forEach { key ->
                                        put(JSONObject().apply {
                                            put("label", key.label)
                                            // Output exactly as typed: a space key is " ", never trimmed.
                                            if (key.output != key.label) put("output", key.output)
                                            if (key.width != 1f) put("width", key.width.toString().toDouble()) // 1.2, not 1.2000000476837158
                                            if (key.style != KeyStyle.NORMAL) put("style", key.style.key)
                                        })
                                    }
                                })
                            }
                        })
                    })
                }
            })
        }
        return root.toString(2)
    }

    private fun parseAppearance(json: JSONObject?, d: Appearance): Appearance {
        json ?: return d
        val accent = json.optNonBlank("accentColor")?.let { value ->
            if (!value.matches(COLOR)) throw ConfigException("appearance.accentColor: \"$value\" is not a color (#RRGGBB)")
            value.uppercase()
        }
        return Appearance(
            theme = enumValue(json, "theme", "appearance", d.theme),
            accentColor = accent,
            keyHeight = json.optInt("keyHeight", d.keyHeight).coerceIn(70, 160),
            width = json.optInt("width", d.width).coerceIn(50, 100),
            numberRow = json.optBoolean("numberRow", d.numberRow),
            functionRow = json.optBoolean("functionRow", d.functionRow),
            keyHints = json.optBoolean("keyHints", d.keyHints),
            modifierRow = json.optBoolean("modifierRow", d.modifierRow),
            functionModifiers = json.optBoolean("functionModifiers", d.functionModifiers),
        )
    }

    private fun parseLayout(json: JSONObject?, d: LayoutSettings): LayoutSettings {
        json ?: return d
        return LayoutSettings(
            alphabet = enumValue(json, "alphabet", "layout", d.alphabet),
            navigation = enumValue(json, "navigation", "layout", d.navigation),
            numericOrder = enumValue(json, "numericOrder", "layout", d.numericOrder),
        )
    }

    private fun parseTabs(json: JSONObject?, d: TabSettings, custom: List<CustomLayout>): TabSettings {
        json ?: return d
        val preferred = json.optNonBlank("preferred") ?: d.preferred
        if (preferred != Tabs.AUTO && preferred !in Tabs.BUILT_IN && custom.none { it.id == preferred }) {
            throw ConfigException("tabs.preferred: unknown tab \"$preferred\" (auto, ${Tabs.BUILT_IN.joinToString()} or a custom layout id)")
        }
        return TabSettings(
            alpha = json.optBoolean("alpha", d.alpha),
            numeric = json.optBoolean("numeric", d.numeric),
            symbols = json.optBoolean("symbols", d.symbols),
            function = json.optBoolean("function", d.function),
            scan = json.optBoolean("scan", d.scan),
            preferred = preferred,
        )
    }

    private fun parseKeys(json: JSONObject?): KeySettings {
        json ?: return KeySettings()
        val keys = ProgrammableKey.DEFAULTS.toMutableMap()
        json.keys().forEach { id ->
            if (ProgrammableKey.byId(id) == null) {
                throw ConfigException("keys: unknown key \"$id\" (${ProgrammableKey.entries.joinToString { it.id }})")
            }
            val macro = json.optString(id)
            if (macro.isEmpty()) throw ConfigException("keys.$id: empty")
            checkMacro(macro, "keys.$id")
            keys[id] = macro
        }
        return KeySettings(keys)
    }

    private fun parseTyping(json: JSONObject?, d: TypingSettings): TypingSettings {
        json ?: return d
        return TypingSettings(
            autoCapitalize = json.optBoolean("autoCapitalize", d.autoCapitalize),
            doubleSpacePeriod = json.optBoolean("doubleSpacePeriod", d.doubleSpacePeriod),
            longPressDelay = json.optInt("longPressDelay", d.longPressDelay).coerceIn(200, 1500),
        )
    }

    private fun parseFeedback(json: JSONObject?, d: FeedbackSettings): FeedbackSettings {
        json ?: return d
        return FeedbackSettings(
            keyPopup = json.optBoolean("keyPopup", d.keyPopup),
            sound = json.optBoolean("sound", d.sound),
            soundVolume = json.optIntOrNull("soundVolume")?.coerceIn(0, 100),
            vibrate = json.optBoolean("vibrate", d.vibrate),
            vibrationMs = json.optIntOrNull("vibrationMs")?.coerceIn(5, 100),
        )
    }

    private fun parseScanner(json: JSONObject?, d: ScannerSettings): ScannerSettings {
        json ?: return d
        val trigger = enumValue(json, "trigger", "scanner", d.trigger)
        val action = json.optNonBlank("intentAction")
        if (trigger == ScanTriggerType.INTENT && action == null) throw ConfigException("scanner.intentAction is required for trigger \"intent\"")
        val input = json.optJSONObject("input")
        val inputDefaults = d.input
        return ScannerSettings(
            trigger = trigger,
            intentAction = action,
            intentPackage = json.optNonBlank("intentPackage"),
            scanKey = json.optBoolean("scanKey", d.scanKey),
            input = if (input == null) {
                inputDefaults
            } else {
                ScanInputSettings(
                    enabled = input.optBoolean("enabled", inputDefaults.enabled),
                    action = input.optNonBlank("action") ?: inputDefaults.action,
                    extra = input.optNonBlank("extra") ?: inputDefaults.extra,
                    suffix = enumValue(input, "suffix", "scanner.input", inputDefaults.suffix),
                    permission = input.optNonBlank("permission"),
                    allowedPackages = packages(input.optJSONArray("allowedPackages"), "scanner.input.allowedPackages"),
                )
            },
            camera = parseCamera(json.optJSONObject("camera"), d.camera),
            data = parseData(json.optJSONObject("data"), d.data),
        )
    }

    private fun parseCamera(json: JSONObject?, d: CameraSettings): CameraSettings {
        json ?: return d
        val where = "scanner.camera"
        val zoom = json.optDouble("zoom", d.zoom.toDouble())
        if (zoom.isNaN() || zoom < 1.0 || zoom > 4.0) throw ConfigException("$where.zoom: 1.0–4.0 expected")
        val min = intIn(json, "minLength", where, 0, 9999, d.minLength)
        val max = intIn(json, "maxLength", where, 0, 9999, d.maxLength)
        if (max in 1 until min) throw ConfigException("$where.maxLength: smaller than minLength")
        return CameraSettings(
            symbologies = symbologies(json.optJSONArray("symbologies"), "$where.symbologies"),
            aim = enumValue(json, "aim", where, d.aim),
            continuous = json.optBoolean("continuous", d.continuous),
            duplicateDelayMs = intIn(json, "duplicateDelayMs", where, 0, 10_000, d.duplicateDelayMs),
            confirmReads = intIn(json, "confirmReads", where, 1, 3, d.confirmReads),
            minLength = min,
            maxLength = max,
            zoom = zoom.toFloat(),
            torch = json.optBoolean("torch", d.torch),
            inverted = json.optBoolean("inverted", d.inverted),
            beep = json.optBoolean("beep", d.beep),
            vibrate = json.optBoolean("vibrate", d.vibrate),
            timeoutSeconds = intIn(json, "timeoutSeconds", where, 0, 600, d.timeoutSeconds),
        )
    }

    private fun parseData(json: JSONObject?, d: DataSettings): DataSettings {
        json ?: return d
        val where = "scanner.data"
        val prefix = json.optRaw("prefix")
        if (prefix.isNotEmpty()) checkMacro(prefix, "$where.prefix")
        val suffix = if (json.has("suffix") && !json.isNull("suffix")) {
            json.optString("suffix").also { if (it.isNotEmpty()) checkMacro(it, "$where.suffix") }
        } else {
            null
        }
        val rules = json.optJSONArray("rules")?.let { array ->
            (0 until array.length()).map { i ->
                parseRule(array.optJSONObject(i) ?: throw ConfigException("$where.rules[$i]: object expected"), "$where.rules[$i]")
            }
        }.orEmpty()
        return DataSettings(
            prefix = prefix,
            suffix = suffix,
            trim = json.optBoolean("trim", d.trim),
            removeSpaces = json.optBoolean("removeSpaces", d.removeSpaces),
            case = enumValue(json, "case", where, d.case),
            stripLeading = intIn(json, "stripLeading", where, 0, 999, d.stripLeading),
            stripTrailing = intIn(json, "stripTrailing", where, 0, 999, d.stripTrailing),
            replace = replacements(json.optJSONArray("replace"), "$where.replace"),
            gs1 = enumValue(json, "gs1", where, d.gs1),
            gsReplacement = if (json.has("gsReplacement") && !json.isNull("gsReplacement")) {
                json.optString("gsReplacement").also { if (it.isNotEmpty()) checkMacro(it, "$where.gsReplacement") }
            } else {
                null
            },
            rules = rules,
            unmatched = enumValue(json, "unmatched", where, d.unmatched),
        )
    }

    private fun parseRule(json: JSONObject, where: String): ScanRule {
        val pattern = json.optRaw("pattern")
        if (pattern.isNotEmpty()) checkRegex(pattern, "$where.pattern")
        val min = intIn(json, "minLength", where, 0, 9999, 0)
        val max = intIn(json, "maxLength", where, 0, 9999, 0)
        if (max in 1 until min) throw ConfigException("$where.maxLength: smaller than minLength")
        fun macro(name: String): String? =
            if (json.has(name) && !json.isNull(name)) json.optString(name).also { if (it.isNotEmpty()) checkMacro(it, "$where.$name") } else null
        fun optInt(name: String): Int? = if (json.has(name) && !json.isNull(name)) intIn(json, name, where, 0, 999, 0) else null
        return ScanRule(
            name = json.optNonBlank("name").orEmpty(),
            symbologies = symbologies(json.optJSONArray("symbologies"), "$where.symbologies"),
            minLength = min,
            maxLength = max,
            startsWith = json.optRaw("startsWith"),
            pattern = pattern,
            action = enumValue(json, "action", where, RuleAction.SEND),
            prefix = macro("prefix"),
            suffix = macro("suffix"),
            case = if (json.has("case") && !json.isNull("case")) enumValue(json, "case", where, TextCase.NONE) else null,
            stripLeading = optInt("stripLeading"),
            stripTrailing = optInt("stripTrailing"),
            removeSpaces = if (json.has("removeSpaces") && !json.isNull("removeSpaces")) json.optBoolean("removeSpaces") else null,
            replace = json.optJSONArray("replace")?.let { replacements(it, "$where.replace") },
        )
    }

    private fun symbologies(array: JSONArray?, where: String): Set<Symbology> {
        array ?: return emptySet()
        return (0 until array.length()).map { i ->
            val name = array.optString(i, "")
            Symbology.byKey(name) ?: throw ConfigException("$where[$i]: unknown barcode type \"$name\" (${Symbology.entries.joinToString { it.key }})")
        }.toSet()
    }

    private fun replacements(array: JSONArray?, where: String): List<Replacement> {
        array ?: return emptyList()
        return (0 until array.length()).map { i ->
            val json = array.optJSONObject(i) ?: throw ConfigException("$where[$i]: object expected")
            val find = json.optRaw("find")
            if (find.isEmpty()) throw ConfigException("$where[$i].find is missing")
            val regex = json.optBoolean("regex", false)
            if (regex) checkRegex(find, "$where[$i].find")
            Replacement(find = find, with = json.optRaw("with"), regex = regex)
        }
    }

    private fun writeReplacements(list: List<Replacement>) = JSONArray().apply {
        list.forEach { r ->
            put(JSONObject().apply {
                put("find", r.find)
                put("with", r.with)
                if (r.regex) put("regex", true)
            })
        }
    }

    private fun checkRegex(pattern: String, where: String) {
        try {
            Regex(pattern)
        } catch (e: IllegalArgumentException) {
            throw ConfigException("$where: invalid regular expression (${e.message?.lineSequence()?.firstOrNull()})")
        }
    }

    private fun intIn(json: JSONObject, name: String, where: String, min: Int, max: Int, default: Int): Int {
        if (!json.has(name) || json.isNull(name)) return default
        val value = json.opt(name)
        val number = (value as? Number)?.toDouble() ?: (value as? String)?.toDoubleOrNull()
            ?: throw ConfigException("$where.$name: number expected")
        if (number.isNaN() || number < min || number > max || number != Math.floor(number)) throw ConfigException("$where.$name: $min–$max expected")
        return number.toInt()
    }

    private fun parseCustomLayouts(array: JSONArray?): List<CustomLayout> {
        array ?: return emptyList()
        val layouts = (0 until array.length()).map { i ->
            val where = "customLayouts[$i]"
            val json = array.optJSONObject(i) ?: throw ConfigException("$where: object expected")
            val id = json.optNonBlank("id") ?: throw ConfigException("$where.id is missing")
            if (!id.matches(ID)) throw ConfigException("$where.id: \"$id\" may only contain a-z, 0-9, - and _ (max. 24)")
            if (id in Tabs.BUILT_IN || id == Tabs.AUTO) throw ConfigException("$where.id: \"$id\" is reserved")
            val rows = json.optJSONArray("rows") ?: throw ConfigException("$where.rows is missing")
            if (rows.length() == 0) throw ConfigException("$where.rows is empty")
            CustomLayout(
                id = id,
                label = json.optNonBlank("label") ?: id.uppercase(),
                rows = (0 until rows.length()).map { r ->
                    val row = rows.optJSONArray(r) ?: throw ConfigException("$where.rows[$r]: array expected")
                    if (row.length() == 0) throw ConfigException("$where.rows[$r] is empty")
                    (0 until row.length()).map { k -> parseCustomKey(row.opt(k), "$where.rows[$r][$k]") }
                },
            )
        }
        layouts.groupBy { it.id }.filterValues { it.size > 1 }.keys.firstOrNull()?.let {
            throw ConfigException("customLayouts: id \"$it\" is used twice")
        }
        return layouts
    }

    private fun packages(array: JSONArray?, where: String): List<String> {
        array ?: return emptyList()
        return (0 until array.length()).map { i ->
            val name = array.optString(i, "").trim()
            if (!name.matches(PACKAGE)) throw ConfigException("$where[$i]: \"$name\" is not a package name")
            name
        }.distinct()
    }

    private fun parseCustomKey(value: Any?, where: String): CustomKey = when (value) {
        is String -> {
            if (value.isEmpty()) throw ConfigException("$where: empty key")
            checkMacro(value, where)
            CustomKey(label = "", output = value)
        }
        is JSONObject -> {
            val label = value.optRaw("label")
            // Not trimmed: " " is a space key.
            val output = value.optRaw("output").ifEmpty { label }.ifEmpty { throw ConfigException("$where: label or output required") }
            checkMacro(output, "$where.output")
            val width = value.optDouble("width", 1.0)
            if (width.isNaN() || width < 0.5 || width > 10) throw ConfigException("$where.width: 0.5–10 expected")
            CustomKey(label = label, output = output, width = width.toFloat(), style = enumValue(value, "style", where, KeyStyle.NORMAL))
        }
        else -> throw ConfigException("$where: text or object expected")
    }

    private fun checkMacro(macro: String, where: String) {
        try {
            Macro.parse(macro)
        } catch (e: IllegalArgumentException) {
            throw ConfigException("$where: ${e.message}")
        }
    }

    private fun passwordHash(root: JSONObject): String? {
        root.optNonBlank("adminPassword")?.let { return PasswordHasher.hash(it) }
        val hash = root.optNonBlank("adminPasswordHash") ?: return null
        if (!PasswordHasher.isHash(hash)) throw ConfigException("adminPasswordHash: unsupported format")
        return hash
    }

    private inline fun <reified T> enumValue(json: JSONObject, name: String, where: String, default: T): T where T : Enum<T>, T : Keyed {
        val value = json.optNonBlank(name) ?: return default
        return enumValues<T>().firstOrNull { it.key.equals(value, ignoreCase = true) }
            ?: throw ConfigException("$where.$name: unknown value \"$value\" (${enumValues<T>().joinToString { it.key }})")
    }

    /** `auto` or a supported language tag, matched case-insensitively (`zh-hans` → `zh-Hans`). */
    private fun language(value: String?): String {
        value ?: return Languages.AUTO
        return (listOf(Languages.AUTO) + Languages.SUPPORTED).firstOrNull { it.equals(value, ignoreCase = true) }
            ?: throw ConfigException("language: unknown language \"$value\" (${Languages.AUTO}, ${Languages.SUPPORTED.joinToString()})")
    }

    /** The string as written (`""` when missing or null). */
    private fun JSONObject.optRaw(name: String): String = if (has(name) && !isNull(name)) optString(name) else ""

    private fun JSONObject.optNonBlank(name: String): String? =
        if (has(name) && !isNull(name)) optString(name).trim().ifEmpty { null } else null

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE } else null
}
