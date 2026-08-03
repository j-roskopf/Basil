package com.joetr.basil.domain.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StepDurationParserTest {
    @Test
    fun parsesSingleMinutes() {
        assertEquals(15, StepDurationParser.parse("Bake for 15 minutes"))
        assertEquals(5, StepDurationParser.parse("Simmer 5 min"))
    }

    @Test
    fun parsesRangeTakesUpperBound() {
        assertEquals(25, StepDurationParser.parse("Cook 20-25 minutes"))
        assertEquals(30, StepDurationParser.parse("Bake 20–30 min"))
    }

    @Test
    fun parsesHours() {
        assertEquals(120, StepDurationParser.parse("Roast 2 hours"))
        assertEquals(60, StepDurationParser.parse("Rest for an hour"))
        assertEquals(30, StepDurationParser.parse("Chill half an hour"))
    }

    @Test
    fun overnightReturnsNull() {
        assertNull(StepDurationParser.parse("Marinate overnight"))
    }

    @Test
    fun noMatchReturnsNull() {
        assertNull(StepDurationParser.parse("Stir until combined"))
    }
}
