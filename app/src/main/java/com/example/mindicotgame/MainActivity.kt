package com.example.mindicotgame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindicotgame.logic.AiPlayerLogic
import com.example.mindicotgame.model.Card
import com.example.mindicotgame.model.Player
import com.example.mindicotgame.model.Suit
import com.example.mindicotgame.model.Team
import com.example.mindicotgame.model.symbol
import com.example.mindicotgame.model.BandHukumVariant
import com.example.mindicotgame.ui.theme.MindiCotGameTheme

class MainActivity : ComponentActivity() {
    private val gameViewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MindiCotGameTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MindiCotGameScreen(viewModel = gameViewModel)
                }
            }
        }
    }
}

@Composable
fun MindiCotGameScreen(viewModel: GameViewModel) {
    val gamePhase by viewModel.gamePhase

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (gamePhase == GamePhase.SETUP_GAME ||
            (gamePhase == GamePhase.NOT_STARTED && viewModel.tricksPlayedThisDeal.value == 0) ||
            gamePhase == GamePhase.DEAL_COMPLETED ||
            gamePhase == GamePhase.GAME_OVER
        ) {
            GameSetupScreen(viewModel)
        } else {
            MindiCotGameTable(viewModel = viewModel)
        }
    }
}

@Composable
fun GameSetupScreen(viewModel: GameViewModel) {
    val gameMessage by viewModel.gameMessage
    val selectedVariant by viewModel.bandHukumVariant
    val selectedAiDifficulty by viewModel.aiDifficulty

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = gameMessage,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text(
            "Choose Band Hukum (Closed Trump) Rules:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(Modifier.selectableGroup()) {
            BandHukumVariant.values().forEach { variant ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .selectable(
                            selected = (variant == selectedVariant),
                            onClick = { viewModel.setBandHukumVariant(variant) },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (variant == selectedVariant),
                        onClick = null
                    )
                    Text(
                        text = when (variant) {
                            BandHukumVariant.VERSION_A -> "Version A: Auto-reveal on break suit"
                            BandHukumVariant.VERSION_B -> "Version B: Optional reveal on break suit"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Choose AI Difficulty:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(Modifier.selectableGroup()) {
            AiPlayerLogic.Difficulty.values().forEach { difficulty ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .selectable(
                            selected = (difficulty == selectedAiDifficulty),
                            onClick = { viewModel.setAiDifficulty(difficulty) },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (difficulty == selectedAiDifficulty),
                        onClick = null
                    )
                    Text(
                        text = difficulty.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                viewModel.startNewDeal()
            },
        ) {
            Text("Start New Deal")
        }
    }
}


@Composable
fun MindiCotGameTable(viewModel: GameViewModel) {
    val gamePhase by viewModel.gamePhase
    val gameMessage by viewModel.gameMessage
    val players = viewModel.players
    val currentPlayerTurn by viewModel.currentPlayerTurn
    val playerToSelectTrump by viewModel.playerToSelectTrump
    val currentDealer by viewModel.currentDealer
    val currentTrickCards = viewModel.currentTrickCards
    val revealedTrumpSuit by viewModel.revealedTrumpSuit
    val hiddenTrumpCard by viewModel.hiddenTrumpCardState
    val playerCanRequestTrumpReveal by viewModel.playerCanRequestTrumpReveal
    val leadSuitForCurrentTrick by viewModel.leadSuitForCurrentTrick

    val humanPlayer = players.find { it.id == "P1" }
    val leftPlayer = players.getOrNull(1)
    val topPlayer = players.getOrNull(2)
    val rightPlayer = players.getOrNull(3)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GameInfoBar(
                message = gameMessage,
                dealerName = currentDealer?.name,
                trumpSuit = revealedTrumpSuit,
                hiddenTrumpSet = hiddenTrumpCard != null && revealedTrumpSuit == null,
                playerToSelectTrumpName = if (gamePhase == GamePhase.AWAITING_TRUMP_SELECTION) playerToSelectTrump?.name else null,
                currentTrickCount = viewModel.tricksPlayedThisDeal.value,
                totalTricks = 13,
                leadSuit = leadSuitForCurrentTrick
            )

            Spacer(modifier = Modifier.height(8.dp))

            topPlayer?.let { player ->
                val isPlayerCurrentlySelectingTrump = player == playerToSelectTrump && gamePhase == GamePhase.AWAITING_TRUMP_SELECTION
                PlayerArea(
                    player = player,
                    viewModel = viewModel,
                    alignment = Alignment.CenterHorizontally,
                    isCurrentTurn = player == currentPlayerTurn && (gamePhase == GamePhase.PLAYER_TURN || (gamePhase == GamePhase.TRUMP_SELECTION_DONE && currentTrickCards.isEmpty()) || gamePhase == GamePhase.TRUMP_REVEALED || gamePhase == GamePhase.AWAITING_TRUMP_REVEAL_CHOICE),
                    isSelectingTrump = isPlayerCurrentlySelectingTrump,
                    showHandFaceUp = false,
                    leadSuit = null
                )
            }

            Spacer(modifier = Modifier.weight(0.5f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                leftPlayer?.let { player ->
                    val isPlayerCurrentlySelectingTrump = player == playerToSelectTrump && gamePhase == GamePhase.AWAITING_TRUMP_SELECTION
                    PlayerArea(
                        player = player,
                        viewModel = viewModel,
                        alignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .rotate(90f)
                            .weight(1f),
                        isCurrentTurn = player == currentPlayerTurn && (gamePhase == GamePhase.PLAYER_TURN || (gamePhase == GamePhase.TRUMP_SELECTION_DONE && currentTrickCards.isEmpty()) || gamePhase == GamePhase.TRUMP_REVEALED || gamePhase == GamePhase.AWAITING_TRUMP_REVEAL_CHOICE),
                        isSelectingTrump = isPlayerCurrentlySelectingTrump,
                        showHandFaceUp = false,
                        leadSuit = null
                    )
                }

                CurrentTrickView(
                    trickCards = currentTrickCards.toList(),
                    players = players.toList(),
                    modifier = Modifier.weight(1.5f)
                )

                rightPlayer?.let { player ->
                    val isPlayerCurrentlySelectingTrump = player == playerToSelectTrump && gamePhase == GamePhase.AWAITING_TRUMP_SELECTION
                    PlayerArea(
                        player = player,
                        viewModel = viewModel,
                        alignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .rotate(-90f)
                            .weight(1f),
                        isCurrentTurn = player == currentPlayerTurn && (gamePhase == GamePhase.PLAYER_TURN || (gamePhase == GamePhase.TRUMP_SELECTION_DONE && currentTrickCards.isEmpty()) || gamePhase == GamePhase.TRUMP_REVEALED || gamePhase == GamePhase.AWAITING_TRUMP_REVEAL_CHOICE),
                        isSelectingTrump = isPlayerCurrentlySelectingTrump,
                        showHandFaceUp = false,
                        leadSuit = null
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))

            humanPlayer?.let { player ->
                val isPlayerCurrentlySelectingTrump = player == playerToSelectTrump && gamePhase == GamePhase.AWAITING_TRUMP_SELECTION
                val isPlayerCurrentlyPlaying = player == currentPlayerTurn && (gamePhase == GamePhase.PLAYER_TURN || (gamePhase == GamePhase.TRUMP_SELECTION_DONE && currentTrickCards.isEmpty()) || gamePhase == GamePhase.TRUMP_REVEALED || gamePhase == GamePhase.AWAITING_TRUMP_REVEAL_CHOICE)
                PlayerArea(
                    player = player,
                    viewModel = viewModel,
                    alignment = Alignment.CenterHorizontally,
                    isCurrentTurn = isPlayerCurrentlyPlaying,
                    isSelectingTrump = isPlayerCurrentlySelectingTrump,
                    showHandFaceUp = true,
                    leadSuit = leadSuitForCurrentTrick
                )

                if (player == currentPlayerTurn && playerCanRequestTrumpReveal && gamePhase == GamePhase.AWAITING_TRUMP_REVEAL_CHOICE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.requestTrumpReveal(player) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Ask to Reveal Trump")
                    }
                    Text(
                        "Or play a card from your hand.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TeamScoresView(teams = viewModel.teams)

            if (gamePhase == GamePhase.DEAL_COMPLETED || gamePhase == GamePhase.GAME_OVER) {
                Button(
                    onClick = {
                        viewModel.gamePhase.value = GamePhase.SETUP_GAME
                        viewModel.gameMessage.value = "Setup game: Select variant and AI difficulty."
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(if (gamePhase == GamePhase.GAME_OVER) "Start New Game Setup" else "Next Deal Setup")
                }
            }
        }
    }
}

@Composable
fun GameInfoBar(
    message: String,
    dealerName: String?,
    trumpSuit: Suit?,
    hiddenTrumpSet: Boolean,
    playerToSelectTrumpName: String?,
    currentTrickCount: Int,
    totalTricks: Int,
    leadSuit: Suit?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        )
        Divider(modifier = Modifier.padding(bottom = 6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            dealerName?.let { Text("Dealer: $it", style = MaterialTheme.typography.labelSmall) }
            val trumpText = when {
                trumpSuit != null -> "Trump: ${trumpSuit.name.first()}${trumpSuit.symbol}"
                hiddenTrumpSet -> "Trump: Hidden"
                playerToSelectTrumpName != null -> "Trump: $playerToSelectTrumpName selecting..."
                else -> "Trump: Not Set"
            }
            Text(
                trumpText,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                color = when (trumpSuit) {
                    Suit.HEARTS, Suit.DIAMONDS -> Color.Red
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            leadSuit?.let {
                Text("Lead: ${it.name.first()}${it.symbol}", style = MaterialTheme.typography.labelSmall, color = if (it == Suit.HEARTS || it == Suit.DIAMONDS) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Tricks: $currentTrickCount/$totalTricks", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun PlayerArea(
    player: Player,
    viewModel: GameViewModel,
    alignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
    isCurrentTurn: Boolean,
    isSelectingTrump: Boolean,
    showHandFaceUp: Boolean,
    leadSuit: Suit?
) {
    val gamePhase = viewModel.gamePhase.value
    val handInteractionEnabled = player.id == "P1" &&
            ((isCurrentTurn && (
                    gamePhase == GamePhase.PLAYER_TURN ||
                            (gamePhase == GamePhase.TRUMP_SELECTION_DONE && viewModel.currentTrickCards.isEmpty()) ||
                            gamePhase == GamePhase.TRUMP_REVEALED ||
                            gamePhase == GamePhase.AWAITING_TRUMP_REVEAL_CHOICE
                    )) ||
                    (isSelectingTrump && player == viewModel.playerToSelectTrump.value && gamePhase == GamePhase.AWAITING_TRUMP_SELECTION))

    Column(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        Text(
            text = player.name + if (viewModel.currentDealer.value == player) " (D)" else "",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isCurrentTurn || (isSelectingTrump && player == viewModel.playerToSelectTrump.value)) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrentTurn || (isSelectingTrump && player == viewModel.playerToSelectTrump.value)) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        PlayerHandView(
            player = player,
            onCardClick = { card ->
                if (player.id == "P1" && handInteractionEnabled) {
                    if (isSelectingTrump && player == viewModel.playerToSelectTrump.value && gamePhase == GamePhase.AWAITING_TRUMP_SELECTION) {
                        viewModel.selectHiddenTrump(player, card)
                    } else if (isCurrentTurn && player == viewModel.currentPlayerTurn.value) {
                        viewModel.playCard(player, card)
                    }
                }
            },
            enabled = handInteractionEnabled,
            showFaceUp = showHandFaceUp,
            highlightHandArea = handInteractionEnabled,
            leadSuit = if (player.id == "P1" && isCurrentTurn) leadSuit else null,
            isPlayer1Playing = player.id == "P1" && isCurrentTurn && (gamePhase == GamePhase.PLAYER_TURN || gamePhase == GamePhase.AWAITING_TRUMP_REVEAL_CHOICE || gamePhase == GamePhase.TRUMP_REVEALED)
        )
    }
}


@Composable
fun PlayerHandView(
    player: Player,
    onCardClick: (Card) -> Unit,
    enabled: Boolean,
    showFaceUp: Boolean,
    highlightHandArea: Boolean,
    leadSuit: Suit?,
    isPlayer1Playing: Boolean,
    cardWidth: Dp = 55.dp,
    cardHeight: Dp = 82.dp
) {
    val handToDisplay = remember(player.hand.joinToString { it.instanceId }) {
        player.hand.toList()
    }

    Box(
        modifier = Modifier
            .height(cardHeight + 20.dp)
            .fillMaxWidth()
            .then(
                if (highlightHandArea) Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    RoundedCornerShape(10.dp)
                ) else Modifier
            )
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (handToDisplay.isEmpty()) {
            Box(
                modifier = Modifier
                    .width(cardWidth * 1.5f)
                    .height(cardHeight)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("No Cards", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(-(cardWidth / 2.2f), Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                items(handToDisplay, key = { card -> card.instanceId }) { card ->
                    val isCardActiveForSelection = enabled && showFaceUp

                    var isPlayableCardForHuman = false
                    if (isPlayer1Playing && showFaceUp) {
                        if (leadSuit == null) {
                            isPlayableCardForHuman = true
                        } else {
                            isPlayableCardForHuman = card.suit == leadSuit || player.hand.none { it.suit == leadSuit }
                        }
                    }

                    CardView(
                        card = card,
                        isFaceDown = !showFaceUp,
                        isPlayer1ActiveCard = isCardActiveForSelection,
                        highlightAsPlayable = isPlayableCardForHuman,
                        modifier = Modifier
                            .width(cardWidth)
                            .height(cardHeight)
                            .clickable(
                                enabled = isCardActiveForSelection && (if (isPlayer1Playing) isPlayableCardForHuman else true)
                            ) {
                                if (isCardActiveForSelection && (if (isPlayer1Playing) isPlayableCardForHuman else true)) {
                                    onCardClick(card)
                                }
                            }
                    )
                }
            }
        }
    }
}


@Composable
fun CardView(
    card: Card,
    modifier: Modifier = Modifier,
    isFaceDown: Boolean = false,
    isPlayer1ActiveCard: Boolean = false,
    highlightAsPlayable: Boolean = false
) {
    val cardColor = if (card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS) Color.Red else MaterialTheme.colorScheme.onSurface
    val cardShape = RoundedCornerShape(8.dp)

    val offsetY by animateDpAsState(
        targetValue = if (highlightAsPlayable && !isFaceDown) {
            (-20).dp
        } else {
            0.dp
        },
        label = "cardOffsetY"
    )

    val elevation by animateDpAsState(
        targetValue = if ((isPlayer1ActiveCard || highlightAsPlayable) && !isFaceDown) 8.dp else if (!isFaceDown) 3.dp else 1.dp,
        label = "cardElevation"
    )
    val scale by animateFloatAsState(
        targetValue = if ((isPlayer1ActiveCard || highlightAsPlayable) && !isFaceDown) 1.03f else 1.0f,
        label = "cardScale"
    )

    val surfaceColorBase = if (isFaceDown) Color(0xFF757575) else MaterialTheme.colorScheme.surfaceBright
    val animatedSurfaceColor by animateColorAsState(
        targetValue = if (highlightAsPlayable && !isFaceDown) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else if (isPlayer1ActiveCard && !isFaceDown) {
            MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.9f)
        }
        else {
            surfaceColorBase
        },
        label = "cardSurfaceColor"
    )

    val finalBorder = when {
        highlightAsPlayable && !isFaceDown -> BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
        isPlayer1ActiveCard && !isFaceDown -> BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
        else -> BorderStroke(1.dp, if (isFaceDown) Color.DarkGray else MaterialTheme.colorScheme.outlineVariant)
    }


    val surfaceModifier = modifier
        .offset(y = offsetY)
        .scale(scale)
        .shadow(elevation = elevation, shape = cardShape)

    Surface(
        modifier = surfaceModifier,
        shape = cardShape,
        color = animatedSurfaceColor,
        border = finalBorder
    ) {
        if (isFaceDown) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color(0xFF8D8D8D), Color(0xFF616161))
                        ),
                        shape = cardShape
                    )
                    .border(1.dp, Color.Black.copy(alpha = 0.7f), cardShape),
                contentAlignment = Alignment.Center
            ) {}
        } else {
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)) {
                Text(
                    text = card.rank.symbol,
                    style = TextStyle(color = cardColor, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 3.dp, top = 1.dp)
                )
                Text(
                    text = card.suit.symbol,
                    style = TextStyle(color = cardColor, fontSize = 22.sp),
                    modifier = Modifier.align(Alignment.Center)
                )
                Text(
                    text = card.rank.symbol,
                    style = TextStyle(color = cardColor, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 3.dp, bottom = 1.dp)
                        .rotate(180f)
                )
            }
        }
    }
}

@Composable
fun CurrentTrickView(trickCards: List<Pair<Player, Card>>, players: List<Player>, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (trickCards.isEmpty()) {
            Text("Table", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            trickCards.forEach { (player, card) ->
                val playerVisualIndex = players.indexOfFirst { it.id == player.id }

                val alignment = when (playerVisualIndex) {
                    0 -> Alignment.BottomCenter
                    1 -> Alignment.CenterStart
                    2 -> Alignment.TopCenter
                    3 -> Alignment.CenterEnd
                    else -> Alignment.Center
                }
                val cardPadding = 18.dp
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = if (alignment == Alignment.CenterStart) cardPadding else 0.dp,
                            end = if (alignment == Alignment.CenterEnd) cardPadding else 0.dp,
                            top = if (alignment == Alignment.TopCenter) cardPadding else 0.dp,
                            bottom = if (alignment == Alignment.BottomCenter) cardPadding else 0.dp
                        ),
                    contentAlignment = alignment
                ) {
                    CardView(
                        card = card,
                        isPlayer1ActiveCard = false,
                        highlightAsPlayable = false,
                        modifier = Modifier.size(width = 50.dp, height = 75.dp)
                    )
                }
            }
        }
    }
}


@Composable
fun TeamScoresView(teams: List<Team>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        teams.forEach { team ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    team.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Tricks: ${team.tricksWonThisDeal.value}", style = MaterialTheme.typography.bodyMedium)
                Text("Tens: ${team.tensCapturedThisDeal.value}", style = MaterialTheme.typography.bodyMedium)
                if (team.overallScore.value > 0 || teams.any { it.overallScore.value > 0}) {
                    Divider(modifier = Modifier.padding(vertical = 6.dp).width(80.dp))
                    Text(
                        "Deals Won: ${team.overallScore.value}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 850)
@Composable
fun MindiCotGameSetupScreenPreview() {
    MindiCotGameTheme {
        val previewViewModel = GameViewModel()
        MindiCotGameScreen(viewModel = previewViewModel)
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 850)
@Composable
fun MindiCotGameTableDuringPlayPreview() {
    MindiCotGameTheme {
        val previewViewModel = GameViewModel()
        previewViewModel.setBandHukumVariant(BandHukumVariant.VERSION_A)
        previewViewModel.setAiDifficulty(AiPlayerLogic.Difficulty.MEDIUM)
        previewViewModel.startNewDeal()

        val player1 = previewViewModel.players.find { it.id == "P1" }
        if (player1 != null) {
            if (previewViewModel.playerToSelectTrump.value != player1 ||
                (previewViewModel.gamePhase.value == GamePhase.TRUMP_SELECTION_DONE && previewViewModel.currentPlayerTurn.value != player1)) {
            }
            previewViewModel.currentPlayerTurn.value = player1
            previewViewModel.gamePhase.value = GamePhase.PLAYER_TURN

            if (previewViewModel.currentTrickCards.isEmpty() && player1.hand.isNotEmpty()) {
                previewViewModel.leadSuitForCurrentTrick.value = player1.hand.first().suit
            }
        }
        MindiCotGameScreen(viewModel = previewViewModel)
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 850)
@Composable
fun MindiCotGameTablePlayer1SelectingTrumpPreview() {
    MindiCotGameTheme {
        val previewViewModel = GameViewModel()
        previewViewModel.setBandHukumVariant(BandHukumVariant.VERSION_A)
        previewViewModel.setAiDifficulty(AiPlayerLogic.Difficulty.EASY)
        previewViewModel.startNewDeal()

        val player1 = previewViewModel.players.find { it.id == "P1" }
        if (player1 != null) {
            previewViewModel.playerToSelectTrump.value = player1
            previewViewModel.currentPlayerTurn.value = player1
            previewViewModel.gamePhase.value = GamePhase.AWAITING_TRUMP_SELECTION
            previewViewModel.gameMessage.value = "${player1.name}, please select your hidden trump card."
        }
        MindiCotGameScreen(viewModel = previewViewModel)
    }
}
