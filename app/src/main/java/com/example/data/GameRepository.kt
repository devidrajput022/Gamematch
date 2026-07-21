package com.example.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object DatabaseProvider {
    @Volatile
    private var INSTANCE: GameDatabase? = null

    fun getDatabase(context: Context): GameDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                GameDatabase::class.java,
                "memory_match_db"
            )
            .fallbackToDestructiveMigration()
            .build()
            INSTANCE = instance
            instance
        }
    }
}

class GameRepository(private val db: GameDatabase) {
    private val progressDao = db.levelProgressDao()
    private val profileDao = db.userProfileDao()
    private val achievementDao = db.achievementDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    val allProgress: Flow<List<LevelProgress>> = progressDao.getAllProgress()
    val userProfile: Flow<UserProfile?> = profileDao.getUserProfile()
    val achievements: Flow<List<AchievementEntity>> = achievementDao.getAllAchievements()

    init {
        // Seed database asynchronously on startup
        scope.launch {
            seedDatabaseIfNeeded()
        }
    }

    private suspend fun seedDatabaseIfNeeded() = withContext(Dispatchers.IO) {
        // 1. Seed User Profile if empty
        val currentProfile = profileDao.getProfileSync()
        if (currentProfile == null) {
            val defaultProfile = UserProfile(
                id = 1,
                coins = 150, // Give some starter coins so they can play with hints/undo early
                activeThemeId = "synthwave",
                unlockedThemes = "synthwave",
                activeTitle = "Novice Matcher",
                unlockedTitles = "Novice Matcher",
                dailyStreak = 0,
                lastDailyChallengeDate = ""
            )
            profileDao.insertProfile(defaultProfile)
        }

        // 2. Seed Level Progress if empty
        val existingProgress = progressDao.getAllProgress().firstOrNull()
        if (existingProgress.isNullOrEmpty()) {
            val defaultProgressList = (1..LevelManager.MAX_LEVELS).map { id ->
                LevelProgress(
                    levelId = id,
                    stars = 0,
                    highScore = 0,
                    isUnlocked = id == 1 // Only level 1 is unlocked by default
                )
            }
            progressDao.insertAll(defaultProgressList)
        }

        // 3. Seed Achievements if empty
        val existingAchievements = achievementDao.getAllAchievements().firstOrNull()
        if (existingAchievements.isNullOrEmpty()) {
            val defaultAchievements = listOf(
                AchievementEntity("first_win", "First Match", "Complete your first memory level", false, 0, 1),
                AchievementEntity("perfect_three", "Triple Star", "Earn 3 stars on any level", false, 0, 1),
                AchievementEntity("coin_baron", "Coin Collector", "Acquire 500 total coins", false, 0, 500),
                AchievementEntity("theme_collector", "Stylist", "Unlock your first custom level theme", false, 0, 1),
                AchievementEntity("sound_master", "Sound Expert", "Complete level 45 (Sound Memory)", false, 0, 1),
                AchievementEntity("sequence_sage", "Temporal Sage", "Complete level 80 (Sequence Memory)", false, 0, 1),
                AchievementEntity("daily_conqueror", "Daily Warrior", "Complete a daily matching challenge", false, 0, 1)
            )
            achievementDao.insertAchievements(defaultAchievements)
        }
    }

    // Progress updates
    suspend fun saveLevelResult(levelId: Int, stars: Int, score: Int) = withContext(Dispatchers.IO) {
        val currentLevelProgress = allProgress.firstOrNull()?.find { it.levelId == levelId }
        val oldStars = currentLevelProgress?.stars ?: 0
        val oldScore = currentLevelProgress?.highScore ?: 0

        // Only write if we got better results
        val newStars = maxOf(oldStars, stars)
        val newScore = maxOf(oldScore, score)

        progressDao.updateStarsAndScore(levelId, newStars, newScore)

        // Unlock next level if this is a win
        if (stars > 0 && levelId < LevelManager.MAX_LEVELS) {
            progressDao.unlockLevel(levelId + 1)
        }

        // Check level-based achievements
        updateLevelAchievements(levelId, stars)
    }

    private suspend fun updateLevelAchievements(levelId: Int, stars: Int) {
        if (stars > 0) {
            triggerAchievementProgress("first_win", 1)
        }
        if (stars == 3) {
            triggerAchievementProgress("perfect_three", 1)
        }
        if (levelId >= 45 && LevelManager.getLevel(levelId).mode == GameMode.SOUND_MEMORY && stars > 0) {
            triggerAchievementProgress("sound_master", 1)
        }
        if (levelId >= 80 && LevelManager.getLevel(levelId).mode == GameMode.SEQUENCE_MEMORY && stars > 0) {
            triggerAchievementProgress("sequence_sage", 1)
        }
    }

    // Coins and Profile management
    suspend fun addCoins(amount: Int) = withContext(Dispatchers.IO) {
        profileDao.addCoins(amount)
        val profile = profileDao.getProfileSync()
        if (profile != null) {
            updateAchievementProgressValue("coin_baron", profile.coins)
        }
    }

    suspend fun deductCoins(amount: Int): Boolean = withContext(Dispatchers.IO) {
        val profile = profileDao.getProfileSync() ?: return@withContext false
        if (profile.coins >= amount) {
            profileDao.addCoins(-amount)
            return@withContext true
        }
        return@withContext false
    }

    suspend fun selectTheme(themeId: String) = withContext(Dispatchers.IO) {
        profileDao.updateActiveTheme(themeId)
    }

    suspend fun buyTheme(themeId: String, cost: Int): Boolean = withContext(Dispatchers.IO) {
        if (deductCoins(cost)) {
            profileDao.unlockTheme(themeId)
            triggerAchievementProgress("theme_collector", 1)
            return@withContext true
        }
        return@withContext false
    }

    suspend fun selectTitle(title: String) = withContext(Dispatchers.IO) {
        profileDao.updateActiveTitle(title)
    }

    suspend fun unlockTitle(title: String) = withContext(Dispatchers.IO) {
        profileDao.unlockTitle(title)
    }

    suspend fun completeDailyChallenge(dateString: String, coinsReward: Int) = withContext(Dispatchers.IO) {
        val profile = profileDao.getProfileSync() ?: return@withContext
        val newStreak = if (profile.lastDailyChallengeDate == getPreviousDateString(dateString)) {
            profile.dailyStreak + 1
        } else {
            1
        }
        profileDao.updateDailyChallenge(dateString, newStreak)
        profileDao.addCoins(coinsReward)
        triggerAchievementProgress("daily_conqueror", 1)
    }

    private fun getPreviousDateString(dateString: String): String {
        // Date manipulation fallback string helper (rough previous day)
        // Highly resilient local keying
        return try {
            val parts = dateString.split("-")
            val year = parts[0].toInt()
            val month = parts[1].toInt()
            val day = parts[2].toInt()
            if (day > 1) {
                String.format("%04d-%02d-%02d", year, month, day - 1)
            } else {
                // rollover simplified
                String.format("%04d-%02d-%02d", year, if (month > 1) month - 1 else 12, 28)
            }
        } catch (e: Exception) {
            ""
        }
    }

    // Achievement Progress Updates
    suspend fun triggerAchievementProgress(id: String, addProgress: Int) = withContext(Dispatchers.IO) {
        val list = achievements.firstOrNull() ?: return@withContext
        val target = list.find { it.id == id } ?: return@withContext
        if (!target.isUnlocked) {
            val newProgress = (target.progress + addProgress).coerceAtMost(target.maxProgress)
            val isUnlockedNow = newProgress >= target.maxProgress
            achievementDao.updateAchievementProgress(id, newProgress, isUnlockedNow)
        }
    }

    suspend fun updateAchievementProgressValue(id: String, currentProgress: Int) = withContext(Dispatchers.IO) {
        val list = achievements.firstOrNull() ?: return@withContext
        val target = list.find { it.id == id } ?: return@withContext
        if (!target.isUnlocked) {
            val newProgress = currentProgress.coerceAtMost(target.maxProgress)
            val isUnlockedNow = newProgress >= target.maxProgress
            achievementDao.updateAchievementProgress(id, newProgress, isUnlockedNow)
        }
    }
}
