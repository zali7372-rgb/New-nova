package com.nova.assistant.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User-customizable alias for an installed app's package, e.g.
 * packageName="com.discord" alias="disc". AppManager seeds sensible defaults
 * on first run (see AppManager.DEFAULT_ALIASES) and the user can add more via
 * AliasManagerActivity.
 */
@Entity(tableName = "app_aliases", primaryKeys = ["packageName", "alias"])
data class AppAliasEntity(
    val packageName: String,
    val alias: String
)
