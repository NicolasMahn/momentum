    @Test fun lateEatingReflectionStaysRecordedButDoesNotKeepTheHabitDay() {
        val zone = ZoneId.systemDefault()
        val late = monday.plusDays(2).atTime(4, 1).atZone(zone).toInstant().toEpochMilli()
        val reflection = eatingEntry("late", monday.toString(), late)
        val state = AppState(questionEntries = listOf(reflection))
        val streak = inheritedHabitStreak(state, visible(MoNeed.Eating), monday.plusDays(3))
        assertEquals(0, streak.passes[MoNeed.Eating])
        assertEquals(0, streak.days) // The late reflection cannot create a completed day.
        assertEquals(0, streak.freezes)
        assertEquals(
            MoStatus.Missed,
            MoLedger.of(state.withInheritedTrackerConfigurations(), visible(MoNeed.Eating), monday.plusDays(3)).history(MoNeed.Eating)[monday.plusDays(1)],
        )
    }
