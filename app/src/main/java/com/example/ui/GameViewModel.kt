package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioSynthesizer
import com.example.data.DatabaseProvider
import com.example.data.GameLevel
import com.example.data.GameMode
import com.example.data.GameRepository
import com.example.data.LevelManager
import com.example.data.LevelProgress
import com.example.data.UserProfile
import com.example.data.AchievementEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Screen {
    Home,
    LevelMap,
    Gameplay,
    Themes,
    Achievements
}

enum class GameStatus {
    NOT_STARTED,
    SEQUENCE_PREVIEW,
    PLAYING,
    LEVEL_COMPLETE,
    GAME_OVER
}

data class MemoryCard(
    val id: Int,
    val valueId: Int, // Represents the icon/color pair
    val isFlipped: Boolean = false,
    val isMatched: Boolean = false,
    val isShadow: Boolean = false, // True for shadow cards in SHADOW_MATCH mode
    val soundFrequency: Float = 0f, // Tone played on tap
    val isHighlighted: Boolean = false // Hint flashing
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: GameRepository
    private val prefs = application.getSharedPreferences("game_settings", android.content.Context.MODE_PRIVATE)

    private val _soundOn = MutableStateFlow(prefs.getBoolean("sound_on", true))
    val soundOn: StateFlow<Boolean> = _soundOn.asStateFlow()

    private val _darkMode = MutableStateFlow(prefs.getBoolean("dark_mode", true))
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    fun toggleSound() {
        val newValue = !_soundOn.value
        _soundOn.value = newValue
        prefs.edit().putBoolean("sound_on", newValue).apply()
        com.example.audio.AudioSynthesizer.isSoundEnabled = newValue
    }

    fun toggleDarkMode() {
        val newValue = !_darkMode.value
        _darkMode.value = newValue
        prefs.edit().putBoolean("dark_mode", newValue).apply()
    }

    init {
        val db = DatabaseProvider.getDatabase(application)
        repository = GameRepository(db)
        com.example.audio.AudioSynthesizer.isSoundEnabled = prefs.getBoolean("sound_on", true)
    }

    // Database flows
    val levelProgress: StateFlow<List<LevelProgress>> = repository.allProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProfile: StateFlow<UserProfile?> = repository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val achievements: StateFlow<List<AchievementEntity>> = repository.achievements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Screen state
    private val _currentScreen = MutableStateFlow(Screen.Home)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Active gameplay state
    private val _gameLevel = MutableStateFlow<GameLevel?>(null)
    val gameLevel: StateFlow<GameLevel?> = _gameLevel.asStateFlow()

    private val _isDailyChallenge = MutableStateFlow(false)
    val isDailyChallenge: StateFlow<Boolean> = _isDailyChallenge.asStateFlow()

    private val _gameStatus = MutableStateFlow(GameStatus.NOT_STARTED)
    val gameStatus: StateFlow<GameStatus> = _gameStatus.asStateFlow()

    private val _boardCards = MutableStateFlow<List<MemoryCard>>(emptyList())
    val boardCards: StateFlow<List<MemoryCard>> = _boardCards.asStateFlow()

    // Sequence memory details
    private val _currentSequence = MutableStateFlow<List<Int>>(emptyList())
    val currentSequence: StateFlow<List<Int>> = _currentSequence.asStateFlow()

    private val _sequenceRevealIndex = MutableStateFlow(-1)
    val sequenceRevealIndex: StateFlow<Int> = _sequenceRevealIndex.asStateFlow()

    private val _playerSequenceProgress = MutableStateFlow(0)
    val playerSequenceProgress: StateFlow<Int> = _playerSequenceProgress.asStateFlow()

    // Mechanics & Stats
    private val _remainingTime = MutableStateFlow(0)
    val remainingTime: StateFlow<Int> = _remainingTime.asStateFlow()

    private val _movesCount = MutableStateFlow(0)
    val movesCount: StateFlow<Int> = _movesCount.asStateFlow()

    private val _errorsCount = MutableStateFlow(0)
    val errorsCount: StateFlow<Int> = _errorsCount.asStateFlow()

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    private val _undoCount = MutableStateFlow(3)
    val undoCount: StateFlow<Int> = _undoCount.asStateFlow()

    private val _hintsCount = MutableStateFlow(3)
    val hintsCount: StateFlow<Int> = _hintsCount.asStateFlow()

    // Undo Snapshot
    private var cardSelectionHistory = mutableListOf<List<MemoryCard>>()
    private var firstSelectedCardIndex: Int? = null
    private var secondSelectedCardIndex: Int? = null
    private var isProcessingFlip = false

    private var timerJob: Job? = null
    private var sequenceJob: Job? = null

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
        if (screen != Screen.Gameplay) {
            resetGameState()
        }
    }

    private fun resetGameState() {
        timerJob?.cancel()
        sequenceJob?.cancel()
        _gameLevel.value = null
        _gameStatus.value = GameStatus.NOT_STARTED
        _boardCards.value = emptyList()
        _currentSequence.value = emptyList()
        _sequenceRevealIndex.value = -1
        _playerSequenceProgress.value = 0
        _remainingTime.value = 0
        _movesCount.value = 0
        _errorsCount.value = 0
        _score.value = 0
        _undoCount.value = 3
        _hintsCount.value = 3
        firstSelectedCardIndex = null
        secondSelectedCardIndex = null
        cardSelectionHistory.clear()
        isProcessingFlip = false
    }

    // ----------------------------------------------------------------------------
    // GAME LOOPS & INITIALIZATION
    // ----------------------------------------------------------------------------

    fun startLevel(level: GameLevel, isDaily: Boolean = false) {
        resetGameState()
        _gameLevel.value = level
        _isDailyChallenge.value = isDaily
        _undoCount.value = if (isDaily) 2 else 3
        _hintsCount.value = if (isDaily) 2 else 3

        // Setup board
        setupBoardForLevel(level)

        _currentScreen.value = Screen.Gameplay

        if (level.mode == GameMode.SEQUENCE_MEMORY) {
            startSequencePreview(level)
        } else {
            _gameStatus.value = GameStatus.PLAYING
            if (level.timeLimitSeconds > 0) {
                _remainingTime.value = level.timeLimitSeconds
                startTimer()
            }
        }
    }

    private fun setupBoardForLevel(level: GameLevel) {
        val pairsCount = level.cardCount / 2
        val cards = mutableListOf<MemoryCard>()

        when (level.mode) {
            GameMode.SHADOW_MATCH -> {
                // Generate pairs: one normal, one shadow
                val values = (0 until 12).shuffled().take(pairsCount)
                var cardId = 0
                for (value in values) {
                    cards.add(MemoryCard(id = cardId++, valueId = value, isShadow = false))
                    cards.add(MemoryCard(id = cardId++, valueId = value, isShadow = true))
                }
            }
            GameMode.SOUND_MEMORY -> {
                // Generate pairs: hidden cards with matching synthesized sound waves
                val frequencies = AudioSynthesizer.pairFrequencies.shuffled().take(pairsCount)
                var cardId = 0
                for ((index, freq) in frequencies.withIndex()) {
                    cards.add(MemoryCard(id = cardId++, valueId = index, soundFrequency = freq))
                    cards.add(MemoryCard(id = cardId++, valueId = index, soundFrequency = freq))
                }
            }
            GameMode.SEQUENCE_MEMORY -> {
                // Generates flat interactive nodes, numbered or blank
                for (id in 0 until level.cardCount) {
                    cards.add(MemoryCard(id = id, valueId = id))
                }
            }
        }

        _boardCards.value = cards.shuffled()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_remainingTime.value > 0 && _gameStatus.value == GameStatus.PLAYING) {
                delay(1000)
                _remainingTime.value -= 1
            }
            if (_remainingTime.value == 0 && _gameStatus.value == GameStatus.PLAYING) {
                handleGameOver()
            }
        }
    }

    // ----------------------------------------------------------------------------
    // SEQUENCE MEMORY LOGIC
    // ----------------------------------------------------------------------------

    private fun startSequencePreview(level: GameLevel) {
        _gameStatus.value = GameStatus.SEQUENCE_PREVIEW
        _sequenceRevealIndex.value = -1
        _playerSequenceProgress.value = 0

        sequenceJob?.cancel()
        sequenceJob = viewModelScope.launch {
            delay(1000) // initial delay
            val count = level.cardCount
            val sequence = List(level.sequenceLength) { (0 until count).random() }
            _currentSequence.value = sequence

            for (i in sequence.indices) {
                val cardIndex = sequence[i]
                _sequenceRevealIndex.value = i

                // Highlight the card
                _boardCards.value = _boardCards.value.mapIndexed { index, card ->
                    if (index == cardIndex) card.copy(isHighlighted = true, isFlipped = true) else card.copy(isHighlighted = false, isFlipped = false)
                }

                // Play custom procedural tone for reveal
                AudioSynthesizer.playSequenceReveal(i)

                delay(level.revealSpeedMs)

                // Un-highlight
                _boardCards.value = _boardCards.value.map { card ->
                    card.copy(isHighlighted = false, isFlipped = false)
                }

                delay(200) // small space between flashes
            }

            _sequenceRevealIndex.value = -1
            _gameStatus.value = GameStatus.PLAYING
            _boardCards.value = _boardCards.value.map { it.copy(isFlipped = false, isHighlighted = false) }
        }
    }

    // ----------------------------------------------------------------------------
    // PLAY INTERACTIONS
    // ----------------------------------------------------------------------------

    fun handleCardTap(index: Int) {
        if (_gameStatus.value != GameStatus.PLAYING || isProcessingFlip) return

        val level = _gameLevel.value ?: return
        val currentCards = _boardCards.value
        val card = currentCards.getOrNull(index) ?: return

        if (card.isFlipped || card.isMatched) return

        when (level.mode) {
            GameMode.SEQUENCE_MEMORY -> handleSequenceCardTap(index)
            else -> handleMatchingCardTap(index)
        }
    }

    private fun handleMatchingCardTap(index: Int) {
        val currentCards = _boardCards.value
        val tappedCard = currentCards[index]

        // Sound mode plays its individual pluck frequency immediately
        if (tappedCard.soundFrequency > 0f) {
            AudioSynthesizer.playTone(tappedCard.soundFrequency)
        } else {
            // General matching modes play a generic soft pluck
            AudioSynthesizer.playTone(400f + tappedCard.valueId * 30, 150)
        }

        // Save current board snapshot in history for UNDO before making changes
        cardSelectionHistory.add(currentCards.map { it.copy() })
        if (cardSelectionHistory.size > 10) cardSelectionHistory.removeAt(0)

        // Flip card
        _boardCards.value = currentCards.mapIndexed { i, card ->
            if (i == index) card.copy(isFlipped = true) else card
        }

        if (firstSelectedCardIndex == null) {
            firstSelectedCardIndex = index
        } else if (secondSelectedCardIndex == null && index != firstSelectedCardIndex) {
            secondSelectedCardIndex = index
            _movesCount.value += 1
            checkForMatch()
        }
    }

    private fun checkForMatch() {
        val idx1 = firstSelectedCardIndex ?: return
        val idx2 = secondSelectedCardIndex ?: return

        val cards = _boardCards.value
        val card1 = cards[idx1]
        val card2 = cards[idx2]

        isProcessingFlip = true

        viewModelScope.launch {
            delay(800) // keep visible briefly

            val isMatch = card1.valueId == card2.valueId && card1.isShadow != card2.isShadow

            if (isMatch) {
                // Mark matched
                _boardCards.value = _boardCards.value.mapIndexed { i, card ->
                    if (i == idx1 || i == idx2) card.copy(isMatched = true, isFlipped = true) else card
                }
                _score.value += 100
                AudioSynthesizer.playMatchSuccess()

                // Check win condition
                if (_boardCards.value.all { it.isMatched }) {
                    handleLevelWin()
                }
            } else {
                // Incorrect match
                _errorsCount.value += 1
                _score.value = maxOf(0, _score.value - 20)
                AudioSynthesizer.playMatchFailure()

                val level = _gameLevel.value
                if (level != null && level.allowedErrors != -1 && _errorsCount.value >= level.allowedErrors) {
                    handleGameOver()
                } else {
                    // Flip back
                    _boardCards.value = _boardCards.value.mapIndexed { i, card ->
                        if (i == idx1 || i == idx2) card.copy(isFlipped = false) else card
                    }
                }
            }

            firstSelectedCardIndex = null
            secondSelectedCardIndex = null
            isProcessingFlip = false
        }
    }

    private fun handleSequenceCardTap(index: Int) {
        val sequence = _currentSequence.value
        val progress = _playerSequenceProgress.value
        val expectedCardIndex = sequence.getOrNull(progress) ?: return

        // Save state for undo (even though sequence has distinct undo mechanic)
        cardSelectionHistory.add(_boardCards.value.map { it.copy() })

        // Play the reveal note based on tapping order
        AudioSynthesizer.playSequenceReveal(progress)

        if (index == expectedCardIndex) {
            // Correct click
            _boardCards.value = _boardCards.value.mapIndexed { i, card ->
                if (i == index) card.copy(isFlipped = true, isHighlighted = true) else card
            }

            viewModelScope.launch {
                delay(300)
                _boardCards.value = _boardCards.value.map { card ->
                    card.copy(isFlipped = false, isHighlighted = false)
                }
            }

            _playerSequenceProgress.value += 1
            _score.value += 50

            if (_playerSequenceProgress.value == sequence.size) {
                handleLevelWin()
            }
        } else {
            // Incorrect click - reset progress, buzz, and show red flash
            _errorsCount.value += 1
            AudioSynthesizer.playMatchFailure()

            _playerSequenceProgress.value = 0

            // Flash all cards in red briefly (highlighted)
            _boardCards.value = _boardCards.value.map { it.copy(isHighlighted = true, isFlipped = true) }
            viewModelScope.launch {
                delay(400)
                _boardCards.value = _boardCards.value.map { it.copy(isHighlighted = false, isFlipped = false) }
            }
        }
    }

    // ----------------------------------------------------------------------------
    // HINTS & UNDO & HELPERS
    // ----------------------------------------------------------------------------

    fun useHint() {
        val level = _gameLevel.value ?: return
        if (_gameStatus.value != GameStatus.PLAYING || _hintsCount.value <= 0 || isProcessingFlip) return

        if (level.mode == GameMode.SEQUENCE_MEMORY) {
            // In sequence, a hint briefly flashes the next card
            val sequence = _currentSequence.value
            val progress = _playerSequenceProgress.value
            val nextCardIndex = sequence.getOrNull(progress) ?: return

            _hintsCount.value -= 1
            viewModelScope.launch {
                _boardCards.value = _boardCards.value.mapIndexed { i, card ->
                    if (i == nextCardIndex) card.copy(isHighlighted = true, isFlipped = true) else card
                }
                AudioSynthesizer.playTone(350f, 300)
                delay(500)
                _boardCards.value = _boardCards.value.map { card ->
                    card.copy(isHighlighted = false, isFlipped = false)
                }
            }
        } else {
            // Match modes: highlight any matching pair
            // If first selected card exists, show its match
            val cards = _boardCards.value
            val targetValueId: Int
            val targetIdx1: Int
            val targetIdx2: Int

            val firstIdx = firstSelectedCardIndex
            if (firstIdx != null) {
                val card1 = cards[firstIdx]
                targetValueId = card1.valueId
                targetIdx1 = firstIdx
                targetIdx2 = cards.indexOfFirst { it.id != card1.id && it.valueId == targetValueId && !it.isMatched }
            } else {
                // Find first unmatched pair
                val unmatchedCard = cards.firstOrNull { !it.isMatched } ?: return
                targetValueId = unmatchedCard.valueId
                targetIdx1 = cards.indexOf(unmatchedCard)
                targetIdx2 = cards.indexOfLast { it.valueId == targetValueId && it.id != unmatchedCard.id && !it.isMatched }
            }

            if (targetIdx1 == -1 || targetIdx2 == -1) return

            _hintsCount.value -= 1

            // Flash both cards
            viewModelScope.launch {
                _boardCards.value = _boardCards.value.mapIndexed { i, card ->
                    if (i == targetIdx1 || i == targetIdx2) card.copy(isHighlighted = true, isFlipped = true) else card
                }
                delay(1200)
                _boardCards.value = _boardCards.value.mapIndexed { i, card ->
                    if (i == targetIdx1 || i == targetIdx2) {
                        // Keep flipped if it was the first selected, else hide
                        val shouldKeepFlipped = (i == firstIdx)
                        card.copy(isHighlighted = false, isFlipped = shouldKeepFlipped)
                    } else card
                }
            }
        }
    }

    fun useUndo() {
        if (_gameStatus.value != GameStatus.PLAYING || _undoCount.value <= 0 || cardSelectionHistory.isEmpty() || isProcessingFlip) return

        // Pop last state
        val prevState = cardSelectionHistory.removeAt(cardSelectionHistory.lastIndex)
        _boardCards.value = prevState
        _undoCount.value -= 1

        // Reset click cache
        firstSelectedCardIndex = prevState.indexOfFirst { it.isFlipped && !it.isMatched }.takeIf { it != -1 }
        secondSelectedCardIndex = null

        AudioSynthesizer.playTone(300f, 150)
    }

    fun buyUndoWithCoins(cost: Int = 15) {
        viewModelScope.launch {
            if (repository.deductCoins(cost)) {
                _undoCount.value += 1
                AudioSynthesizer.playTone(450f, 150)
            }
        }
    }

    fun buyHintWithCoins(cost: Int = 15) {
        viewModelScope.launch {
            if (repository.deductCoins(cost)) {
                _hintsCount.value += 1
                AudioSynthesizer.playTone(450f, 150)
            }
        }
    }

    // ----------------------------------------------------------------------------
    // WIN & LOSS HANDLERS
    // ----------------------------------------------------------------------------

    private fun handleLevelWin() {
        timerJob?.cancel()
        _gameStatus.value = GameStatus.LEVEL_COMPLETE
        AudioSynthesizer.playLevelComplete()

        val level = _gameLevel.value ?: return
        val coinsToEarn = level.coinsReward + if (_isDailyChallenge.value) 40 else 0

        // Calculate stars: 3 stars if errors <= 2, 2 stars if errors <= 5, 1 star otherwise
        val stars = when {
            _errorsCount.value <= 1 -> 3
            _errorsCount.value <= 3 -> 2
            else -> 1
        }

        viewModelScope.launch {
            if (_isDailyChallenge.value) {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                repository.completeDailyChallenge(todayStr, coinsToEarn)
                repository.unlockTitle("Daily Champion")
            } else {
                repository.saveLevelResult(level.id, stars, _score.value)
                repository.addCoins(coinsToEarn)

                // Achievement progress and titles
                checkCustomTitlesAndAchievements()
            }
        }
    }

    private fun handleGameOver() {
        timerJob?.cancel()
        _gameStatus.value = GameStatus.GAME_OVER
        AudioSynthesizer.playMatchFailure()
    }

    private suspend fun checkCustomTitlesAndAchievements() {
        val progresses = levelProgress.value
        val completedCount = progresses.count { it.stars > 0 }

        // Mode specific counts
        val shadowCompleted = progresses.count { it.stars > 0 && LevelManager.getLevel(it.levelId).mode == GameMode.SHADOW_MATCH }
        val soundCompleted = progresses.count { it.stars > 0 && LevelManager.getLevel(it.levelId).mode == GameMode.SOUND_MEMORY }
        val sequenceCompleted = progresses.count { it.stars > 0 && LevelManager.getLevel(it.levelId).mode == GameMode.SEQUENCE_MEMORY }

        if (shadowCompleted >= 10) {
            repository.unlockTitle("Shadow Detective")
        }
        if (soundCompleted >= 10) {
            repository.unlockTitle("Sonic Scholar")
        }
        if (sequenceCompleted >= 10) {
            repository.unlockTitle("Sequence Master")
        }
        if (completedCount >= 50) {
            repository.unlockTitle("Elite Matcher")
        }
        if (progresses.count { it.stars == 3 } >= 50) {
            repository.unlockTitle("Grandmaster")
        }
    }

    // ----------------------------------------------------------------------------
    // STORE & STORE ACTIONS
    // ----------------------------------------------------------------------------

    fun equipTheme(themeId: String) {
        viewModelScope.launch {
            repository.selectTheme(themeId)
        }
    }

    fun purchaseTheme(themeId: String, cost: Int) {
        viewModelScope.launch {
            repository.buyTheme(themeId, cost)
        }
    }

    fun equipTitle(title: String) {
        viewModelScope.launch {
            repository.selectTitle(title)
        }
    }

    // ----------------------------------------------------------------------------
    // DAILY CHALLENGE SYSTEM
    // ----------------------------------------------------------------------------

    fun startDailyChallenge() {
        // Daily challenge generates a high-difficulty level based on date seed
        val calendar = java.util.Calendar.getInstance()
        val dayOfYear = calendar.get(java.util.Calendar.DAY_OF_YEAR)

        // Select game mode based on day of year
        val mode = when (dayOfYear % 3) {
            0 -> GameMode.SHADOW_MATCH
            1 -> GameMode.SOUND_MEMORY
            else -> GameMode.SEQUENCE_MEMORY
        }

        val dailyLevel = GameLevel(
            id = 999, // special code
            mode = mode,
            title = "Daily Challenge",
            cardCount = 12, // High difficulty but solvable
            timeLimitSeconds = if (mode == GameMode.SEQUENCE_MEMORY) 0 else 45,
            sequenceLength = 5,
            revealSpeedMs = 700L,
            coinsReward = 50
        )

        startLevel(dailyLevel, isDaily = true)
    }

    fun startNextUnlockedLevelOfMode(mode: GameMode) {
        val progress = levelProgress.value
        val range = when (mode) {
            GameMode.SHADOW_MATCH -> 1..33
            GameMode.SOUND_MEMORY -> 34..66
            GameMode.SEQUENCE_MEMORY -> 67..100
        }
        val highestUnlocked = progress
            .filter { it.levelId in range && it.isUnlocked }
            .maxByOrNull { it.levelId }
            ?.levelId ?: range.first

        val level = LevelManager.getLevel(highestUnlocked)
        startLevel(level)
    }

    fun startHighestUnlockedLevel() {
        val progress = levelProgress.value
        val highestUnlocked = progress
            .filter { it.isUnlocked }
            .maxByOrNull { it.levelId }
            ?.levelId ?: 1

        val level = LevelManager.getLevel(highestUnlocked)
        startLevel(level)
    }
}
