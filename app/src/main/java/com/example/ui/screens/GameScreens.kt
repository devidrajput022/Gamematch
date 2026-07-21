package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.AudioSynthesizer
import com.example.data.*
import com.example.ui.*
import com.example.ui.theme.GameTheme
import com.example.ui.theme.GameThemes
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ----------------------------------------------------------------------------
// CANVAS PARTICLES & CONFETTI
// ----------------------------------------------------------------------------

data class CanvasParticle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val size: Float,
    val color: Color,
    var alpha: Float = 1.0f,
    var rotation: Float = 0f,
    val rotationSpeed: Float = Random.nextFloat() * 10f - 5f
)

@Composable
fun ParticleExplosion(
    isActive: Boolean,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {}
) {
    if (!isActive) return

    var particles by remember { mutableStateOf(emptyList<CanvasParticle>()) }

    LaunchedEffect(isActive) {
        if (isActive) {
            particles = List(80) {
                val angle = Random.nextFloat() * 2.0 * Math.PI
                val speed = Random.nextFloat() * 20f + 6f
                CanvasParticle(
                    x = 0.5f,
                    y = 0.35f,
                    vx = (cos(angle) * speed).toFloat(),
                    vy = (sin(angle) * speed).toFloat() - Random.nextFloat() * 8f, // float upwards
                    size = Random.nextFloat() * 16f + 8f,
                    color = colors.random()
                )
            }
        }
    }

    if (particles.isNotEmpty()) {
        val transition = rememberInfiniteTransition(label = "explosion")
        val step by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(16, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "step"
        )

        Canvas(modifier = modifier.fillMaxSize().graphicsLayer { translationX = step * 0f }) {
            val width = size.width
            val height = size.height

            var allDead = true
            val updated = particles.map { p ->
                val nextX = p.x + p.vx / width
                val nextY = p.y + p.vy / height
                val nextVy = p.vy + 0.35f // Gravity
                val nextAlpha = (p.alpha - 0.012f).coerceAtLeast(0f)
                val nextRotation = (p.rotation + p.rotationSpeed) % 360f

                if (nextAlpha > 0) allDead = false

                p.copy(
                    x = nextX,
                    y = nextY,
                    vy = nextVy,
                    alpha = nextAlpha,
                    rotation = nextRotation
                )
            }

            particles = updated

            particles.forEach { p ->
                if (p.alpha > 0) {
                    val drawX = p.x * width
                    val drawY = p.y * height
                    rotate(degrees = p.rotation, pivot = Offset(drawX, drawY)) {
                        drawRect(
                            color = p.color.copy(alpha = p.alpha),
                            topLeft = Offset(drawX - p.size / 2, drawY - p.size / 2),
                            size = androidx.compose.ui.geometry.Size(p.size, p.size)
                        )
                    }
                }
            }

            if (allDead) {
                onFinished()
            }
        }
    }
}

// ----------------------------------------------------------------------------
// ICON MAPPING
// ----------------------------------------------------------------------------

fun getIconForValue(valueId: Int): ImageVector {
    return when (valueId) {
        0 -> Icons.Default.Favorite
        1 -> Icons.Default.Star
        2 -> Icons.Default.Lightbulb
        3 -> Icons.Default.Home
        4 -> Icons.Default.Pets
        5 -> Icons.Default.DirectionsCar
        6 -> Icons.Default.Flight
        7 -> Icons.Default.LocalFireDepartment
        8 -> Icons.Default.SportsEsports
        9 -> Icons.Default.Warning
        10 -> Icons.Default.Notifications
        else -> Icons.Default.Face
    }
}

fun getColorForValue(valueId: Int): Color {
    return when (valueId) {
        0 -> Color(0xFFFF2A6D) // Neon pink
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFF00F0FF) // Neon cyan
        3 -> Color(0xFF76FF03) // Neon green
        4 -> Color(0xFFFF5722) // Coral
        5 -> Color(0xFFE040FB) // Orchid purple
        6 -> Color(0xFF2979FF) // Electric blue
        7 -> Color(0xFFFF9100) // Bright orange
        8 -> Color(0xFFAEEA00) // Lime
        9 -> Color(0xFFFFEA00) // Neon yellow
        10 -> Color(0xFFD500F9) // Purple
        else -> Color(0xFF00E676) // Spring green
    }
}

// ----------------------------------------------------------------------------
// BASE MAIN CONTENT (SCREEN SELECTOR)
// ----------------------------------------------------------------------------

@Composable
fun MemoryMatchApp(viewModel: GameViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()

    val activeThemeId = profile?.activeThemeId ?: "synthwave"
    val theme = GameThemes[activeThemeId] ?: GameThemes.values.first()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF020617)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            // Background Atmosphere glows
            BackgroundAtmosphere(theme = theme)

            Column(modifier = Modifier.fillMaxSize()) {
                // Main screen content area (expand to fill remaining space if bottom nav is present)
                Box(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        },
                        label = "screen_navigation"
                    ) { screen ->
                        when (screen) {
                            Screen.Home -> HomeScreen(viewModel, theme)
                            Screen.LevelMap -> LevelMapScreen(viewModel, theme)
                            Screen.Gameplay -> GameplayScreen(viewModel, theme)
                            Screen.Themes -> ThemesScreen(viewModel, theme)
                            Screen.Achievements -> AchievementsScreen(viewModel, theme)
                        }
                    }
                }

                // Beautiful Bottom Nav bar (only if NOT in gameplay)
                if (currentScreen != Screen.Gameplay) {
                    ImmersiveBottomNavigation(
                        currentScreen = currentScreen,
                        onNavigate = { viewModel.navigateTo(it) },
                        onPlayClick = {
                            viewModel.startHighestUnlockedLevel()
                        },
                        theme = theme
                    )
                }
            }
        }
    }
}

@Composable
fun BackgroundAtmosphere(theme: GameTheme) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Top-left blur glow: Indigo/Violet
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF4F46E5).copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(-100f, -100f),
                        radius = size.minDimension * 0.8f
                    )
                )
                // Mid-right blur glow: Fuchsia
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFD946EF).copy(alpha = 0.15f), Color.Transparent),
                        center = Offset(size.width + 150f, size.height * 0.5f),
                        radius = size.minDimension * 0.9f
                    )
                )
                // Bottom glow: Cyan
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF06B6D4).copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height + 100f),
                        radius = size.minDimension * 0.8f
                    )
                )
            }
    )
}

@Composable
fun ImmersiveBottomNavigation(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onPlayClick: () -> Unit,
    theme: GameTheme
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Nav bar container
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.80f)),
            shape = RoundedCornerShape(50.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(50.dp), clip = false)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Home tab
                BottomNavItem(
                    label = "Home",
                    icon = Icons.Default.Home,
                    isActive = currentScreen == Screen.Home,
                    onClick = { onNavigate(Screen.Home) },
                    modifier = Modifier.weight(1f)
                )

                // Levels tab
                BottomNavItem(
                    label = "Levels",
                    icon = Icons.Default.Map,
                    isActive = currentScreen == Screen.LevelMap,
                    onClick = { onNavigate(Screen.LevelMap) },
                    modifier = Modifier.weight(1f)
                )

                // Spacer for the center play button
                Spacer(modifier = Modifier.weight(1.1f))

                // Store/Themes tab
                BottomNavItem(
                    label = "Store",
                    icon = Icons.Default.Palette,
                    isActive = currentScreen == Screen.Themes,
                    onClick = { onNavigate(Screen.Themes) },
                    modifier = Modifier.weight(1f)
                )

                // Trophy/Titles tab
                BottomNavItem(
                    label = "Trophy",
                    icon = Icons.Default.EmojiEvents,
                    isActive = currentScreen == Screen.Achievements,
                    onClick = { onNavigate(Screen.Achievements) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Floating Action Button in the center (elevated)
        Box(
            modifier = Modifier
                .offset(y = (-18).dp)
                .size(64.dp)
                .background(Color(0xFF020617), CircleShape)
                .padding(4.dp), // outer border
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF818CF8), Color(0xFF4F46E5))
                        )
                    )
                    .clickable(onClick = onPlayClick)
                    .shadow(elevation = 8.dp, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Quick Play",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun BottomNavItem(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) Color(0xFF818CF8) else Color(0xFF64748B),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) Color(0xFF818CF8) else Color(0xFF64748B),
            letterSpacing = 0.5.sp
        )
    }
}

// ----------------------------------------------------------------------------
// HOME SCREEN
// ----------------------------------------------------------------------------

@Composable
fun HomeScreen(viewModel: GameViewModel, theme: GameTheme) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val progressList by viewModel.levelProgress.collectAsStateWithLifecycle()

    // Shadow Match (Levels 1 to 33)
    val shadowUnlocked = progressList.filter { it.levelId in 1..33 && it.isUnlocked }.maxByOrNull { it.levelId }?.levelId ?: 1
    val shadowStars = progressList.find { it.levelId == shadowUnlocked }?.stars ?: 0

    // Sound Memory (Levels 34 to 66)
    val soundUnlocked = progressList.filter { it.levelId in 34..66 && it.isUnlocked }.maxByOrNull { it.levelId }?.levelId ?: 34
    val soundUnlockedDisplay = soundUnlocked - 33
    val soundStars = progressList.find { it.levelId == soundUnlocked }?.stars ?: 0

    // Sequence Memory (Levels 67 to 100)
    val sequenceUnlocked = progressList.filter { it.levelId in 67..100 && it.isUnlocked }.maxByOrNull { it.levelId }?.levelId ?: 67
    val sequenceUnlockedDisplay = sequenceUnlocked - 66
    val sequenceStars = progressList.find { it.levelId == sequenceUnlocked }?.stars ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // TOP HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "WELCOME BACK",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = Color(0xFF818CF8)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Memory Match",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Stars capsule
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1E293B).copy(alpha = 0.5f), RoundedCornerShape(50.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Coins",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${profile?.coins ?: 0}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                // Settings button
                IconButton(
                    onClick = { viewModel.navigateTo(Screen.Themes) },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF1E293B).copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // DAILY MISSION CARD
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable { viewModel.startDailyChallenge() }
        ) {
            // Gradient Backdrop
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF6366F1), Color(0xFF7C3AED))
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(50.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "DAILY MISSION",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ancient Echoes",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Complete today's challenge to earn 50 bonus coins!",
                        fontSize = 12.sp,
                        color = Color(0xFFE0E7FF)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // GAME MODES LABEL
        Text(
            text = "GAME MODES",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = Color(0xFF64748B),
            modifier = Modifier
                .align(Alignment.Start)
                .padding(top = 8.dp, bottom = 4.dp)
        )

        // GAME MODES GRID / COLUMN
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Mode 1: Shadow Match
            GameModeRow(
                title = "Shadow Match",
                description = "Sync forms with their silhouettes",
                levelText = "Lvl $shadowUnlocked",
                stars = shadowStars,
                icon = Icons.Default.Contrast,
                gradientColors = listOf(Color(0xFF06B6D4), Color(0xFF2563EB)),
                onClick = { viewModel.startNextUnlockedLevelOfMode(GameMode.SHADOW_MATCH) }
            )

            // Mode 2: Sound Memory
            GameModeRow(
                title = "Sound Memory",
                description = "Master the rhythm of hidden tones",
                levelText = "Lvl $soundUnlockedDisplay",
                stars = soundStars,
                icon = Icons.Default.GraphicEq,
                gradientColors = listOf(Color(0xFFF43F5E), Color(0xFFEA580C)),
                onClick = { viewModel.startNextUnlockedLevelOfMode(GameMode.SOUND_MEMORY) }
            )

            // Mode 3: Sequence Memory
            GameModeRow(
                title = "Sequence Memory",
                description = "Trace the path of blinking lights",
                levelText = "Lvl $sequenceUnlockedDisplay",
                stars = sequenceStars,
                icon = Icons.Default.Reorder,
                gradientColors = listOf(Color(0xFF10B981), Color(0xFF0D9488)),
                onClick = { viewModel.startNextUnlockedLevelOfMode(GameMode.SEQUENCE_MEMORY) }
            )
        }
    }
}

@Composable
fun GameModeRow(
    title: String,
    description: String,
    levelText: String,
    stars: Int,
    icon: ImageVector,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.40f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Gradient Icon container
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(
                            Brush.linearGradient(colors = gradientColors),
                            RoundedCornerShape(16.dp)
                        )
                        .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            // Level and stars info
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = levelText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = gradientColors.first()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (index < stars) gradientColors.first() else Color(0xFF334155),
                                    CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// LEVEL MAP SCREEN
// ----------------------------------------------------------------------------

@Composable
fun LevelMapScreen(viewModel: GameViewModel, theme: GameTheme) {
    val progressList by viewModel.levelProgress.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()

    val totalCompleted = progressList.count { it.stars > 0 }
    val progressPercentage = if (progressList.isNotEmpty()) totalCompleted.toFloat() / progressList.size else 0f

    Column(modifier = Modifier.fillMaxSize()) {
        // TOP HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateTo(Screen.Home) },
                modifier = Modifier
                    .background(theme.cardBackground.copy(alpha = 0.5f), CircleShape)
                    .border(1.dp, theme.accentColor.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, "Back", tint = theme.textColor)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ADVENTURE", color = theme.accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("100 Handcrafted Levels", color = theme.textColor, fontSize = 16.sp, fontWeight = FontWeight.Black)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(theme.cardBackground.copy(alpha = 0.5f), RoundedCornerShape(50.dp))
                    .border(1.dp, theme.accentColor.copy(alpha = 0.3f), RoundedCornerShape(50.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("${profile?.coins ?: 0}", color = theme.textColor, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.MonetizationOn, null, tint = theme.accentColor, modifier = Modifier.size(18.dp))
            }
        }

        // PROGRESS BAR CAPSULE
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${(progressPercentage * 100).toInt()}%", color = theme.accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(12.dp))
            LinearProgressIndicator(
                progress = { progressPercentage },
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = theme.accentColor,
                trackColor = theme.cardBackground.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text("$totalCompleted / 100", color = theme.textColor.copy(alpha = 0.6f), fontSize = 12.sp)
        }

        // MAP CONTAINER - alternating nodes winding down
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 12.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 60.dp)
            ) {
                items(100) { index ->
                    val levelId = index + 1
                    val levelData = LevelManager.getLevel(levelId)
                    val progress = progressList.find { it.levelId == levelId } ?: LevelProgress(levelId, 0, 0, levelId == 1)

                    // Alternate placement to create winding path
                    val bias = when (index % 4) {
                        0 -> 0.15f
                        1 -> 0.45f
                        2 -> 0.75f
                        else -> 0.45f
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Spacer width proportional to winding pathway
                            Spacer(modifier = Modifier.fillMaxWidth(bias * 0.9f))

                            // Actual node
                            LevelMapNode(
                                level = levelData,
                                progress = progress,
                                theme = theme,
                                onClick = {
                                    if (progress.isUnlocked) {
                                        viewModel.startLevel(levelData)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LevelMapNode(
    level: GameLevel,
    progress: LevelProgress,
    theme: GameTheme,
    onClick: () -> Unit
) {
    val isCurrent = progress.isUnlocked && progress.stars == 0
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(86.dp)
            .graphicsLayer {
                if (isCurrent) {
                    scaleX = scale
                    scaleY = scale
                }
            }
    ) {
        // Mode icon indicators
        val modeIcon = when (level.mode) {
            GameMode.SHADOW_MATCH -> Icons.Default.FilterFrames
            GameMode.SOUND_MEMORY -> Icons.Default.MusicNote
            GameMode.SEQUENCE_MEMORY -> Icons.Default.FlashOn
        }

        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(
                    elevation = if (progress.isUnlocked) 6.dp else 0.dp,
                    shape = CircleShape,
                    ambientColor = theme.accentColor
                )
                .background(
                    brush = if (progress.isUnlocked) {
                        if (progress.stars > 0) {
                            Brush.linearGradient(listOf(theme.accentColor, theme.accentColor.copy(alpha = 0.7f)))
                        } else {
                            Brush.linearGradient(listOf(theme.cardBackground, theme.cardBackground.copy(alpha = 0.9f)))
                        }
                    } else {
                        Brush.linearGradient(listOf(Color(0xFF333333), Color(0xFF222222)))
                    },
                    shape = CircleShape
                )
                .border(
                    width = if (isCurrent) 3.dp else 1.dp,
                    color = if (isCurrent) theme.accentColor else theme.accentColor.copy(alpha = 0.4f),
                    shape = CircleShape
                )
                .clickable(enabled = progress.isUnlocked, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (!progress.isUnlocked) {
                Icon(Icons.Default.Lock, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = modeIcon,
                        contentDescription = null,
                        tint = if (progress.stars > 0) Color.Black else theme.accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${level.id}",
                        color = if (progress.stars > 0) Color.Black else theme.textColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                }
            }
        }

        // Small stars rating below node
        if (progress.isUnlocked && progress.stars > 0) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                repeat(3) { starIdx ->
                    val isLit = starIdx < progress.stars
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (isLit) Color(0xFFFFD700) else Color.Gray.copy(alpha = 0.4f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        } else {
            // Label category for locked/ready
            Text(
                text = when (level.mode) {
                    GameMode.SHADOW_MATCH -> "SHADOW"
                    GameMode.SOUND_MEMORY -> "SOUND"
                    GameMode.SEQUENCE_MEMORY -> "SEQUENCE"
                },
                color = theme.textColor.copy(alpha = 0.4f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
                letterSpacing = 1.sp
            )
        }
    }
}

// ----------------------------------------------------------------------------
// GAMEPLAY PLAYSCREEN & COMPONENTS
// ----------------------------------------------------------------------------

@Composable
fun GameplayScreen(viewModel: GameViewModel, theme: GameTheme) {
    val level by viewModel.gameLevel.collectAsStateWithLifecycle()
    val status by viewModel.gameStatus.collectAsStateWithLifecycle()
    val cards by viewModel.boardCards.collectAsStateWithLifecycle()
    val remainingTime by viewModel.remainingTime.collectAsStateWithLifecycle()
    val moves by viewModel.movesCount.collectAsStateWithLifecycle()
    val errors by viewModel.errorsCount.collectAsStateWithLifecycle()
    val score by viewModel.score.collectAsStateWithLifecycle()
    val undos by viewModel.undoCount.collectAsStateWithLifecycle()
    val hints by viewModel.hintsCount.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()

    val currentLevel = level ?: return

    // Particle active controller
    var celebrateActive by remember { mutableStateOf(false) }
    LaunchedEffect(status) {
        if (status == GameStatus.LEVEL_COMPLETE) {
            celebrateActive = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // GAME HEADS-UP DISPLAY
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.navigateTo(Screen.LevelMap) },
                    modifier = Modifier.background(theme.cardBackground.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, "Exit", tint = theme.textColor)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = currentLevel.title.uppercase(),
                        color = theme.accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = when (currentLevel.mode) {
                            GameMode.SHADOW_MATCH -> "Shadow Match"
                            GameMode.SOUND_MEMORY -> "Sound Memory"
                            GameMode.SEQUENCE_MEMORY -> "Sequence Memory"
                        },
                        color = theme.textColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Score capsule
                Card(
                    colors = CardDefaults.cardColors(containerColor = theme.accentColor.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "SCORE: $score",
                        color = theme.accentColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // LEVEL STATE ROW (Timer, errors, sequences status)
            Card(
                colors = CardDefaults.cardColors(containerColor = theme.cardBackground.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, theme.accentColor.copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Time Left
                    if (currentLevel.timeLimitSeconds > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, null, tint = theme.accentColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Time: ${remainingTime}s",
                                color = if (remainingTime <= 10) Color.Red else theme.textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Casino, null, tint = theme.accentColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Casual Mode", color = theme.textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    // Divider
                    Box(modifier = Modifier.size(1.dp, 20.dp).background(theme.textColor.copy(alpha = 0.2f)))

                    // Errors / Limit
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, null, tint = theme.textColor.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (currentLevel.allowedErrors != -1) {
                                "Errors: $errors/${currentLevel.allowedErrors}"
                            } else {
                                "Errors: $errors"
                            },
                            color = theme.textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    if (currentLevel.mode == GameMode.SEQUENCE_MEMORY) {
                        Box(modifier = Modifier.size(1.dp, 20.dp).background(theme.textColor.copy(alpha = 0.2f)))
                        val seqProg by viewModel.playerSequenceProgress.collectAsStateWithLifecycle()
                        val seqMax = currentLevel.sequenceLength
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, null, tint = theme.accentColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Seq: $seqProg/$seqMax", color = theme.textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            // GRID BOARD CONTAINER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when (status) {
                    GameStatus.SEQUENCE_PREVIEW -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = theme.accentColor)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "MEMORIZE SEQUENCE!",
                                    color = theme.accentColor,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    letterSpacing = 2.sp
                                )
                                Text(
                                    "Watch the flashes closely...",
                                    color = theme.textColor.copy(alpha = 0.6f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    else -> {}
                }

                // Grid size based on cards
                val columns = when (currentLevel.cardCount) {
                    4 -> 2
                    6 -> 2
                    8, 12 -> 3
                    else -> 4
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxWidth().testTag("memory_grid"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    userScrollEnabled = false
                ) {
                    items(cards.size) { cardIdx ->
                        val card = cards[cardIdx]
                        GameCard(
                            card = card,
                            mode = currentLevel.mode,
                            theme = theme,
                            onClick = { viewModel.handleCardTap(cardIdx) }
                        )
                    }
                }
            }

            // GAMEPLAY CONTROLS (HINT, UNDO)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // UNDO BUTTON
                Button(
                    onClick = {
                        if (undos > 0) {
                            viewModel.useUndo()
                        } else {
                            // If out of undos, buy one for 15 coins
                            if ((profile?.coins ?: 0) >= 15) {
                                viewModel.buyUndoWithCoins(15)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.cardBackground.copy(alpha = 0.7f)),
                    border = BorderStroke(1.dp, theme.accentColor.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("undo_button")
                ) {
                    Icon(Icons.Default.Undo, null, tint = theme.textColor)
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("UNDO", color = theme.textColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            text = if (undos > 0) "Free: $undos" else "Buy 🪙15",
                            color = theme.textColor.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                    }
                }

                // HINT BUTTON
                Button(
                    onClick = {
                        if (hints > 0) {
                            viewModel.useHint()
                        } else {
                            // Buy a hint
                            if ((profile?.coins ?: 0) >= 15) {
                                viewModel.buyHintWithCoins(15)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.cardBackground.copy(alpha = 0.7f)),
                    border = BorderStroke(1.dp, theme.accentColor.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("hint_button")
                ) {
                    Icon(Icons.Default.Lightbulb, null, tint = theme.accentColor)
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("HINT", color = theme.textColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            text = if (hints > 0) "Free: $hints" else "Buy 🪙15",
                            color = theme.textColor.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // CONFETTI OVERLAY
        ParticleExplosion(
            isActive = celebrateActive,
            colors = theme.particleColors,
            modifier = Modifier.fillMaxSize(),
            onFinished = { celebrateActive = false }
        )

        // STATUS FULL OVERLAYS (Win / Lose Screens)
        when (status) {
            GameStatus.LEVEL_COMPLETE -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = theme.cardBackground),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(2.dp, theme.accentColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .testTag("win_overlay")
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "VICTORY!",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = theme.accentColor,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Level Completed Successfully",
                                color = theme.textColor.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )

                            // Stars result display
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                repeat(3) { index ->
                                    val earnStar = when {
                                        errors <= 1 -> 3
                                        errors <= 3 -> 2
                                        else -> 1
                                    }
                                    val isLit = index < earnStar
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = if (isLit) Color(0xFFFFD700) else Color.DarkGray,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }

                            // Coins and Points Info
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.MonetizationOn, null, tint = theme.accentColor)
                                Spacer(modifier = Modifier.width(6.dp))
                                val coinsReward = currentLevel.coinsReward + if (viewModel.isDailyChallenge.value) 40 else 0
                                Text("+$coinsReward Coins Earned!", color = theme.textColor, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    viewModel.navigateTo(Screen.LevelMap)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("CONTINUE MAP", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            GameStatus.GAME_OVER -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = theme.cardBackground),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(2.dp, Color.Red),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .testTag("game_over_overlay")
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.SentimentVeryDissatisfied, null, tint = Color.Red, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "LIMIT REACHED",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Red,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Too many errors or out of time!",
                                color = theme.textColor.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.navigateTo(Screen.LevelMap) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("MAP", color = Color.White, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { viewModel.startLevel(currentLevel, viewModel.isDailyChallenge.value) },
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(48.dp)
                                ) {
                                    Text("TRY AGAIN", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
fun GameCard(
    card: MemoryCard,
    mode: GameMode,
    theme: GameTheme,
    onClick: () -> Unit
) {
    // Elegant flip rotation animation
    val flipRotation by animateFloatAsState(
        targetValue = if (card.isFlipped || card.isMatched) 180f else 0f,
        animationSpec = tween(400, easing = EaseInOutSine),
        label = "card_flip"
    )

    val scale by animateFloatAsState(
        targetValue = if (card.isHighlighted) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "card_scale"
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .graphicsLayer {
                rotationY = flipRotation
                scaleX = scale
                scaleY = scale
                cameraDistance = 12f * density
            }
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (card.isHighlighted) 3.dp else 1.dp,
                color = if (card.isHighlighted) theme.accentColor else theme.accentColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (flipRotation <= 90f) {
            // CARD BACK - displays the customized theme texture
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.cardBackGradient)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (mode) {
                        GameMode.SHADOW_MATCH -> Icons.Default.FilterFrames
                        GameMode.SOUND_MEMORY -> Icons.Default.Hearing
                        GameMode.SEQUENCE_MEMORY -> Icons.Default.Casino
                    },
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.size(28.dp)
                )
            }
        } else {
            // CARD FRONT - Displays graphics rotated correctly
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.cardBackground)
                    .graphicsLayer { rotationY = 180f }, // Correct flip projection mirror
                contentAlignment = Alignment.Center
            ) {
                when (mode) {
                    GameMode.SHADOW_MATCH -> {
                        val cardIcon = getIconForValue(card.valueId)
                        if (card.isShadow) {
                            // Silhouette tint
                            Icon(
                                imageVector = cardIcon,
                                contentDescription = "Shadow Object",
                                tint = Color.Black.copy(alpha = 0.85f),
                                modifier = Modifier.size(34.dp)
                            )
                        } else {
                            // Full-color design
                            Icon(
                                imageVector = cardIcon,
                                contentDescription = "Object",
                                tint = getColorForValue(card.valueId),
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    GameMode.SOUND_MEMORY -> {
                        // Display clean waveform animation or static speaker
                        if (card.isFlipped) {
                            val pulseTransition = rememberInfiniteTransition(label = "pulse_audio")
                            val rPulse by pulseTransition.animateFloat(
                                initialValue = 18.dp.value,
                                targetValue = 44.dp.value,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "pulse"
                            )
                            // Outer sonar ring
                            Box(
                                modifier = Modifier
                                    .size(rPulse.dp)
                                    .background(theme.accentColor.copy(alpha = (1f - (rPulse / 44f)).coerceIn(0f, 1f)), CircleShape)
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Sound Wave",
                            tint = theme.accentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    GameMode.SEQUENCE_MEMORY -> {
                        // Displays flat Sci-fi retro numbers
                        Text(
                            text = "${card.id + 1}",
                            color = theme.accentColor,
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// CUSTOM THEMES STORE SCREEN
// ----------------------------------------------------------------------------

@Composable
fun ThemesScreen(viewModel: GameViewModel, theme: GameTheme) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val coins = profile?.coins ?: 0
    val unlockedThemesList = profile?.unlockedThemes?.split(",") ?: listOf("synthwave")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateTo(Screen.Home) },
                modifier = Modifier.background(theme.cardBackground.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, "Back", tint = theme.textColor)
            }

            Text("VISUAL THEMES", color = theme.textColor, fontWeight = FontWeight.Black, fontSize = 18.sp)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$coins", color = theme.textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.MonetizationOn, null, tint = theme.accentColor, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(GameThemes.values.toList()) { targetTheme ->
                val isUnlocked = unlockedThemesList.contains(targetTheme.id)
                val isEquipped = profile?.activeThemeId == targetTheme.id

                Card(
                    colors = CardDefaults.cardColors(containerColor = targetTheme.cardBackground.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        width = if (isEquipped) 3.dp else 1.dp,
                        color = if (isEquipped) targetTheme.accentColor else Color.Gray.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            if (isEquipped) {
                                shadowElevation = 12f
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = targetTheme.name,
                                color = targetTheme.textColor,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isUnlocked) "Theme Unlocked" else "Cost: 🪙${targetTheme.cost}",
                                color = targetTheme.textColor.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )

                            // Palette Previews
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(14.dp).background(targetTheme.accentColor, CircleShape))
                                Box(modifier = Modifier.size(14.dp).background(targetTheme.textColor, CircleShape))
                                targetTheme.particleColors.take(2).forEach { pCol ->
                                    Box(modifier = Modifier.size(14.dp).background(pCol, CircleShape))
                                }
                            }
                        }

                        // Action button
                        Button(
                            onClick = {
                                if (isUnlocked) {
                                    viewModel.equipTheme(targetTheme.id)
                                } else if (coins >= targetTheme.cost) {
                                    viewModel.purchaseTheme(targetTheme.id, targetTheme.cost)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isEquipped) Color.Gray else targetTheme.accentColor
                            ),
                            shape = RoundedCornerShape(8.dp),
                            enabled = isUnlocked || coins >= targetTheme.cost
                        ) {
                            Text(
                                text = when {
                                    isEquipped -> "EQUIPPED"
                                    isUnlocked -> "EQUIP"
                                    else -> "UNLOCK"
                                },
                                color = if (isEquipped) Color.White else Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// ACHIEVEMENTS & TITLES SCREEN
// ----------------------------------------------------------------------------

@Composable
fun AchievementsScreen(viewModel: GameViewModel, theme: GameTheme) {
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()

    val unlockedTitles = profile?.unlockedTitles?.split(",") ?: listOf("Novice Matcher")
    val activeTitle = profile?.activeTitle ?: "Novice Matcher"

    // Available titles mapping
    val availableTitles = listOf(
        "Novice Matcher" to "Complete the tutorial and open the game",
        "Shadow Detective" to "Complete 10 levels of Shadow Matching",
        "Sonic Scholar" to "Complete 10 levels of Sound Memory",
        "Sequence Master" to "Complete 10 levels of Sequence Memory",
        "Daily Champion" to "Complete a Daily Challenge",
        "Elite Matcher" to "Unlock 50 adventure map levels",
        "Grandmaster" to "Earn 3 stars on 50 map levels"
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateTo(Screen.Home) },
                modifier = Modifier.background(theme.cardBackground.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, "Back", tint = theme.textColor)
            }

            Text("TITLES & REWARDS", color = theme.textColor, fontWeight = FontWeight.Black, fontSize = 18.sp)

            Box(modifier = Modifier.size(40.dp)) // symmetry spacer
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tab-less single screen: divide into Achievements (Top) and Equipable Titles (Bottom)
        Text(
            "EQUIPABLE TITLES",
            color = theme.accentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().height(200.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(availableTitles.size) { index ->
                val (titleName, titleDesc) = availableTitles[index]
                val isUnlocked = unlockedTitles.contains(titleName)
                val isEquipped = activeTitle == titleName

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isEquipped) theme.accentColor.copy(alpha = 0.15f) else theme.cardBackground.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isEquipped) theme.accentColor else if (isUnlocked) theme.textColor.copy(alpha = 0.3f) else Color.Transparent
                    ),
                    modifier = Modifier
                        .clickable(enabled = isUnlocked) {
                            viewModel.equipTitle(titleName)
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isUnlocked) Icons.Default.VerifiedUser else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isEquipped) theme.accentColor else if (isUnlocked) theme.textColor else Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = titleName,
                                color = if (isUnlocked) theme.textColor else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = titleDesc,
                            color = theme.textColor.copy(alpha = 0.5f),
                            fontSize = 8.sp,
                            lineHeight = 10.sp,
                            maxLines = 2
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ACHIEVEMENTS SECTION
        Text(
            "CHALLENGE ACHIEVEMENTS",
            color = theme.accentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(achievements) { ach ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = theme.cardBackground.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, theme.textColor.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ach.title,
                                color = theme.textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = ach.description,
                                color = theme.textColor.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )

                            // Achievement progress bar
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LinearProgressIndicator(
                                    progress = { ach.progress.toFloat() / ach.maxProgress },
                                    color = theme.accentColor,
                                    trackColor = Color.DarkGray.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "${ach.progress}/${ach.maxProgress}",
                                    color = theme.textColor.copy(alpha = 0.5f),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Unlock stamp indicator
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (ach.isUnlocked) theme.accentColor.copy(alpha = 0.15f) else Color.DarkGray.copy(alpha = 0.2f),
                                    CircleShape
                                )
                                .border(
                                    1.dp,
                                    if (ach.isUnlocked) theme.accentColor else Color.Gray.copy(alpha = 0.3f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (ach.isUnlocked) Icons.Default.Check else Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = if (ach.isUnlocked) theme.accentColor else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
