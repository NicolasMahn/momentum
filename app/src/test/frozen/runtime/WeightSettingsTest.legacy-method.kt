    /** Before the reward start the old rules decide alone: one free day a week and a week of six, whatever the configuration says. */
    @Test fun theLegacyRulesKeepTheirFreeDayAndTheirTarget() {
        val records = weighed(listOf(0, 1, 3, 4))
        val changed = records.saved(0, rules(maxPasses = 2, days = 3), MeasurementEdit.SetWhenAway(WhenAway.Due))
        val notYet = on(30)
        listOf(records, changed).forEach { state ->
            val mood = moMood(state, onlyWeight, on(4), away(2), rewardStart = notYet)
            assertEquals(MoWeekProgress(4, 6), mood.trackerWeekProgress[weightRef])
            assertEquals(mapOf(weightRef to setOf(on(2))), MoEvaluation(state, onlyWeight, on(4), away(2), rewardStart = notYet).trackerFreeDays())
        }
        assertEquals(
            InheritedMoEvaluation(records, onlyWeight, on(4), away(2), rewardStart = notYet).mood.outcome(),
            InheritedMoEvaluation(changed, onlyWeight, on(4), away(2), rewardStart = notYet).mood.outcome(),
        )
    }
