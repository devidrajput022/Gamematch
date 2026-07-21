package com.example.data

enum class GameMode {
    SHADOW_MATCH,
    SOUND_MEMORY,
    SEQUENCE_MEMORY
}

data class GameLevel(
    val id: Int,
    val mode: GameMode,
    val title: String,
    val cardCount: Int, // total cards in grid (4, 6, 8, 12, 16)
    val timeLimitSeconds: Int, // maximum seconds to finish, 0 for casual
    val allowedErrors: Int = -1, // -1 means unlimited
    val sequenceLength: Int = 0, // only for sequence mode
    val revealSpeedMs: Long = 1000L, // flash speed for sequence, or initial reveal time for matches
    val coinsReward: Int = 10
)

object LevelManager {
    const val MAX_LEVELS = 100

    fun getLevel(id: Int): GameLevel {
        val levelId = id.coerceIn(1, MAX_LEVELS)
        val coins = 10 + (levelId / 5) * 5

        return when {
            // Level 1 - 33: Shadow Match
            levelId <= 33 -> {
                val (cardCount, time, title, errors) = when {
                    levelId <= 5 -> Quad(4, 0, "Shadow Sprout", -1)
                    levelId <= 12 -> Quad(8, 60, "Shadow Novice", -1)
                    levelId <= 22 -> Quad(12, 45, "Shadow Sleuth", 8)
                    else -> Quad(16, 30, "Shadow Legend", 5)
                }
                GameLevel(
                    id = levelId,
                    mode = GameMode.SHADOW_MATCH,
                    title = title,
                    cardCount = cardCount,
                    timeLimitSeconds = time,
                    allowedErrors = errors,
                    coinsReward = coins
                )
            }
            // Level 34 - 66: Sound Memory
            levelId <= 66 -> {
                val (cardCount, time, title, errors) = when {
                    levelId <= 38 -> Quad(4, 45, "Acoustic Starter", -1)
                    levelId <= 46 -> Quad(8, 40, "Echo Seeker", -1)
                    levelId <= 56 -> Quad(12, 30, "Chamber Solver", 8)
                    else -> Quad(16, 25, "Sonic Virtuoso", 5)
                }
                GameLevel(
                    id = levelId,
                    mode = GameMode.SOUND_MEMORY,
                    title = title,
                    cardCount = cardCount,
                    timeLimitSeconds = time,
                    allowedErrors = errors,
                    coinsReward = coins
                )
            }
            // Level 67 - 100: Sequence Memory
            else -> {
                val (cardCount, seqLength, speed, title) = when {
                    levelId <= 72 -> Quad(4, 3, 1000L, "Pattern Sprout")
                    levelId <= 80 -> Quad(6, 4, 800L, "Memory Linker")
                    levelId <= 90 -> Quad(9, 5, 600L, "Matrix Echo")
                    else -> Quad(12, 6 + (levelId - 90) / 3, 450L, "Temporal Sage")
                }
                GameLevel(
                    id = levelId,
                    mode = GameMode.SEQUENCE_MEMORY,
                    title = title as String,
                    cardCount = cardCount as Int,
                    timeLimitSeconds = 0, // Sequence memory is not time-limited, but memory-speed driven
                    sequenceLength = seqLength as Int,
                    revealSpeedMs = speed as Long,
                    coinsReward = coins
                )
            }
        }
    }

    // Helper class for level generator packing
    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    // Generate standard level nodes for map display
    fun getLevelList(): List<GameLevel> {
        return (1..MAX_LEVELS).map { getLevel(it) }
    }
}
