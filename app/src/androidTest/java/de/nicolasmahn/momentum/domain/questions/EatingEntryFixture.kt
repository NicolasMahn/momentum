package de.nicolasmahn.momentum.domain.questions

import de.nicolasmahn.momentum.testfixtures.EatingAmountQuestionId

import de.nicolasmahn.momentum.testfixtures.EatingQualityQuestionId

import de.nicolasmahn.momentum.testfixtures.EatingQuestionsId


/** A seeded questions entry, with absent answers left absent. */
fun eatingEntry(
    id: String,
    dateKey: String,
    timestampMillis: Long,
    quality: String? = null,
    amount: String? = null,
    note: String? = null,
): QuestionEntry = QuestionEntry(
    id = id,
    viewId = EatingQuestionsId,
    dateKey = dateKey,
    timestampMillis = timestampMillis,
    answers = listOfNotNull(
        quality?.let { EatingQualityQuestionId to it },
        amount?.let { EatingAmountQuestionId to it },
    ).toMap(),
    note = note,
)
