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
    private val APP_PATTERN = Regex("[A-Za-z0-9_.*?]{1,100}")

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
            snippets = parseSnippets(root.optJSONObject("snippets"), defaults.snippets),
            appProfiles = parseAppProfiles(root.opt("appProfiles"), custom),
            quickToggles = parseQuickToggles(root.optJSONObject("quickToggles"), defaults.quickToggles),
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
            put("dynamicColor", a.dynamicColor)
            put("emojiKey", a.emojiKey)
            put("symbolHints", a.symbolHints)
            put("keyBorders", a.keyBorders)
            put("oneHanded", a.oneHanded.key)
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
            put("umlautKeys", config.layout.umlautKeys)
            put("symbolsKey", config.layout.symbolsKey)
            put("topBar", config.layout.topBar.key)
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
            put("suggestions", config.typing.suggestions)
            put("autocorrect", config.typing.autocorrect)
            put("voice", config.typing.voice.key)
            put("deleteGesture", config.typing.deleteGesture)
            put("nextWord", config.typing.nextWord)
            put("clipboardHistory", config.typing.clipboardHistory)
            put("textEditing", config.typing.textEditing)
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
            put("ocr", JSONObject().apply {
                val o = s.ocr
                put("enabled", o.enabled)
                put("mode", o.mode.key)
                put("pattern", o.pattern ?: JSONObject.NULL)
                put("startInOcr", o.startInOcr)
                put("confirmReads", o.confirmReads)
                put("minLength", o.minLength)
                put("maxLength", o.maxLength)
                put("charset", o.charset.key)
            })
            put("nfc", JSONObject().apply {
                val n = s.nfc
                put("enabled", n.enabled)
                put("read", n.read.key)
                put("uidFormat", n.uidFormat.key)
                put("uidReversed", n.uidReversed)
                put("uidSeparator", n.uidSeparator)
                put("ndefRecord", n.ndefRecord.key)
                put("beep", n.beep)
                put("vibrate", n.vibrate)
            })
        })
        root.put("snippets", JSONObject().apply {
            val s = config.snippets
            put("enabled", s.enabled)
            put("shortcuts", s.shortcuts)
            put("button", s.button)
            put("allowUser", s.allowUser)
            put("items", writeSnippetItems(s.items))
        })
        // Always the array, `[]` included, so that "none" survives a round trip.
        root.put("quickToggles", JSONObject().apply {
            put("allowed", JSONArray(config.quickToggles.allowed.sortedBy { it.ordinal }.map { it.key }))
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
        // Left out when empty, so files of older versions stay as they were.
        if (config.appProfiles.isNotEmpty()) {
            root.put("appProfiles", JSONArray().apply { config.appProfiles.forEach { put(writeAppProfile(it)) } })
        }
        return root.toString(2)
    }

    /** `id`, `label`, `text` exactly as stored, `shortcut` only when set; also the file of the user's own. */
    internal fun writeSnippetItems(items: List<Snippet>) = JSONArray().apply {
        items.forEach { s ->
            put(JSONObject().apply {
                put("id", s.id)
                put("label", s.label)
                put("text", s.text)
                putOpt("shortcut", s.shortcut)
            })
        }
    }

    private fun parseSnippets(json: JSONObject?, d: SnippetSettings): SnippetSettings {
        json ?: return d
        val items = if (!json.has("items") || json.isNull("items")) {
            d.items
        } else {
            parseSnippetItems(json.opt("items") as? JSONArray ?: throw ConfigException("snippets.items: array expected"), "snippets.items", Snippets.MAX_ADMIN)
        }
        return SnippetSettings(
            enabled = json.optBoolean("enabled", d.enabled),
            shortcuts = json.optBoolean("shortcuts", d.shortcuts),
            button = json.optBoolean("button", d.button),
            allowUser = json.optBoolean("allowUser", d.allowUser),
            items = items,
        )
    }

    /**
     * Snippets of [array] with every check of the format; messages name [where] (`snippets.items`,
     * the managed key `snippets_json`, the file `user-snippets`). [user]: own snippets, limited to
     * text, `{TAB}` and `{ENTER}` ([Snippets.problem]).
     */
    internal fun parseSnippetItems(array: JSONArray, where: String, max: Int, user: Boolean = false): List<Snippet> {
        if (array.length() > max) throw ConfigException("$where: at most $max snippets")
        val items = (0 until array.length()).map { i ->
            val at = "$where[$i]"
            val json = array.opt(i) as? JSONObject ?: throw ConfigException("$at: object expected")
            val id = json.optNonBlank("id") ?: "s${i + 1}"
            if (!id.matches(ID)) throw ConfigException("$at.id: \"$id\" may only contain a-z, 0-9, - and _ (max. 24)")
            val label = json.optNonBlank("label") ?: throw ConfigException("$at.label is missing")
            val text = json.optRaw("text")
            val shortcut = json.optNonBlank("shortcut")
            when (Snippets.problem(label, text, shortcut, emptySet(), user)) {
                null, Snippets.Problem.LABEL_EMPTY, Snippets.Problem.SHORTCUT_TAKEN -> Unit
                Snippets.Problem.LABEL_TOO_LONG -> throw ConfigException("$at.label: at most ${Snippets.MAX_LABEL} characters")
                Snippets.Problem.TEXT_EMPTY -> throw ConfigException("$at.text is missing")
                Snippets.Problem.TEXT_TOO_LONG -> throw ConfigException("$at.text: at most ${Snippets.MAX_TEXT} characters")
                Snippets.Problem.MACRO -> checkMacro(text, "$at.text")
                Snippets.Problem.MACRO_NOT_ALLOWED -> throw ConfigException("$at.text: own snippets may only contain text, {TAB} and {ENTER}")
                Snippets.Problem.SHORTCUT_FORMAT ->
                    throw ConfigException("$at.shortcut: \"$shortcut\" must be ${Snippets.MIN_SHORTCUT}–${Snippets.MAX_SHORTCUT} letters")
            }
            Snippet(id = id, label = label, text = text, shortcut = shortcut)
        }
        items.groupBy { it.id }.filterValues { it.size > 1 }.keys.firstOrNull()?.let { throw ConfigException("$where: id \"$it\" is used twice") }
        items.mapNotNull { it.shortcut?.let(Snippets::key) }.groupBy { it }.filterValues { it.size > 1 }.keys.firstOrNull()?.let {
            throw ConfigException("$where: shortcut \"$it\" is used twice")
        }
        return items
    }

    /** Only what the profile sets, in the documented order; `scanSuffix: ""` is kept (= nothing). */
    private fun writeAppProfile(p: AppProfile) = JSONObject().apply {
        if (p.name.isNotEmpty()) put("name", p.name)
        if (p.apps.isNotEmpty()) put("apps", JSONArray(p.apps))
        if (p.fields.isNotEmpty()) put("fields", JSONArray(p.fields.sortedBy { it.ordinal }.map { it.key }))
        if (p.hints.isNotEmpty()) put("hints", JSONArray(p.hints))
        putOpt("page", p.page)
        putOpt("alphabet", p.alphabet?.key)
        putOpt("numericOrder", p.numericOrder?.key)
        putOpt("topBar", p.topBar?.key)
        putOpt("emojiKey", p.emojiKey)
        putOpt("suggestions", p.suggestions)
        putOpt("autocorrect", p.autocorrect)
        putOpt("scanSuffix", p.scanSuffix)
    }

    /** `appProfiles`: missing or null = none. */
    private fun parseAppProfiles(value: Any?, custom: List<CustomLayout>): List<AppProfile> {
        if (value == null || value == JSONObject.NULL) return emptyList()
        val array = value as? JSONArray ?: throw ConfigException("appProfiles: array expected")
        if (array.length() > AppProfile.MAX_PROFILES) throw ConfigException("appProfiles: at most ${AppProfile.MAX_PROFILES} profiles")
        return (0 until array.length()).map { i ->
            val where = "appProfiles[$i]"
            parseAppProfile(array.opt(i) as? JSONObject ?: throw ConfigException("$where: object expected"), where, custom)
        }
    }

    private fun parseAppProfile(json: JSONObject, where: String, custom: List<CustomLayout>): AppProfile {
        val name = json.optNonBlank("name").orEmpty()
        if (name.length > AppProfile.MAX_NAME) throw ConfigException("$where.name: at most ${AppProfile.MAX_NAME} characters")
        val apps = stringList(json, "apps", where, AppProfile.MAX_PATTERNS).onEachIndexed { j, app ->
            if (!app.matches(APP_PATTERN)) throw ConfigException("$where.apps[$j]: \"$app\" is not a package name or pattern (letters, digits, _, ., * and ?)")
        }.distinct()
        val fields = stringList(json, "fields", where, AppProfile.MAX_PATTERNS).mapIndexed { j, kind ->
            FieldKind.entries.firstOrNull { it.key.equals(kind, ignoreCase = true) }
                ?: throw ConfigException("$where.fields[$j]: unknown field type \"$kind\" (${FieldKind.entries.joinToString { it.key }})")
        }.toSet()
        val hints = stringList(json, "hints", where, AppProfile.MAX_PATTERNS).onEachIndexed { j, hint ->
            if (hint.isEmpty() || hint.length > AppProfile.MAX_PATTERN_LENGTH) {
                throw ConfigException("$where.hints[$j]: 1–${AppProfile.MAX_PATTERN_LENGTH} characters expected")
            }
        }.distinct()
        if (apps.isEmpty() && fields.isEmpty() && hints.isEmpty()) throw ConfigException("$where: needs at least one of apps, fields or hints")
        val page = json.optNonBlank("page")?.also { checkTab(it, "$where.page", custom) }
        val scanSuffix = if (json.has("scanSuffix") && !json.isNull("scanSuffix")) {
            json.optString("scanSuffix").also { if (it.isNotEmpty()) checkMacro(it, "$where.scanSuffix") }
        } else {
            null
        }
        val profile = AppProfile(
            name = name,
            apps = apps,
            fields = fields,
            hints = hints,
            page = page,
            alphabet = optEnum<Alphabet>(json, "alphabet", where),
            numericOrder = optEnum<NumericOrder>(json, "numericOrder", where),
            topBar = optEnum<TopBar>(json, "topBar", where),
            emojiKey = optBool(json, "emojiKey", where),
            suggestions = optBool(json, "suggestions", where),
            autocorrect = optBool(json, "autocorrect", where),
            scanSuffix = scanSuffix,
        )
        if (profile == AppProfile(name = name, apps = apps, fields = fields, hints = hints)) {
            throw ConfigException("$where: sets nothing (page, alphabet, numericOrder, topBar, emojiKey, suggestions, autocorrect or scanSuffix)")
        }
        return profile
    }

    /** Trimmed texts of the array [name]; missing or null = empty, at most [max] entries. */
    private fun stringList(json: JSONObject, name: String, where: String, max: Int): List<String> {
        if (!json.has(name) || json.isNull(name)) return emptyList()
        val array = json.opt(name) as? JSONArray ?: throw ConfigException("$where.$name: array expected")
        if (array.length() > max) throw ConfigException("$where.$name: at most $max entries")
        return (0 until array.length()).map { j ->
            (array.opt(j) as? String ?: throw ConfigException("$where.$name[$j]: text expected")).trim()
        }
    }

    /**
     * Strict, unlike the lenient `optBoolean` of the other sections: in a profile a missing value
     * means "inherit", so a typo such as `"flase"` must not silently become `false`.
     */
    private fun optBool(json: JSONObject, name: String, where: String): Boolean? {
        if (!json.has(name) || json.isNull(name)) return null
        return when (val value = json.opt(name)) {
            is Boolean -> value
            is String -> when (value.trim().lowercase()) {
                "true" -> true
                "false" -> false
                else -> null
            }
            else -> null
        } ?: throw ConfigException("$where.$name: true or false expected")
    }

    /** Like [enumValue], `null` when missing. */
    private inline fun <reified T> optEnum(json: JSONObject, name: String, where: String): T? where T : Enum<T>, T : Keyed {
        json.optNonBlank(name) ?: return null
        return enumValue(json, name, where, enumValues<T>().first())
    }

    /** `auto`, a built-in tab or a custom layout id (`tabs.preferred`, `appProfiles[].page`). */
    private fun checkTab(tab: String, where: String, custom: List<CustomLayout>) {
        if (tab != Tabs.AUTO && tab !in Tabs.BUILT_IN && custom.none { it.id == tab }) {
            throw ConfigException("$where: unknown tab \"$tab\" (auto, ${Tabs.BUILT_IN.joinToString()} or a custom layout id)")
        }
    }

    /** `allowed`: a list of toggle names; `[]` = none, missing = the default. */
    private fun parseQuickToggles(json: JSONObject?, d: QuickToggleSettings): QuickToggleSettings {
        json ?: return d
        if (!json.has("allowed") || json.isNull("allowed")) return d
        val array = json.opt("allowed") as? JSONArray ?: throw ConfigException("quickToggles.allowed: array expected")
        return QuickToggleSettings(
            (0 until array.length()).map { i ->
                val name = array.optString(i, "").trim()
                QuickToggle.byKey(name)
                    ?: throw ConfigException("quickToggles.allowed[$i]: unknown toggle \"$name\" (${QuickToggle.entries.joinToString { it.key }})")
            }.toSet(),
        )
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
            dynamicColor = json.optBoolean("dynamicColor", d.dynamicColor),
            emojiKey = json.optBoolean("emojiKey", d.emojiKey),
            symbolHints = json.optBoolean("symbolHints", d.symbolHints),
            keyBorders = json.optBoolean("keyBorders", d.keyBorders),
            oneHanded = enumValue(json, "oneHanded", "appearance", d.oneHanded),
        )
    }

    private fun parseLayout(json: JSONObject?, d: LayoutSettings): LayoutSettings {
        json ?: return d
        return LayoutSettings(
            alphabet = enumValue(json, "alphabet", "layout", d.alphabet),
            navigation = enumValue(json, "navigation", "layout", d.navigation),
            numericOrder = enumValue(json, "numericOrder", "layout", d.numericOrder),
            umlautKeys = json.optBoolean("umlautKeys", d.umlautKeys),
            symbolsKey = json.optBoolean("symbolsKey", d.symbolsKey),
            topBar = enumValue(json, "topBar", "layout", d.topBar),
        )
    }

    private fun parseTabs(json: JSONObject?, d: TabSettings, custom: List<CustomLayout>): TabSettings {
        json ?: return d
        val preferred = json.optNonBlank("preferred") ?: d.preferred
        checkTab(preferred, "tabs.preferred", custom)
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
            // alpha.p1 may be empty: then there is no such key (like Gboard).
            if (macro.isEmpty() && id != ProgrammableKey.ALPHA_P1.id) throw ConfigException("keys.$id: empty")
            if (macro.isEmpty()) {
                keys[id] = macro
                return@forEach
            }
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
            suggestions = json.optBoolean("suggestions", d.suggestions),
            autocorrect = json.optBoolean("autocorrect", d.autocorrect),
            voice = json.optNonBlank("voice")?.let { raw ->
                VoiceInput.parse(raw) ?: throw ConfigException("typing.voice: unknown value \"$raw\" (auto, offline, off)")
            } ?: d.voice,
            deleteGesture = json.optBoolean("deleteGesture", d.deleteGesture),
            nextWord = json.optBoolean("nextWord", d.nextWord),
            clipboardHistory = json.optBoolean("clipboardHistory", d.clipboardHistory),
            textEditing = json.optBoolean("textEditing", d.textEditing),
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
            ocr = parseOcr(json.optJSONObject("ocr"), d.ocr),
            nfc = parseNfc(json.optJSONObject("nfc"), d.nfc),
        )
    }

    private fun parseOcr(json: JSONObject?, d: OcrSettings): OcrSettings {
        json ?: return d
        val where = "scanner.ocr"
        val pattern = if (json.has("pattern") && !json.isNull("pattern")) json.optString("pattern").ifEmpty { null } else null
        pattern?.let { checkRegex(it, "$where.pattern") }
        val mode = enumValue(json, "mode", where, d.mode)
        if (mode == OcrMode.PATTERN && pattern == null) throw ConfigException("$where.pattern is required for mode pattern")
        val min = intIn(json, "minLength", where, 0, 999, d.minLength)
        val max = intIn(json, "maxLength", where, 0, 999, d.maxLength)
        if (max in 1 until min) throw ConfigException("$where.maxLength: smaller than minLength")
        return OcrSettings(
            enabled = json.optBoolean("enabled", d.enabled),
            mode = mode,
            pattern = pattern,
            startInOcr = json.optBoolean("startInOcr", d.startInOcr),
            confirmReads = intIn(json, "confirmReads", where, 1, 5, d.confirmReads),
            minLength = min,
            maxLength = max,
            charset = enumValue(json, "charset", where, d.charset),
        )
    }

    private fun parseNfc(json: JSONObject?, d: NfcSettings): NfcSettings {
        json ?: return d
        val where = "scanner.nfc"
        val separator = if (json.has("uidSeparator") && !json.isNull("uidSeparator")) json.optString("uidSeparator") else d.uidSeparator
        if (separator.length > 4) throw ConfigException("$where.uidSeparator: at most 4 characters")
        return NfcSettings(
            enabled = json.optBoolean("enabled", d.enabled),
            read = enumValue(json, "read", where, d.read),
            uidFormat = enumValue(json, "uidFormat", where, d.uidFormat),
            uidReversed = json.optBoolean("uidReversed", d.uidReversed),
            uidSeparator = separator,
            ndefRecord = enumValue(json, "ndefRecord", where, d.ndefRecord),
            beep = json.optBoolean("beep", d.beep),
            vibrate = json.optBoolean("vibrate", d.vibrate),
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
            // `ocr` and `nfc` are no camera barcode types; they only make sense in rules.
            symbologies = symbologies(json.optJSONArray("symbologies"), "$where.symbologies").filter { it.barcode }.toSet(),
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
