package com.xuan.fitai.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.xuan.fitai.data.model.AppearanceSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppearanceSettingsTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun wallpaperModeRetainsCustomHueAndResetRestoresDefault() = runTest {
        val store = UserPreferenceStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("appearance.preferences_pb") })
        assertEquals(AppearanceSettings(), store.appearanceFlow.first())
        store.saveAppearance(AppearanceSettings(customHue = 275))
        assertEquals(AppearanceSettings(customHue = 275), store.appearanceFlow.first())
        store.saveAppearance(AppearanceSettings(useWallpaperColors = true, customHue = 275))
        assertEquals(AppearanceSettings(useWallpaperColors = true, customHue = 275), store.appearanceFlow.first())
        store.saveAppearance(AppearanceSettings())
        assertEquals(AppearanceSettings(), store.appearanceFlow.first())
    }
}
