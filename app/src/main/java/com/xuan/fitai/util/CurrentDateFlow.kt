package com.xuan.fitai.util

import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

// Re-evaluate after resume as well as while a screen stays open across midnight.
fun currentDateFlow() = flow {
    while (true) {
        emit(LocalDate.now())
        delay(1_000)
    }
}.distinctUntilChanged()
