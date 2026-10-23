package com.xuan.fitai.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xuan.fitai.data.model.UserProfile
import com.xuan.fitai.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

import kotlinx.coroutines.flow.first

class OnboardingViewModel(private val userRepository: UserRepository) : ViewModel() {
    private val _profile = MutableStateFlow(UserProfile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _profile.value = userRepository.userProfile.first()
            } catch (e: Exception) {
                // Keep default
            } finally {
                _isLoaded.value = true
            }
        }
    }

    fun updateGoal(goal: String) { _profile.value = _profile.value.copy(goal = goal) }
    fun updateGender(gender: String) { _profile.value = _profile.value.copy(gender = gender) }
    fun updateAge(age: Int) { _profile.value = _profile.value.copy(age = age) }
    fun updateHeight(height: Float) { _profile.value = _profile.value.copy(height = height) }
    fun updateWeight(weight: Float) { _profile.value = _profile.value.copy(weight = weight) }
    fun updateActivityLevel(level: String) { _profile.value = _profile.value.copy(activityLevel = level) }
    fun updateDietPreference(pref: String) { _profile.value = _profile.value.copy(dietPreference = pref) }
    fun updateWorkoutExperience(exp: String) { _profile.value = _profile.value.copy(workoutExperience = exp) }

    val saving = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    fun saveProfile(onSuccess: () -> Unit) {
        if (saving.value) return
        saving.value = true
        error.value = null
        viewModelScope.launch {
            try {
                val profile = _profile.value
                require(profile.age in 1..120 && profile.height.isFinite() && profile.height in 50f..300f && profile.weight.isFinite() && profile.weight in 10f..1000f)
                userRepository.saveUserProfile(profile)
                onSuccess()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error.value = "設定儲存失敗，請再試一次" }
            finally { saving.value = false }
        }
    }

    class Factory(private val userRepository: UserRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return OnboardingViewModel(userRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
