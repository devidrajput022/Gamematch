package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// ----------------------------------------------------------------------------
// ENTITIES
// ----------------------------------------------------------------------------

@Entity(tableName = "level_progress")
data class LevelProgress(
    @PrimaryKey val levelId: Int,
    val stars: Int, // 0 to 3
    val highScore: Int,
    val isUnlocked: Boolean
)

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val coins: Int = 100,
    val activeThemeId: String = "synthwave",
    val unlockedThemes: String = "synthwave", // comma-separated values
    val activeTitle: String = "Novice Matcher",
    val unlockedTitles: String = "Novice Matcher", // comma-separated values
    val dailyStreak: Int = 0,
    val lastDailyChallengeDate: String = "" // format "YYYY-MM-DD"
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val isUnlocked: Boolean = false,
    val progress: Int = 0,
    val maxProgress: Int = 1
)

// ----------------------------------------------------------------------------
// DAOS
// ----------------------------------------------------------------------------

@Dao
interface LevelProgressDao {
    @Query("SELECT * FROM level_progress ORDER BY levelId ASC")
    fun getAllProgress(): Flow<List<LevelProgress>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: LevelProgress)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(progressList: List<LevelProgress>)

    @Query("UPDATE level_progress SET isUnlocked = 1 WHERE levelId = :levelId")
    suspend fun unlockLevel(levelId: Int)

    @Query("UPDATE level_progress SET stars = :stars, highScore = :score WHERE levelId = :levelId")
    suspend fun updateStarsAndScore(levelId: Int, stars: Int, score: Int)
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfile)

    @Query("UPDATE user_profile SET coins = coins + :amount WHERE id = 1")
    suspend fun addCoins(amount: Int)

    @Query("UPDATE user_profile SET coins = :amount WHERE id = 1")
    suspend fun setCoins(amount: Int)

    @Query("UPDATE user_profile SET activeThemeId = :themeId WHERE id = 1")
    suspend fun updateActiveTheme(themeId: String)

    @Transaction
    suspend fun unlockTheme(themeId: String) {
        val profile = getProfileSync() ?: UserProfile()
        val themes = profile.unlockedThemes.split(",").toMutableSet()
        if (themes.add(themeId)) {
            val updatedThemes = themes.joinToString(",")
            updateUnlockedThemes(updatedThemes)
        }
    }

    @Query("UPDATE user_profile SET unlockedThemes = :themes WHERE id = 1")
    suspend fun updateUnlockedThemes(themes: String)

    @Query("UPDATE user_profile SET activeTitle = :title WHERE id = 1")
    suspend fun updateActiveTitle(title: String)

    @Transaction
    suspend fun unlockTitle(titleString: String) {
        val profile = getProfileSync() ?: UserProfile()
        val titles = profile.unlockedTitles.split(",").toMutableSet()
        if (titles.add(titleString)) {
            val updatedTitles = titles.joinToString(",")
            updateUnlockedTitles(updatedTitles)
        }
    }

    @Query("UPDATE user_profile SET unlockedTitles = :titles WHERE id = 1")
    suspend fun updateUnlockedTitles(titles: String)

    @Query("UPDATE user_profile SET dailyStreak = :streak, lastDailyChallengeDate = :dateString WHERE id = 1")
    suspend fun updateDailyChallenge(dateString: String, streak: Int)

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfileSync(): UserProfile?
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements")
    fun getAllAchievements(): Flow<List<AchievementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievements(achievements: List<AchievementEntity>)

    @Query("UPDATE achievements SET progress = :progress, isUnlocked = :isUnlocked WHERE id = :id")
    suspend fun updateAchievementProgress(id: String, progress: Int, isUnlocked: Boolean)

    @Query("UPDATE achievements SET isUnlocked = 1 WHERE id = :id")
    suspend fun unlockAchievement(id: String)
}

// ----------------------------------------------------------------------------
// DATABASE
// ----------------------------------------------------------------------------

@Database(
    entities = [LevelProgress::class, UserProfile::class, AchievementEntity::class],
    version = 1,
    exportSchema = false
)
abstract class GameDatabase : RoomDatabase() {
    abstract fun levelProgressDao(): LevelProgressDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun achievementDao(): AchievementDao
}
