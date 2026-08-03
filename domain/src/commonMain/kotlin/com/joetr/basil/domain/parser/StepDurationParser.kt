package com.joetr.basil.domain.parser

/**
 * Parses step duration from free text. Shared logic with the Edge Function TypeScript twin.
 */
public object StepDurationParser {
    private val minutePattern = Regex(
        """(?i)(\d+)\s*(?:-|–|to)\s*(\d+)\s*(?:min(?:ute)?s?|m)\b""",
    )
    private val singleMinutePattern = Regex(
        """(?i)(\d+)\s*(?:min(?:ute)?s?|m)\b""",
    )
    private val hourPattern = Regex(
        """(?i)(\d+)\s*(?:hr|hour|hours|h)\b""",
    )
    private val halfHourPattern = Regex("""(?i)half\s+an?\s+hour""")
    private val anHourPattern = Regex("""(?i)an?\s+hour\b""")
    private val overnightPattern = Regex("""(?i)overnight""")

    public fun parse(text: String): Int? {
        if (overnightPattern.containsMatchIn(text)) return null

        minutePattern.find(text)?.let { match ->
            return match.groupValues[2].toIntOrNull()
        }

        hourPattern.find(text)?.let { match ->
            return match.groupValues[1].toIntOrNull()?.times(60)
        }

        halfHourPattern.find(text)?.let { return 30 }
        anHourPattern.find(text)?.let { return 60 }

        singleMinutePattern.find(text)?.let { match ->
            return match.groupValues[1].toIntOrNull()
        }

        return null
    }
}
