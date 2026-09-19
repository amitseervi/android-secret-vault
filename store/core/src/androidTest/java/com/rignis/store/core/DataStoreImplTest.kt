package com.rignis.store.core

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rignis.common.ExecutorFactory
import com.rignis.store.api.EncryptedDataEntry
import com.rignis.store.core.local.SecretRoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private class ImmediateExecutorFactory : ExecutorFactory {
    override val mainExecutor = java.util.concurrent.Executor { it.run() }
    override val backgroundExecutor = java.util.concurrent.Executor { it.run() }
    override val mainDispatcher = Dispatchers.Unconfined
    override val backgroundDispatcher = Dispatchers.Unconfined
}

// Exercises the real DataStoreImpl (versioning + soft delete) against an
// in-memory Room database, so this can be re-run on every change instead of
// re-verifying by hand via adb/sqlite each time.
@RunWith(AndroidJUnit4::class)
class DataStoreImplTest {

    private lateinit var db: SecretRoomDatabase
    private lateinit var dataStore: DataStoreImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, SecretRoomDatabase::class.java).build()
        dataStore = DataStoreImpl(DataStoreFactory.forTesting(db), ImmediateExecutorFactory())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entry(title: String, body: String = "body") =
        EncryptedDataEntry(title, body.toByteArray(), byteArrayOf(1, 2, 3))

    @Test
    fun insert_startsAtVersion1AndIsListed() = runBlocking {
        dataStore.insertItem(entry("Wi-Fi"))

        val refs = dataStore.getAllData().first()
        assertEquals(1, refs.size)
        assertEquals("Wi-Fi", refs[0].title)

        val raw = db.secretStoreDao().getRawById(refs[0].id)
        assertNotNull(raw)
        assertEquals(1L, raw!!.version)
        assertNull(raw.deletedAt)
        assertNull(raw.lastSyncedVersion)
        assertTrue(raw.updatedAt > 0)
    }

    @Test
    fun update_bumpsVersionAndChangesContentWithoutTouchingDeletedAt() = runBlocking {
        dataStore.insertItem(entry("Wi-Fi", "old-body"))
        val id = dataStore.getAllData().first()[0].id

        dataStore.updateExisting(id, entry("Wi-Fi 2", "new-body"))

        val item = dataStore.getDataById(id)
        assertNotNull(item)
        assertEquals("Wi-Fi 2", item!!.title)
        assertEquals("new-body", String(item.encryptedBody))

        val raw = db.secretStoreDao().getRawById(id)
        assertEquals(2L, raw!!.version)
        assertNull(raw.deletedAt)
    }

    @Test
    fun update_preservesLastSyncedVersionAcrossEdits() = runBlocking {
        dataStore.insertItem(entry("Wi-Fi"))
        val id = dataStore.getAllData().first()[0].id

        // Simulate a completed sync directly against the DAO (SyncDataStore
        // itself isn't implemented until Phase 5).
        db.secretStoreDao().let { dao ->
            val raw = dao.getRawById(id)!!
            dao.insertItem(raw.copy(lastSyncedVersion = raw.version))
        }

        dataStore.updateExisting(id, entry("Wi-Fi renamed"))

        val raw = db.secretStoreDao().getRawById(id)!!
        assertEquals(2L, raw.version)
        assertEquals(1L, raw.lastSyncedVersion)
    }

    @Test
    fun delete_isSoftAndExcludedFromListAndGetById() = runBlocking {
        dataStore.insertItem(entry("Wi-Fi"))
        val id = dataStore.getAllData().first()[0].id

        dataStore.deleteDataById(id)

        assertTrue(dataStore.getAllData().first().isEmpty())
        assertNull(dataStore.getDataById(id))

        val raw = db.secretStoreDao().getRawById(id)
        assertNotNull("soft delete must keep the row as a tombstone", raw)
        assertNotNull(raw!!.deletedAt)
        assertEquals(2L, raw.version)
    }

    @Test
    fun getDataById_returnsNullForUnknownId() = runBlocking {
        assertNull(dataStore.getDataById("does-not-exist"))
    }

    @Test
    fun getAllData_ordersCaseInsensitivelyByTitle() = runBlocking {
        dataStore.insertItem(entry("zebra"))
        dataStore.insertItem(entry("Apple"))
        dataStore.insertItem(entry("banana"))

        val titles = dataStore.getAllData().first().map { it.title }
        assertEquals(listOf("Apple", "banana", "zebra"), titles)
    }
}
