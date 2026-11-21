    /**
     * Weight beside a joined measurement that is logged every day: its configuration, its records,
     * its bank, its week's earning and everything Mo makes of it are what they are without the
     * measurement. The streak gains the measurement's standing and nothing else.
     */
    @Test fun weightIsWhatItWasBesideAJoinedMeasurementThatIsLogged() {
        val away = HolidaySchedule(listOf(DayRange(on(9), on(10))))
        listOf(HolidaySchedule(), away).forEach { daysOff ->
            val alone = history(withoutWeight = setOf(4, 9, 17)).withoutTheWaist()
            val beside = history(withoutWeight = setOf(4, 9, 17)).joined(day = 2)
            (3L..20L).forEach { today ->
                val without = streak(alone, today, daysOff = daysOff)
                val with = streak(beside, today, daysOff = daysOff)
                assertEquals("day $today", InheritedMoEvaluation(alone, onlyWeight, on(today), daysOff).streak,
                    InheritedMoEvaluation(beside, onlyWeight, on(today), daysOff).streak.copy(measurements = emptyMap()))
                assertEquals("day $today", setOf(waistRef), with.standings.keys.filter { it.type.value == "measurement" && it != weightRef }.toSet())
                val moodWithout = moMood(alone, onlyWeight, on(today), daysOff)
                val moodWith = moMood(beside, onlyWeight, on(today), daysOff)
                assertEquals("day $today", InheritedMoEvaluation(alone, onlyWeight, on(today), daysOff).mood,
                    InheritedMoEvaluation(beside, onlyWeight, on(today), daysOff).mood.copy(
                        streak = InheritedMoEvaluation(beside, onlyWeight, on(today), daysOff).streak.copy(measurements = emptyMap()),
                    ))
                assertEquals("day $today", moodWithout.state, moodWith.state)
                assertEquals("day $today", moodWithout.streak.standings[weightRef], moodWith.streak.standings[weightRef])
            }
            assertEquals(alone.recordsOf("weight"), beside.recordsOf("weight"))
            assertEquals(alone.measurements.single(), beside.measurements.first())
        }
    }
