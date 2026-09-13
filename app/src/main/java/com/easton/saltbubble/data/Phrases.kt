package com.easton.saltbubble.data

object Phrases {
    enum class Kind {
        Wake,
        Standby,
        TargetGrok,
        TargetBrand,
        Clip,
        Task,
        Noise,
    }

    private val saltAliases = listOf(
        "salt", "assault", "fault", "sold", "Saul", "solt", "sort",
    )

    private val wakeCores = listOf(
        "wake up", "wakeup", "wake", "listen", "open voice over", "start voice over",
        "open voice", "start voice", "voice over", "hey", "ok", "okay",
    )

    private val standbyCores = listOf(
        "go on standby", "go on stand by", "on standby", "on stand by", "standby", "stand by",
        "pause", "quiet", "stop voice over", "close voice over", "turn voice over off",
        "cut asleep", "cut sleep", "sleep", "go to sleep", "close voice", "stop listening",
        "that's all", "thats all", "turn off", "stop", "bye", "say bye",
    )

    private val grokCores = listOf(
        "send to grok bot", "send to grok", "use grok bot", "use grok", "to grok",
    )

    private val brandCores = listOf(
        "send to brand studio", "send to my brand studio", "send to capture",
        "use brand studio", "use capture", "to brand studio", "to capture",
        "open brand studio", "open capture",
    )

    // Keep in sync with Salt Local phrases.CLIP_PHRASES
    private val clipCores = listOf(
        "clip that", "clip this", "clip it", "save clip", "grab that", "clip",
    )

    fun normalize(raw: String): String {
        var t = raw.lowercase()
            .replace(Regex("[^a-z0-9\\s']"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        for (alias in saltAliases) {
            t = t.replace(Regex("\\b${Regex.escape(alias.lowercase())}\\b"), "salt")
        }
        // "clipped" / salt+"click" → clip (matches Local)
        t = t.replace(Regex("\\bclipped\\b"), "clip")
        if (t.contains("salt")) {
            t = t.replace(Regex("\\bclick\\b"), "clip")
        }
        return t
    }

    fun classify(raw: String): Kind {
        val text = normalize(raw)
        if (text.length < 2) return Kind.Noise
        if (isNoise(text)) return Kind.Noise
        if (isClip(text)) return Kind.Clip
        if (matchesSaltCommand(text, standbyCores) || text == "bye" || text == "say bye" || text == "salt bye") {
            return Kind.Standby
        }
        if (matchesSaltCommand(text, wakeCores) || text.startsWith("hey salt") || text.startsWith("ok salt") || text.startsWith("okay salt")) {
            return Kind.Wake
        }
        if (matchesSaltCommand(text, grokCores)) return Kind.TargetGrok
        if (matchesSaltCommand(text, brandCores)) return Kind.TargetBrand
        return Kind.Task
    }

    /** If the user said wake + task in one breath, return the task remainder. */
    fun remainderAfterWake(raw: String): String {
        val text = normalize(raw)
        val patterns = listOf(
            Regex("^salt\\s+(?:wake(?:\\s*up)?|listen|open voice(?: over)?|start voice(?: over)?|voice over)\\s*(.*)"),
            Regex("^(?:hey|ok|okay)\\s+salt\\s*(.*)"),
            Regex("^wake(?:\\s*up)?\\s+salt\\s*(.*)"),
        )
        for (p in patterns) {
            val m = p.find(text) ?: continue
            val rest = m.groupValues.getOrNull(1)?.trim().orEmpty()
            if (rest.isNotEmpty() && classify(rest) == Kind.Task) return rest
        }
        return ""
    }

    private fun isClip(text: String): Boolean {
        if (clipCores.any { text == it || text == "salt $it" }) return true
        if (text.startsWith("salt ") && clipCores.any { text.removePrefix("salt ").startsWith(it) }) return true
        return text == "clip that" || text == "clip this" || text == "salt clip that" || text == "salt save clip"
    }

    private fun matchesSaltCommand(text: String, cores: List<String>): Boolean {
        if (cores.any { text == it || text == "salt $it" || text.endsWith(" salt $it") }) return true
        if (text.startsWith("salt ") && cores.any { text.removePrefix("salt ").startsWith(it) }) return true
        // also allow cores without salt prefix for standby bye etc.
        return cores.any { text == it || text.startsWith("$it ") }
    }

    private fun isNoise(text: String): Boolean {
        val noise = setOf("um", "uh", "hmm", "mm", "mhm", "ah", "oh", "you", "the", "a", "i")
        return text in noise || text.length <= 1
    }
}
