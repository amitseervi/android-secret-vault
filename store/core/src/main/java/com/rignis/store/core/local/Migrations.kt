package com.rignis.store.core.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object Migrations {
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `secret_data` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `secret_data` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `secret_data` ADD COLUMN `deleted_at` INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE `secret_data` ADD COLUMN `last_synced_version` INTEGER DEFAULT NULL")
            db.execSQL(
                "UPDATE `secret_data` SET `updated_at` = ?", arrayOf<Any>(System.currentTimeMillis())
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_secret_data_deleted_at` ON `secret_data` (`deleted_at`)"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `secret_backup_blob` (" +
                        "`secret_id` TEXT NOT NULL, `version` INTEGER NOT NULL, " +
                        "`bed` BLOB NOT NULL, `biv` BLOB NOT NULL, " +
                        "`key_epoch` TEXT NOT NULL, `staged_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`secret_id`))"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `secret_remote_staged` (" +
                        "`secret_id` TEXT NOT NULL, `remote_version` INTEGER NOT NULL, " +
                        "`remote_updated_at` INTEGER NOT NULL, `remote_deleted` INTEGER NOT NULL, " +
                        "`remote_file_id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`ed` BLOB NOT NULL, `iv` BLOB NOT NULL, `fetched_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`secret_id`))"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sync_item_state` (" +
                        "`secret_id` TEXT NOT NULL, `remote_file_id` TEXT, " +
                        "`remote_version` INTEGER, `remote_updated_at` INTEGER, " +
                        "`remote_deleted` INTEGER NOT NULL DEFAULT 0, `status` TEXT NOT NULL, " +
                        "`failure_kind` TEXT, `failure_detail` TEXT, " +
                        "`attempt_count` INTEGER NOT NULL DEFAULT 0, " +
                        "`last_attempt_at` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`secret_id`))"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_sync_item_state_status` ON `sync_item_state` (`status`)"
            )
        }
    }
}
