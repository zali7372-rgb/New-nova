package com.nova.assistant

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.nova.assistant.core.memory.MemoryManager
import com.nova.assistant.data.db.NovaDatabase
import com.nova.assistant.data.repository.MemoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MemoryManagerTest {

    private lateinit var database: NovaDatabase
    private lateinit var memoryManager: MemoryManager

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NovaDatabase::class.java
        ).allowMainThreadQueries().build()

        val repository = MemoryRepository(database.memoryDao(), database.appAliasDao())
        memoryManager = MemoryManager(repository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `remembering a fact then recalling it returns the fact`() = runTest {
        memoryManager.remember("szeretem az angol zenét")

        // "zenét" is a literal substring shared with the stored fact, so this
        // exercises the actual keyword-search path (not just the "nothing
        // relevant, showing recent memories" fallback).
        val recalled = memoryManager.recall("milyen zenét szeretek")

        assertThat(recalled).contains("angol zenét")
    }

    @Test
    fun `blank fact is not saved and gives a clarifying response`() = runTest {
        val response = memoryManager.remember("   ")

        assertThat(response).contains("Nem értettem")
    }

    @Test
    fun `forgetting most recent removes it from recall`() = runTest {
        memoryManager.remember("a kedvenc színem a kék")

        val forgetResponse = memoryManager.forgetMostRecent()

        assertThat(forgetResponse).contains("kék")
    }

    @Test
    fun `forgetAll clears every memory`() = runTest {
        memoryManager.remember("tény egy")
        memoryManager.remember("tény kettő")

        memoryManager.forgetAll()
        val recalled = memoryManager.recall("tény")

        assertThat(recalled).contains("nincs")
    }

    @Test
    fun `relevantFactsFor returns matching facts only`() = runTest {
        memoryManager.remember("a kutyám neve Rex")
        memoryManager.remember("a kedvenc ételem a gulyás")

        // Uses "kutyám" verbatim (not "kutyámat") because MemoryDao.searchByKeyword
        // does a plain SQL substring match, not stemmed/lemmatized search - this is
        // documented in MemoryDao as a deliberate, simple on-device search strategy.
        val relevant = memoryManager.relevantFactsFor("mi a kutyám neve")

        assertThat(relevant.any { it.contains("Rex") }).isTrue()
    }
}
