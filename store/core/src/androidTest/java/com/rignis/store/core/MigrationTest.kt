package com.rignis.store.core

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rignis.store.core.local.Migrations
import com.rignis.store.core.local.SecretRoomDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val testDb = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), SecretRoomDatabase::class.java
    )

    @Test
    fun migrate2To3_addsVersioningColumnsAndSyncTables() {
        helper.createDatabase(testDb, 2).apply {
            execSQL(
                "INSERT INTO secret_data (id, title, ed, iv) VALUES ('id1', 'title1', X'00', X'01')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 3, true, Migrations.MIGRATION_2_3)

        val cursor = db.query(
            "SELECT version, updated_at, deleted_at, last_synced_version FROM secret_data WHERE id='id1'"
        )
        assertTrue(cursor.moveToFirst())
        assertEquals(1L, cursor.getLong(0))
        assertTrue("updated_at should be backfilled to a real timestamp", cursor.getLong(1) > 0)
        assertTrue(cursor.isNull(2))
        assertTrue(cursor.isNull(3))
        cursor.close()

        // New tables must exist and be queryable (validates against the generated schema).
        db.query("SELECT * FROM secret_backup_blob").close()
        db.query("SELECT * FROM secret_remote_staged").close()
        db.query("SELECT * FROM sync_item_state").close()
    }

    @Test
    fun migrate3To4_addsBackupCiphertextColumnsToParkedRemote() {
        helper.createDatabase(testDb, 2).apply {
            close()
        }
        helper.runMigrationsAndValidate(testDb, 3, true, Migrations.MIGRATION_2_3).apply {
            execSQL(
                "INSERT INTO secret_remote_staged (secret_id, remote_version, remote_updated_at, " +
                        "remote_deleted, remote_file_id, title, ed, iv, fetched_at) " +
                        "VALUES ('id1', 1, 1000, 0, 'file1', 'title1', X'00', X'01', 2000)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 4, true, Migrations.MIGRATION_3_4)

        val cursor = db.query("SELECT bed, biv FROM secret_remote_staged WHERE secret_id='id1'")
        assertTrue(cursor.moveToFirst())
        assertEquals(0, cursor.getBlob(0).size)
        assertEquals(0, cursor.getBlob(1).size)
        cursor.close()
    }
}
