package com.xuan.fitai.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.xuan.fitai.data.model.AppearanceSettings
import com.xuan.fitai.data.model.ThemeMode
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

    @Test fun themeModeAndCustomColorAttributesPersistCorrectly() = runTest {
        val store = UserPreferenceStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("appearance_custom.preferences_pb") })
        val custom = AppearanceSettings(
            themeMode = ThemeMode.DARK,
            customHue = 210,
            customSaturation = 0.65f,
            customHex = "#3B82F6"
        )
        store.saveAppearance(custom)
        val loaded = store.appearanceFlow.first()
        assertEquals(ThemeMode.DARK, loaded.themeMode)
        assertEquals(210, loaded.customHue)
        assertEquals(0.65f, loaded.customSaturation ?: 0f, 0.001f)
        assertEquals("#3B82F6", loaded.customHex)
    }
    @Test fun fontScalePersistsAndInvalidValuesCannotReachLayout() = runTest {
        val store = UserPreferenceStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("font.preferences_pb") })
        val appearance = AppearanceSettings(customHue = 210, fontScale = 1.3f)
        store.saveAppearance(appearance)
        assertEquals(appearance, store.appearanceFlow.first())
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            store.saveAppearance(appearance.copy(fontScale = invalid))
            val loaded = store.appearanceFlow.first()
            assertEquals(1f, loaded.fontScale, 0.001f)
            assertEquals(210, loaded.customHue)
        }
        store.saveAppearance(appearance.copy(fontScale = 0f))
        assertEquals(0.85f, store.appearanceFlow.first().fontScale, 0.001f)
        store.saveAppearance(appearance.copy(fontScale = 9f))
        assertEquals(1.3f, store.appearanceFlow.first().fontScale, 0.001f)
    }

}
