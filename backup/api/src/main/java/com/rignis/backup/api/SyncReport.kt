package com.rignis.backup.api

enum class SyncTrigger {
    APP_OPEN, USER_REQUESTED, AFTER_UNLOCK, ISSUE_RETRY
}

data class SyncReport(
    val pushed: Int,
    val pulled: Int,
    val conflicts: Int,
    val failed: Int,
    val skippedNotStaged: Int,
    val finishedAt: Long
)
