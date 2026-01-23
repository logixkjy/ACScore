package com.kandc.acscore.library.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val metadataDateFormat =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

fun formatDate(epochMillis: Long): String =
    metadataDateFormat.format(Date(epochMillis))