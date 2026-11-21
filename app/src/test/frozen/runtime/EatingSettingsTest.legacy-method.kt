    /** Before the reward start the old rules decide alone: any reflection keeps the day and a week has its free day, whatever the configuration says. */
    @Test fun theLegacyRulesAreWhatTheyWere() {
        val records = kept(listOf(0, 1, 3, 4))
        val changed = records.saved(0, rules(maxPasses = 1, days = 2), QuestionsEdit.SetWhenAway(WhenAway.Excused))
        val notYet = on(30)
        listOf(records, changed).forEach { state ->
            assertEquals(mapOf(eatingRef to setOf(on(2))), MoEvaluation(state, onlyEating, on(4), away(2), rewardStart = notYet).trackerFreeDays())
        }
        assertEquals(
            InheritedMoEvaluation(records, onlyEating, on(4), away(2), rewardStart = notYet).mood.outcome(),
            InheritedMoEvaluation(changed, onlyEating, on(4), away(2), rewardStart = notYet).mood.outcome(),
        )
    }
