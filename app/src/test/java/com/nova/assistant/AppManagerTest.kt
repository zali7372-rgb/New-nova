package com.nova.assistant

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.nova.assistant.data.db.AppAliasEntity
import com.nova.assistant.data.db.NovaDatabase
import com.nova.assistant.data.repository.MemoryRepository
import com.nova.assistant.system.apps.AppManager
import com.nova.assistant.system.apps.LaunchResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AppManagerTest {

    private lateinit var database: NovaDatabase
    private lateinit var appManager: AppManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, NovaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = MemoryRepository(database.memoryDao(), database.appAliasDao())
        appManager = AppManager(context, repository)

        installFakeApp(context, "com.discord", "Discord")
        installFakeApp(context, "com.google.android.youtube", "YouTube")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `exact label match finds the right app`() = runTest {
        val match = appManager.findBestMatch("Discord")
        assertThat(match?.packageName).isEqualTo("com.discord")
    }

    @Test
    fun `known alias matches even when it differs from label`() = runTest {
        val match = appManager.findBestMatch("yt")
        assertThat(match?.packageName).isEqualTo("com.google.android.youtube")
    }

    @Test
    fun `custom user alias is respected`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = MemoryRepository(database.memoryDao(), database.appAliasDao())
        repository.addAlias("com.discord", "dizkord")

        val match = appManager.findBestMatch("dizkord")

        assertThat(match?.packageName).isEqualTo("com.discord")
    }

    @Test
    fun `unmatched query returns no launch result and lists suggestions`() = runTest {
        val result = appManager.launch("teljesenismeretlenapp")
        assertThat(result).isInstanceOf(LaunchResult.NotFound::class.java)
    }

    @Test
    fun `fuzzy match handles minor typos`() = runTest {
        val match = appManager.findBestMatch("discrod") // transposed letters
        assertThat(match?.packageName).isEqualTo("com.discord")
    }

    private fun installFakeApp(context: android.content.Context, packageName: String, label: String) {
        val applicationInfo = ApplicationInfo().apply {
            this.packageName = packageName
            this.flags = 0
            // AppManager resolves labels via packageManager.getApplicationLabel(appInfo),
            // which reads ApplicationInfo.nonLocalizedLabel - NOT ResolveInfo's label. Both
            // must be set so the shadow PackageManager returns a real label instead of
            // falling back to the raw package name.
            this.nonLocalizedLabel = label
        }
        val activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            this.name = "$packageName.MainActivity"
            this.applicationInfo = applicationInfo
        }
        val resolveInfo = ResolveInfo().apply {
            this.activityInfo = activityInfo
            this.nonLocalizedLabel = label
        }
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        shadowOf(context.packageManager).addResolveInfoForIntent(launcherIntent, resolveInfo)
    }
}
