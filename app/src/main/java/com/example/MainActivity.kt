package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.Card
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.DealerChatEntry
import com.example.viewmodel.GamePhase
import com.example.viewmodel.PlayerState
import com.example.viewmodel.TeenPattiViewModel
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    TeenPattiApp(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun TeenPattiApp(
    modifier: Modifier = Modifier,
    viewModel: TeenPattiViewModel = viewModel()
) {
    val wallet by viewModel.walletState.collectAsState()
    val phase by viewModel.phase.collectAsState()
    val players by viewModel.players.collectAsState()
    val potAmount by viewModel.potAmount.collectAsState()
    val currentChaal by viewModel.currentChaal.collectAsState()
    val activeTurnIndex by viewModel.activeTurnIndex.collectAsState()
    val eventLogs by viewModel.eventLogs.collectAsState()
    val winnerMessage by viewModel.winnerMessage.collectAsState()
    val showdownDetails by viewModel.showdownDetails.collectAsState()

    // Tab Selection State
    var selectedSideTab by remember { mutableStateOf("Game") } // "Game", "AI Advisor", "Vault Log"
    
    // Support responsive columns on widescreen devices
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1113)) // Sleek dark canvas background
    ) {
        if (isTablet) {
            // Adaptive horizontal split screen for large devices
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                ) {
                    TableFeltCanvasView(
                        phase = phase,
                        players = players,
                        potAmount = potAmount,
                        currentChaal = currentChaal,
                        activeTurnIndex = activeTurnIndex,
                        winnerMessage = winnerMessage,
                        onSeeCards = { viewModel.userSeeCards() },
                        onStartGame = { viewModel.startNewGame() },
                        viewModel = viewModel
                    )
                }
                
                // Sidebar panel (Tablet Detail support)
                Box(
                    modifier = Modifier
                        .weight(0.8f)
                        .fillMaxHeight()
                        .background(Color(0xFF1C1B1F)) // Sleek surface
                        .border(1.dp, Color(0xFF49454F), RoundedCornerShape(0.dp))
                ) {
                    SidebarCabinetContent(
                        selectedTab = selectedSideTab,
                        onTabSelected = { selectedSideTab = it },
                        viewModel = viewModel,
                        walletBalance = wallet?.balance ?: 25000L
                    )
                }
            }
        } else {
            // Vertical stacked layout for Compact devices
            Column(modifier = Modifier.fillMaxSize()) {
                // Royal header bar
                RoyalHeaderBar(
                    walletLevel = wallet?.level ?: 1,
                    walletXp = wallet?.xp ?: 0,
                    walletBalance = wallet?.balance ?: 25000L,
                    selectedTab = selectedSideTab,
                    onTabSelected = { selectedSideTab = it }
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (selectedSideTab) {
                        "Game" -> {
                            TableFeltCanvasView(
                                phase = phase,
                                players = players,
                                potAmount = potAmount,
                                currentChaal = currentChaal,
                                activeTurnIndex = activeTurnIndex,
                                winnerMessage = winnerMessage,
                                onSeeCards = { viewModel.userSeeCards() },
                                onStartGame = { viewModel.startNewGame() },
                                viewModel = viewModel
                            )
                        }
                        "AI Advisor" -> {
                            CabinetAiPanel(viewModel = viewModel)
                        }
                        "Vault Log" -> {
                            CabinetVaultPanel(
                                viewModel = viewModel,
                                walletBalance = wallet?.balance ?: 25000L
                            )
                        }
                    }
                }
                
                // Sticky Action Event Logs ticker at bottom
                TickerNotificationLogs(eventLogs = eventLogs)
            }
        }
    }
}

@Composable
fun RoyalHeaderBar(
    walletLevel: Int,
    walletXp: Int,
    walletBalance: Long,
    selectedTab: String,
    onTabSelected: (String) -> Unit
) {
    val xpNeeded = walletLevel * 500
    val xpProgress = (walletXp.toFloat() / xpNeeded).coerceIn(0f, 1f)
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("royal_header_bar"),
        color = Color(0xFF1C1B1F),
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Profile & Level Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                Brush.linearGradient(colors = listOf(Color(0xFFD0BCFF), Color(0xFF381E72))),
                                CircleShape
                            )
                            .shadow(2.dp, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("TP", fontSize = 12.sp, color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "TEEN PATTI PRO",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Lvl $walletLevel",
                                color = Color(0xFFD0BCFF),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Progress bar to next level
                            Box(
                                modifier = Modifier
                                    .width(50.dp)
                                    .height(4.dp)
                                    .background(Color(0xFF49454F), RoundedCornerShape(2.dp))
                              ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(xpProgress)
                                        .background(Color(0xFFD0BCFF), RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }
                }

                // Table Title Indicator
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "ROYAL TABLE",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "● Live Table #402",
                        color = Color(0xFFD0BCFF),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Chips Wallet Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0xFF2B2930), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF49454F), RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "$",
                        color = Color(0xFFD0BCFF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatChips(walletBalance),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(Color(0xFFD0BCFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", fontSize = 10.sp, color = Color(0xFF381E72), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tab Nav Bar - Standard Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TabItemButton(
                    title = "GAME FELT",
                    icon = Icons.Filled.Star, // Star represents game table
                    isSelected = selectedTab == "Game",
                    onClick = { onTabSelected("Game") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                TabItemButton(
                    title = "AI ADVISOR",
                    icon = Icons.Filled.Person, // Person represents live AI Dealer
                    isSelected = selectedTab == "AI Advisor",
                    onClick = { onTabSelected("AI Advisor") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                TabItemButton(
                    title = "ROBO VAULT",
                    icon = Icons.Filled.Lock, // Secure Balance Wallet
                    isSelected = selectedTab == "Vault Log",
                    onClick = { onTabSelected("Vault Log") }
                )
            }
        }
    }
}

@Composable
fun TabItemButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF381E72) else Color(0xFF2B2930))
            .border(
                1.dp,
                if (isSelected) Color(0xFFD0BCFF) else Color(0xFF49454F),
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color(0xFFD0BCFF) else Color(0xFF938F99),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                color = if (isSelected) Color(0xFFD0BCFF) else Color(0xFF938F99),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SidebarCabinetContent(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    viewModel: TeenPattiViewModel,
    walletBalance: Long
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = if (selectedTab == "AI Advisor") 0 else 1,
            containerColor = Color(0xFF0F1113),
            contentColor = Color(0xFFD0BCFF)
        ) {
            Tab(
                selected = selectedTab == "AI Advisor",
                onClick = { onTabSelected("AI Advisor") },
                text = { Text("AI Dealer Chat") }
            )
            Tab(
                selected = selectedTab == "Vault Log",
                onClick = { onTabSelected("Vault Log") },
                text = { Text("Robo Vault Ledger") }
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (selectedTab == "AI Advisor") {
                CabinetAiPanel(viewModel = viewModel)
            } else {
                CabinetVaultPanel(viewModel = viewModel, walletBalance = walletBalance)
            }
        }
    }
}

// ---------------- GAME FEEL / TABLE CANVAS VIEW ----------------

@Composable
fun TableFeltCanvasView(
    phase: GamePhase,
    players: List<PlayerState>,
    potAmount: Long,
    currentChaal: Long,
    activeTurnIndex: Int,
    winnerMessage: String?,
    onSeeCards: () -> Unit,
    onStartGame: () -> Unit,
    viewModel: TeenPattiViewModel
) {
    val showdownDetails by viewModel.showdownDetails.collectAsState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1A3A2A), Color(0xFF0F1113))
                )
            )
            .testTag("game_table_felt")
    ) {
        // Draw elegant table felt oval
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw luxury oval felt inner ring
            drawOval(
                color = Color(0xFF2D7344).copy(alpha = 0.3f),
                topLeft = Offset(size.width * 0.12f, size.height * 0.15f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.54f),
                style = Stroke(width = 1.dp.toPx())
            )
            
            // Outer sleek rail
            drawOval(
                color = Color(0xFFD0BCFF).copy(alpha = 0.4f),
                topLeft = Offset(size.width * 0.08f, size.height * 0.12f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.84f, size.height * 0.60f),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Draw Player seats (Multiplayer representation)
        if (players.size >= 4) {
            // Priya - TOP (Index 2)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp)
            ) {
                PlayerChairStation(
                    player = players[2],
                    isActiveTurn = activeTurnIndex == 2,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Rajesh - LEFT (Index 1)
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.35f)
                    .fillMaxWidth(0.35f)
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
            ) {
                PlayerChairStation(
                    player = players[1],
                    isActiveTurn = activeTurnIndex == 1,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Ananya - RIGHT (Index 3)
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.35f)
                    .fillMaxWidth(0.35f)
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
            ) {
                PlayerChairStation(
                    player = players[3],
                    isActiveTurn = activeTurnIndex == 3,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // You - BOTTOM (Index 0)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp) // Safe margin above controls
            ) {
                PlayerChairStation(
                    player = players[0],
                    isActiveTurn = activeTurnIndex == 0,
                    modifier = Modifier.align(Alignment.Center),
                    isHuman = true,
                    onSeeCards = onSeeCards
                )
            }
        }

        // Center Pot and Status Dashboard
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-40).dp) // Center nicely inside oval canvas
                .shadow(12.dp, RoundedCornerShape(24.dp))
                .background(Color(0xFF0F1113).copy(alpha = 0.75f), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "TOTAL POT",
                    color = Color(0xFFD0BCFF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "$",
                        color = Color(0xFFD0BCFF),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatChips(potAmount),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Chaal Goal: $currentChaal",
                    color = Color(0xFFD0BCFF).copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Dynamic Showdown revelations
        if (phase == GamePhase.SHOWDOWN && winnerMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { /* Block terms */ }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(Color(0xFF1C1B1F), RoundedCornerShape(24.dp))
                        .border(2.dp, Color(0xFFD0BCFF), RoundedCornerShape(24.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "✦ ROUND WINNER ✦",
                            color = Color(0xFFD0BCFF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = winnerMessage,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color(0xFF49454F))
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Detail listings
                        showdownDetails.forEach { detail ->
                            Text(
                                text = detail,
                                color = Color(0xFFE2E2E6),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { onStartGame() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD0BCFF),
                                contentColor = Color(0xFF381E72)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.testTag("start_round_button")
                        ) {
                            Text(
                                "NEXT HAND ROUND",
                                color = Color(0xFF381E72),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Bottom Controls interface overlay
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Color(0xFF1C1B1F), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .border(1.dp, Color(0xFF49454F), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .padding(vertical = 12.dp, horizontal = 16.dp)
        ) {
            InteractiveControlsPanel(
                phase = phase,
                players = players,
                activeTurnIndex = activeTurnIndex,
                onStartGame = onStartGame,
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun PlayerChairStation(
    player: PlayerState,
    isActiveTurn: Boolean,
    modifier: Modifier = Modifier,
    isHuman: Boolean = false,
    onSeeCards: (() -> Unit)? = null
) {
    // Pulse animation for active turn
    val infiniteTransition = rememberInfiniteTransition()
    val glowColor by infiniteTransition.animateColor(
        initialValue = Color(0xFFD0BCFF).copy(alpha = 0.2f),
        targetValue = Color(0xFFD0BCFF).copy(alpha = 0.9f),
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(4.dp)
            .width(135.dp)
    ) {
        // Last action status balloon
        if (player.lastAction.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .shadow(4.dp, RoundedCornerShape(8.dp))
                    .background(Color(0xFF381E72), RoundedCornerShape(8.dp))
                    .border(
                        1.dp,
                        if (isActiveTurn) Color(0xFFD0BCFF) else Color(0xFF49454F),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = player.lastAction,
                    color = if (isActiveTurn) Color.White else Color(0xFFD0BCFF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Player seat circle with Sleek details
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (player.isPacked) Color(0xFF25262B) else Color(0xFF1C1B1F))
                    .border(
                        if (isActiveTurn) 2.5.dp else 1.5.dp,
                        if (isActiveTurn) glowColor else Color(0xFF49454F),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (player.isThinking) {
                    CircularProgressIndicator(
                        color = Color(0xFFD0BCFF),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        text = if (player.isPacked) "💀" else player.avatarEmoji,
                        fontSize = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column {
                Text(
                    text = player.name,
                    color = if (isActiveTurn) Color(0xFFD0BCFF) else Color.White,
                    fontWeight = if (isActiveTurn) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatChips(player.balance)} pt",
                    color = Color(0xFFD0BCFF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Three Card Stack Graphics
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (player.cards.isEmpty()) {
                // Empty placeholders prior to starting
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .size(width = 24.dp, height = 34.dp)
                            .background(Color(0xFF2B2930), RoundedCornerShape(3.dp))
                            .border(0.5.dp, Color(0xFF49454F), RoundedCornerShape(3.dp))
                    )
                }
            } else {
                player.cards.take(3).forEachIndexed { index, card ->
                    Box(
                        modifier = Modifier.offset(x = (index * -4).dp)
                    ) {
                        CardGraphic(
                            card = card,
                            isSeen = player.isSeen || (isHuman && player.isSeen),
                            isPacked = player.isPacked,
                            onSeeCards = onSeeCards
                        )
                    }
                }
            }
        }
        
        // Show hands rank name overlay if cards are seen and not folded
        if (player.isSeen && !player.isPacked && player.handEvaluation != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = player.handEvaluation.handType.displayName,
                color = Color(0xFFFFC107),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun CardGraphic(
    card: Card,
    isSeen: Boolean,
    isPacked: Boolean,
    onSeeCards: (() -> Unit)? = null
) {
    if (isPacked) {
        // Red tinted packed transparency
        Box(
            modifier = Modifier
                .size(width = 30.dp, height = 44.dp)
                .background(Color(0x33440000), RoundedCornerShape(4.dp))
                .border(0.5.dp, Color(0x66FF3333), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("X", fontSize = 10.sp, color = Color(0x66FF3333), fontWeight = FontWeight.Bold)
        }
    } else if (!isSeen) {
        // Gilded Face down back card design
        val interactModifier = if (onSeeCards != null) {
            Modifier.clickable { onSeeCards() }
        } else {
            Modifier
        }
        
        Box(
            modifier = interactModifier
                .size(width = 34.dp, height = 50.dp)
                .shadow(2.dp, RoundedCornerShape(4.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF381E72), Color(0xFF150A30))
                    ),
                    RoundedCornerShape(4.dp)
                )
                .border(1.dp, Color(0xFFD0BCFF), RoundedCornerShape(4.dp))
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(0.5.dp, Color(0xFFD0BCFF).copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                    .background(Color.Black.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "?",
                    color = Color(0xFFD0BCFF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        // Face up reveal Card design
        Box(
            modifier = Modifier
                .size(width = 34.dp, height = 50.dp)
                .shadow(2.dp, RoundedCornerShape(4.dp))
                .background(Color.White, RoundedCornerShape(4.dp))
                .border(0.5.dp, Color(0xFFC0C0C0), RoundedCornerShape(4.dp))
                .padding(2.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = card.rank.symbol,
                    color = if (card.suit.colorRed) Color(0xFFC62828) else Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 1.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.End),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Text(
                        text = card.suit.symbol,
                        color = if (card.suit.colorRed) Color(0xFFC62828) else Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

// ---------------- INTERACTIVE PLAY CONTROLS PANEL ----------------

@Composable
fun InteractiveControlsPanel(
    phase: GamePhase,
    players: List<PlayerState>,
    activeTurnIndex: Int,
    onStartGame: () -> Unit,
    viewModel: TeenPattiViewModel
) {
    if (phase == GamePhase.IDLE || phase == GamePhase.SHOWDOWN) {
        // Round idle: start state
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome to Royal High Stakes. Tap deal to play standard Teen Patti rules.",
                color = Color(0xFF938F99),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
            Button(
                onClick = { onStartGame() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD0BCFF),
                    contentColor = Color(0xFF381E72)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(44.dp)
                    .testTag("deal_starting_button")
            ) {
                Text(
                    text = "DEAL HANDS (Boot 200)",
                    color = Color(0xFF381E72),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    } else {
        // Active round bidding state
        val isUserTurn = activeTurnIndex == 0
        val user = players.getOrNull(0)
        
        if (user == null || user.isPacked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "You packed. Watching simulated live players...",
                    color = Color.White.copy(alpha = 0.5f),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontSize = 12.sp
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Warning if turn is bot's
                if (!isUserTurn) {
                    val activePlayer = players.getOrNull(activeTurnIndex)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Please wait, ${activePlayer?.name ?: "Opponent"} is betting...",
                            color = Color(0xFFD0BCFF).copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Your Turn (${if (user.isSeen) "Seen 2x Bet" else "Blind 1x Bet"})",
                            color = Color(0xFFD0BCFF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Active card status: option to look at cards!
                        if (!user.isSeen) {
                            Button(
                                onClick = { viewModel.userSeeCards() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF381E72),
                                    contentColor = Color(0xFFD0BCFF)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .testTag("see_cards_button")
                            ) {
                                Text("SEE MY CARDS", color = Color(0xFFD0BCFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Action buttons grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Pack / Fold Button
                        Button(
                            onClick = { viewModel.userPack() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2B2930),
                                contentColor = Color(0xFFE2E2E6)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF49454F)),
                            modifier = Modifier
                                .weight(1f)
                                .height(45.dp)
                                .testTag("pack_action_button")
                        ) {
                            Text("FOLD", color = Color(0xFFE2E2E6), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Chaal/Call
                        val betNeeded = if (user.isSeen) viewModel.currentChaal.value * 2 else viewModel.currentChaal.value
                        Button(
                            onClick = { viewModel.userPlayChaal() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD0BCFF),
                                contentColor = Color(0xFF381E72)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            elevation = ButtonDefaults.buttonElevation(4.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(45.dp)
                                .testTag("chaal_action_button")
                        ) {
                            Text(
                                "CHAAL $betNeeded",
                                color = Color(0xFF381E72),
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Double Raise
                        val raiseChaal = if (viewModel.currentChaal.value < 1600L) viewModel.currentChaal.value * 2 else viewModel.currentChaal.value
                        val raiseBet = if (user.isSeen) raiseChaal * 2 else raiseChaal
                        val isMaxRaised = raiseChaal == viewModel.currentChaal.value
                        
                        Button(
                            onClick = { viewModel.userRaise() },
                            enabled = !isMaxRaised,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF381E72),
                                contentColor = Color(0xFFD0BCFF),
                                disabledContainerColor = Color(0xFF1C1B1F)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF49454F)),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(45.dp)
                                .testTag("raise_action_button")
                        ) {
                            Text(
                                if (isMaxRaised) "MAX LIMIT" else "RAISE $raiseBet",
                                color = if (isMaxRaised) Color.Gray else Color(0xFFD0BCFF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Show (permitted only if remaining non-packed players is 2!)
                        val remainingActive = players.count { !it.isPacked }
                        val canShow = remainingActive == 2 && isUserTurn
                        
                        Button(
                            onClick = { viewModel.userRequestShow() },
                            enabled = canShow,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2B2930),
                                contentColor = Color(0xFFD0BCFF),
                                disabledContainerColor = Color(0xFF1C1B1F)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF49454F)),
                            modifier = Modifier
                                .weight(1.1f)
                                .height(45.dp)
                                .testTag("show_action_button")
                        ) {
                            Text(
                                "SHOW",
                                color = if (canShow) Color(0xFFD0BCFF) else Color.Gray,
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

// ---------------- SIDEBAR CABINET BOXES ----------------

@Composable
fun CabinetAiPanel(viewModel: TeenPattiViewModel) {
    val logs by viewModel.dealerChatLogs.collectAsState()
    val advisorMsg by viewModel.aiAdvisorMessage.collectAsState()
    val isAdvisorLoading by viewModel.isAiAdvisorLoading.collectAsState()
    var userChatText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .testTag("ai_advisor_panel")
    ) {
        // AI Probability / Advisor Block
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F261B)),
            elevation = CardDefaults.cardElevation(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI HAND CALCULATOR & STRAT", color = Color(0xFFFFC107), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.askGeminiDealerForAdvice() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B4E33)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text("ANALYZE HAND", color = Color(0xFFFFD700), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                if (isAdvisorLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color(0xFFFFC107))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Calculating hand probabilities...", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                } else {
                    Text(
                        text = if (advisorMsg.isEmpty()) "Tap Analyze Hand to consult the dealer about your currently dealt cards and table risk coefficients." else advisorMsg,
                        color = Color(0xFFE0E0E0),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Live Dealer Chat Log
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0A140F), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF1E3326), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true
            ) {
                // Show latest messages first since list is reversed
                items(logs.reversed()) { chat ->
                    DealerChatMessageBubble(chat = chat)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Message input row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = userChatText,
                onValueChange = { userChatText = it },
                placeholder = { Text("Ask dealer anything...", fontSize = 12.sp, color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFFC107),
                    unfocusedBorderColor = Color(0xFF1E3E2A)
                ),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = {
                    viewModel.sendUserChatToDealer(userChatText)
                    userChatText = ""
                },
                modifier = Modifier
                    .shadow(2.dp, CircleShape)
                    .background(Color(0xFFFFC107), CircleShape)
                    .size(40.dp)
            ) {
                Icon(imageVector = Icons.Filled.Send, contentDescription = "Send", tint = Color.Black, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun DealerChatMessageBubble(chat: DealerChatEntry) {
    val isDealer = chat.sender == "Dealer"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isDealer) Alignment.Start else Alignment.End
    ) {
        Text(
            text = chat.sender,
            color = if (isDealer) Color(0xFFFFC107) else Color(0xFFFFEB3B),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Box(
            modifier = Modifier
                .shadow(2.dp, RoundedCornerShape(12.dp))
                .background(
                    if (isDealer) Color(0xFF122C1D) else Color(0xFF263E30),
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (isDealer) 0.dp else 12.dp,
                        bottomEnd = if (isDealer) 12.dp else 0.dp
                    )
                )
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .widthIn(max = 240.dp)
        ) {
            Text(
                text = chat.message,
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun CabinetVaultPanel(viewModel: TeenPattiViewModel, walletBalance: Long) {
    val transactions by viewModel.transactionsState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .testTag("vault_ledger_panel")
    ) {
        // Vault Secure Deposit Claim Unit
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF241C0A)),
            elevation = CardDefaults.cardElevation(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .border(1.dp, Color(0xFFFFC107).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Lock, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SECURE ROYAL VAULT", color = Color(0xFFFFC107), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Claim virtual free starter gold here. Cooldown interval ensures secure ledger transactions.",
                    color = Color(0xFFFFECB3),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.claimDailyFreeReward() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .testTag("claim_free_chips_button")
                ) {
                    Text("CLAIM 10,000 COINS", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Text(
            text = "TRANSACTION LEDGER HISTORY",
            color = Color(0xFFFFC107),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
        )

        // Transactions List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0C1410), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF1E3527), RoundedCornerShape(8.dp))
        ) {
            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No transactions logged yet.", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(transactions) { tx ->
                        TransactionRow(tx = tx)
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionRow(tx: com.example.data.TransactionHistory) {
    val formattedAmt = "${if (tx.amount >= 0) "+" else ""}${formatChips(tx.amount)}"
    val color = if (tx.amount > 0) Color(0xFF4CAF50) else if (tx.amount < 0) Color(0xFFF44336) else Color(0xFFFFC107)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, Color(0xFF152A1D))
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = tx.transType,
                        color = color,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = tx.description,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(140.dp)
                )
            }
            Text(
                text = formattedAmt,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ---------------- DYNAMIC EVENT TICKER BAR ----------------

@Composable
fun TickerNotificationLogs(eventLogs: List<String>) {
    val latest = eventLogs.lastOrNull() ?: "Sit tight, game will update..."
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF040A06),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xFFFFC107).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .border(0.5.dp, Color(0xFFFFC107), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "FEED",
                    color = Color(0xFFFFC107),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            AnimatedContent(
                targetState = latest,
                label = "eventTickerAnimation",
                transitionSpec = {
                    slideInVertically { height -> height } + fadeIn() togetherWith
                            slideOutVertically { height -> -height } + fadeOut()
                }
            ) { text ->
                Text(
                    text = text,
                    color = Color(0xFF90C29E),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------------- UTILS ----------------

private fun formatChips(amount: Long): String {
    return try {
        NumberFormat.getNumberInstance(Locale.US).format(amount)
    } catch (e: Exception) {
        amount.toString()
    }
}
