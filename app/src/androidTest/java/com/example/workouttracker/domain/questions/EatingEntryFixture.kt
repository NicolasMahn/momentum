package com.example.workouttracker.domain.questions

import com.example.workouttracker.testfixtures.EatingAmountQuestionId

import com.example.workouttracker.testfixtures.EatingQualityQuestionId

import com.example.workouttracker.testfixtures.EatingQuestionsId


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
