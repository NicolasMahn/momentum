    /** Before the reward start the old rules decide alone: any log keeps the day and a week has its free day, whatever the configuration says. */
    @Test fun theLegacyRulesAreWhatTheyWere() {
        val records = read(0 to 10, 1 to 5, 3 to 20, 4 to 1)
        val changed = records.saved(0, TimeEdit.SetBankSize(1), notDue)
        val notYet = on(30)
        listOf(records, changed).forEach { state ->
            assertEquals(mapOf(readingRef to setOf(on(2))), MoEvaluation(state, onlyReading, on(4), away(2), rewardStart = notYet).trackerFreeDays())
        }
        assertEquals(
            InheritedMoEvaluation(records, onlyReading, on(4), away(2), rewardStart = notYet).mood.outcome(),
            InheritedMoEvaluation(changed, onlyReading, on(4), away(2), rewardStart = notYet).mood.outcome(),
        )
        assertEquals(false, InheritedMoEvaluation(changed, onlyReading, on(4), away(2), rewardStart = notYet).mood.usesWeeklyReading)
    }
