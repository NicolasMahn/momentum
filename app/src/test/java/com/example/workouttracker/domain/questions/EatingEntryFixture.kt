package com.example.workouttracker.domain.questions

import com.example.workouttracker.domain.compatibility.EatingAmountQuestionId

import com.example.workouttracker.domain.compatibility.EatingQualityQuestionId

import com.example.workouttracker.domain.compatibility.EatingQuestionsId


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
