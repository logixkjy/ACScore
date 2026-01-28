package com.kandc.acscore.session

interface SessionStateRepository {
    suspend fun getLastPickerContext(): LastPickerContext?
    suspend fun setLastPickerContext(ctx: LastPickerContext)
}