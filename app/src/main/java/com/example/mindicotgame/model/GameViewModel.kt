package com.example.mindicotgame

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindicotgame.model.Card
import com.example.mindicotgame.model.Deck
import com.example.mindicotgame.model.Player
import com.example.mindicotgame.model.Suit
import com.example.mindicotgame.model.Team
import com.example.mindicotgame.model.BandHukumVariant
import com.example.mindicotgame.logic.AiPlayerLogic
import com.example.mindicotgame.logic.AiDecision
import com.example.mindicotgame.logic.AiGameState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Collections

enum class GamePhase {
    NOT_STARTED,
    SETUP_GAME,
    DEALING,
    AWAITING_TRUMP_SELECTION,
    TRUMP_SELECTION_DONE,
    PLAYER_TURN,
    AWAITING_TRUMP_REVEAL_CHOICE,
    TRUMP_REVEALED,
    TRICK_COMPLETED,
    CONCLUDING_TRICK,
    DEAL_COMPLETED,
    GAME_OVER,
    AI_THINKING
}

fun Team.resetDealStats() {
    this.tricksWonThisDeal.value = 0
    this.tensCapturedThisDeal.value = 0
}


class GameViewModel : ViewModel() {
    val players = mutableStateListOf<Player>()
    val teams = mutableStateListOf<Team>()
    private val deck = Deck()

    var bandHukumVariant = mutableStateOf(BandHukumVariant.VERSION_A)
    var aiDifficulty = mutableStateOf(AiPlayerLogic.Difficulty.MEDIUM)

    var currentDealer = mutableStateOf<Player?>(null)
    var playerToSelectTrump = mutableStateOf<Player?>(null)
    var hiddenTrumpCardState = mutableStateOf<Card?>(null)
    var revealedTrumpSuit = mutableStateOf<Suit?>(null)
    var currentPlayerTurn = mutableStateOf<Player?>(null)
    val currentTrickCards = mutableStateListOf<Pair<Player, Card>>()
    var leadSuitForCurrentTrick = mutableStateOf<Suit?>(null)
    var tricksPlayedThisDeal = mutableStateOf(0)

    var playerCanRequestTrumpReveal = mutableStateOf(false)
    var playerMustPlayTrumpIfAble = mutableStateOf(false)
    var trumpWasRevealedThisTrick = mutableStateOf(false)
    var cardIndexAtTrumpReveal = mutableStateOf(-1)

    var gamePhase = mutableStateOf(GamePhase.SETUP_GAME)
    var gameMessage = mutableStateOf("Setup game: Select variant and AI difficulty.")

    private var aiTurnJob: Job? = null
    private val humanPlayerId = "P1"
    private val allPlayedCardsInCurrentDeal = mutableSetOf<Card>()
    private var trickConclusionJob: Job? = null


    init {
        setupInitialPlayersAndTeams()
        AiPlayerLogic.difficulty = aiDifficulty.value
    }

    private fun setupInitialPlayersAndTeams() {
        players.clear()
        teams.clear()
        val p1 = Player("P1", "Player 1 (You)")
        val p2 = Player("P2", "Player 2 (AI)")
        val p3 = Player("P3", "Player 3 (AI)")
        val p4 = Player("P4", "Player 4 (AI)")
        players.addAll(listOf(p1, p2, p3, p4))

        val teamA = Team("A", "Team A (P1 & P3)", listOf(p1, p3))
        val teamB = Team("B", "Team B (P2 & P4)", listOf(p2, p4))
        teams.addAll(listOf(teamA, teamB))
        currentDealer.value = p1
        currentPlayerTurn.value = p1
    }

    fun setBandHukumVariant(variant: BandHukumVariant) {
        bandHukumVariant.value = variant
        gameMessage.value = "Variant ${variant.name} selected. AI Difficulty: ${aiDifficulty.value.name}."
    }

    fun setAiDifficulty(difficulty: AiPlayerLogic.Difficulty) {
        aiDifficulty.value = difficulty
        AiPlayerLogic.difficulty = difficulty
        gameMessage.value = "Variant ${bandHukumVariant.value.name} selected. AI Difficulty: ${difficulty.name}."
    }


    fun startNewDeal() {
        println("DEBUG: startNewDeal called. Current Dealer: ${currentDealer.value?.name}")
        aiTurnJob?.cancel()
        trickConclusionJob?.cancel()

        AiPlayerLogic.difficulty = aiDifficulty.value
        println("DEBUG: AI Difficulty set to ${AiPlayerLogic.difficulty}")

        if (players.isEmpty()) setupInitialPlayersAndTeams()
        deck.reset()
        players.forEach { it.clearHand() }
        teams.forEach { it.resetDealStats() }
        allPlayedCardsInCurrentDeal.clear()
        currentTrickCards.clear()
        hiddenTrumpCardState.value = null
        revealedTrumpSuit.value = null
        leadSuitForCurrentTrick.value = null
        tricksPlayedThisDeal.value = 0
        playerCanRequestTrumpReveal.value = false
        playerMustPlayTrumpIfAble.value = false
        trumpWasRevealedThisTrick.value = false
        cardIndexAtTrumpReveal.value = -1

        val dealer = currentDealer.value ?: players.first()
        currentDealer.value = dealer
        println("DEBUG: Dealer confirmed: ${dealer.name}")
        gamePhase.value = GamePhase.DEALING
        gameMessage.value = "${dealer.name} is dealing..."

        val dealerIndex = players.indexOf(dealer)
        if (dealerIndex == -1) {
            println("ERROR: Dealer not found in players list!")
            return
        }
        val tempPlayersDealingOrder = players.toMutableList()
        Collections.rotate(tempPlayersDealingOrder, -((dealerIndex + 1) % players.size))

        val dealBatches = listOf(5, 4, 4)
        for (batchSize in dealBatches) {
            for (player in tempPlayersDealingOrder) {
                player.addCards(deck.dealMultipleCards(batchSize))
            }
        }
        println("DEBUG: Dealing complete.")
        players.forEach { it.sortHand() }

        val trumpSelectorIndex = (players.indexOf(currentDealer.value!!) + 1) % players.size
        playerToSelectTrump.value = players[trumpSelectorIndex]
        println("DEBUG: Player to select trump: ${playerToSelectTrump.value?.name}")

        currentPlayerTurn.value = playerToSelectTrump.value
        println("DEBUG: CurrentPlayerTurn set to selector: ${currentPlayerTurn.value?.name}")

        gamePhase.value = GamePhase.AWAITING_TRUMP_SELECTION
        gameMessage.value = "${playerToSelectTrump.value?.name}, please select your hidden trump card."
        println("DEBUG: Phase set to AWAITING_TRUMP_SELECTION. Calling checkAndTriggerAiAction.")
        checkAndTriggerAiAction()
    }

    fun selectHiddenTrump(player: Player, card: Card) {
        println("DEBUG: viewModel.selectHiddenTrump called by ${player.name} with card ${card}. Expected Selector: ${playerToSelectTrump.value?.name}, Current Phase: ${gamePhase.value}")
        val currentPhase = gamePhase.value
        if (player == playerToSelectTrump.value &&
            (currentPhase == GamePhase.AWAITING_TRUMP_SELECTION || (currentPhase == GamePhase.AI_THINKING && player.id != humanPlayerId))) {

            if (player.hand.any { it.instanceId == card.instanceId }) {
                hiddenTrumpCardState.value = card
                println("DEBUG: ${player.name} selected hidden trump: $card. Phase changing to TRUMP_SELECTION_DONE.")
                gamePhase.value = GamePhase.TRUMP_SELECTION_DONE
                currentPlayerTurn.value = playerToSelectTrump.value
                println("DEBUG: currentPlayerTurn set to leader: ${currentPlayerTurn.value?.name}")
                gameMessage.value = "Hidden trump selected. ${currentPlayerTurn.value?.name} to lead."
                checkAndTriggerAiAction()
            } else {
                if (player.id == humanPlayerId) gameMessage.value = "Invalid card selection. Card not in hand."
                else {
                    println("AI Error: ${player.name} tried to select trump not in hand: $card. Hand: ${player.hand.joinToString()}")
                    if (player.hand.isNotEmpty()) {
                        val recoveryCard = AiPlayerLogic.chooseHiddenTrumpCard(player)
                        println("AI Recovery: Attempting to select ${recoveryCard} instead.")
                        selectHiddenTrump(player, recoveryCard)
                    } else {
                        println("AI Error: Hand empty during recovery.")
                        gamePhase.value = GamePhase.DEAL_COMPLETED
                        gameMessage.value = "Error: AI hand empty during trump selection."
                    }
                }
            }
        } else {
            println("DEBUG: selectHiddenTrump called at wrong time or by wrong player. Player: ${player.name}, Expected Selector: ${playerToSelectTrump.value?.name}, Phase: $currentPhase")
        }
    }


    fun requestTrumpReveal(requestingPlayer: Player) {
        println("DEBUG: requestTrumpReveal called by ${requestingPlayer.name}. Phase: ${gamePhase.value}, CanRequest: ${playerCanRequestTrumpReveal.value}")
        if (bandHukumVariant.value == BandHukumVariant.VERSION_B &&
            revealedTrumpSuit.value == null &&
            hiddenTrumpCardState.value != null &&
            (playerCanRequestTrumpReveal.value || requestingPlayer.id != humanPlayerId) &&
            currentPlayerTurn.value == requestingPlayer &&
            (gamePhase.value == GamePhase.AWAITING_TRUMP_REVEAL_CHOICE || (gamePhase.value == GamePhase.AI_THINKING && requestingPlayer.id != humanPlayerId)) ) {

            val trumpToReveal = hiddenTrumpCardState.value!!
            println("DEBUG: Revealing trump: ${trumpToReveal.suit}")
            revealedTrumpSuit.value = trumpToReveal.suit
            trumpWasRevealedThisTrick.value = true
            cardIndexAtTrumpReveal.value = currentTrickCards.size
            playerCanRequestTrumpReveal.value = false
            playerMustPlayTrumpIfAble.value = true
            gamePhase.value = GamePhase.TRUMP_REVEALED
            gameMessage.value = "Trump is ${revealedTrumpSuit.value}! ${requestingPlayer.name}, you must play a trump if you have one."
            println("DEBUG: Phase set to TRUMP_REVEALED.")
            if (requestingPlayer.id != humanPlayerId) {
                checkAndTriggerAiAction()
            }
        } else {
            println("DEBUG: requestTrumpReveal conditions not met.")
        }
    }

    fun playCard(player: Player, card: Card) {
        val currentPhase = gamePhase.value
        println("DEBUG: playCard called by ${player.name} with ${card}. Phase: $currentPhase, CurrentTurn: ${currentPlayerTurn.value?.name}")

        val allowedPhases = listOf(
            GamePhase.PLAYER_TURN,
            GamePhase.TRUMP_SELECTION_DONE,
            GamePhase.TRUMP_REVEALED,
            GamePhase.AWAITING_TRUMP_REVEAL_CHOICE,
            GamePhase.AI_THINKING
        )
        if (player != currentPlayerTurn.value || currentPhase !in allowedPhases) {
            if (currentPhase == GamePhase.AI_THINKING && player.id != currentPlayerTurn.value?.id) {
                println("AI (${player.name}) tried to play during another player's AI_THINKING phase. CurrentTurn: ${currentPlayerTurn.value?.name}")
                return
            }
            else if (currentPhase != GamePhase.AI_THINKING) {
                if (player.id == humanPlayerId) gameMessage.value = "Not your turn or invalid game phase ($currentPhase)."
                else println("AI (${player.name}) tried to play out of sync. Phase: $currentPhase, Turn: ${currentPlayerTurn.value?.name}")
                return
            }
        }


        val cardInHand = player.hand.find { it.instanceId == card.instanceId }
        if (cardInHand == null) {
            if (player.id == humanPlayerId) gameMessage.value = "You don't have that card ($card)."
            else {
                println("CRITICAL AI ERROR: ${player.name} tried to play card not in hand: $card. Hand: ${player.hand.joinToString()}")
                if (player.hand.isNotEmpty()) {
                    val aiGameState = createAiGameStateForPlayer(player, false)
                    val recoveryDecision = AiPlayerLogic.decideNextAction(player, aiGameState)
                    if (recoveryDecision is AiDecision.PlayCard && player.hand.any { it.instanceId == recoveryDecision.card.instanceId }) {
                        println("AI Recovery: Playing ${recoveryDecision.card} instead.")
                        playCard(player, recoveryDecision.card)
                        return
                    } else {
                        println("FATAL: AI (${player.name}) could not recover. Decision: $recoveryDecision")
                        gamePhase.value = GamePhase.DEAL_COMPLETED
                        gameMessage.value = "Error: AI ${player.name} state invalid. Please restart deal."
                        return
                    }
                } else {
                    println("FATAL: AI (${player.name}) hand is empty but tried to play.")
                    gamePhase.value = GamePhase.DEAL_COMPLETED
                    gameMessage.value = "Error: AI ${player.name} hand empty. Please restart deal."
                    return
                }
            }
            return
        }


        if (playerMustPlayTrumpIfAble.value) {
            if (revealedTrumpSuit.value != null && card.suit != revealedTrumpSuit.value && player.hand.any { it.suit == revealedTrumpSuit.value }) {
                if (player.id == humanPlayerId) gameMessage.value = "You asked for trump. You must play a trump card if you have one."
                return
            }
            if (revealedTrumpSuit.value != null && card.suit == revealedTrumpSuit.value) {
                playerMustPlayTrumpIfAble.value = false
            }
        }

        val isFirstCardOfTrick = currentTrickCards.isEmpty()
        if (isFirstCardOfTrick) {
            leadSuitForCurrentTrick.value = card.suit
        } else {
            val lead = leadSuitForCurrentTrick.value!!
            val playerHasLeadSuit = player.hand.any { it.suit == lead }
            if (card.suit != lead && playerHasLeadSuit) {
                if (player.id == humanPlayerId) gameMessage.value = "You must follow suit ($lead)."
                return
            }
            if (card.suit != lead && !playerHasLeadSuit) {
                if (revealedTrumpSuit.value == null && hiddenTrumpCardState.value != null) {
                    if (bandHukumVariant.value == BandHukumVariant.VERSION_A) {
                        println("DEBUG: Auto-revealing trump (Version A).")
                        revealedTrumpSuit.value = hiddenTrumpCardState.value!!.suit
                        trumpWasRevealedThisTrick.value = true
                        cardIndexAtTrumpReveal.value = currentTrickCards.size
                        gameMessage.value = "${player.name} could not follow. Trump is ${revealedTrumpSuit.value}! ${player.name} plays ${card}."
                    } else {
                        playerCanRequestTrumpReveal.value = false
                        println("DEBUG: Player ${player.name} broke suit in Version B without requesting trump.")
                    }
                }
                if(revealedTrumpSuit.value != null && card.suit == revealedTrumpSuit.value) {
                    playerMustPlayTrumpIfAble.value = false
                }
            }
        }

        player.removeCard(cardInHand)
        currentTrickCards.add(Pair(player, cardInHand))
        allPlayedCardsInCurrentDeal.add(Card(cardInHand.suit, cardInHand.rank, "played_${cardInHand.suit}_${cardInHand.rank}_${System.nanoTime()}"))

        println("DEBUG: ${player.name} played ${card}. Trick size: ${currentTrickCards.size}")


        if (currentTrickCards.size < players.size) {
            val currentPlayerIndex = players.indexOf(player)
            val nextPlayer = players[(currentPlayerIndex + 1) % players.size]
            currentPlayerTurn.value = nextPlayer
            playerCanRequestTrumpReveal.value = false

            if (bandHukumVariant.value == BandHukumVariant.VERSION_B &&
                revealedTrumpSuit.value == null &&
                hiddenTrumpCardState.value != null &&
                leadSuitForCurrentTrick.value != null &&
                !nextPlayer.hand.any { it.suit == leadSuitForCurrentTrick.value!! }) {
                playerCanRequestTrumpReveal.value = true
                gamePhase.value = GamePhase.AWAITING_TRUMP_REVEAL_CHOICE
                println("DEBUG: Phase set to AWAITING_TRUMP_REVEAL_CHOICE for ${nextPlayer.name}")
                if (nextPlayer.id == humanPlayerId) {
                    gameMessage.value = "${nextPlayer.name}'s turn. You cannot follow suit ${leadSuitForCurrentTrick.value}. You may ask to reveal trump or play a card."
                }
            } else {
                if(gamePhase.value != GamePhase.AWAITING_TRUMP_REVEAL_CHOICE) {
                    gamePhase.value = GamePhase.PLAYER_TURN
                }
                println("DEBUG: Phase set/kept PLAYER_TURN for ${nextPlayer.name}")
                if (gamePhase.value == GamePhase.PLAYER_TURN && nextPlayer.id == humanPlayerId) {
                    gameMessage.value = "${nextPlayer.name}'s turn."
                }
            }
            checkAndTriggerAiAction()
        } else {
            println("DEBUG: Trick complete. Phase set to TRICK_COMPLETED.")
            gamePhase.value = GamePhase.TRICK_COMPLETED
            processTrickCompletion()
        }
    }

    private fun processTrickCompletion() {
        println("DEBUG: processTrickCompletion started.")
        trickConclusionJob?.cancel()

        val trickWinnerPair = determineTrickWinner()
        val trickWinningPlayer = trickWinnerPair.first
        val winningTeam = teams.find { it.players.contains(trickWinningPlayer) }

        winningTeam?.let {
            it.tricksWonThisDeal.value++
            currentTrickCards.toList().forEach { (_, card) ->
                if (card.rank.isTen) {
                    it.tensCapturedThisDeal.value++
                }
            }
            println("DEBUG: Trick winner ${trickWinningPlayer.name} (${it.name}). Team Tricks: ${it.tricksWonThisDeal.value}, Tens: ${it.tensCapturedThisDeal.value}")
        } ?: println("ERROR: Could not find winning team for player ${trickWinningPlayer.name}")


        gameMessage.value = "${trickWinningPlayer.name} wins the trick with ${trickWinnerPair.second}."

        trickConclusionJob = viewModelScope.launch {
            delay(2500)
            println("DEBUG: Concluding trick after delay. Incrementing tricksPlayedThisDeal.")
            tricksPlayedThisDeal.value++
            println("DEBUG: Tricks played this deal: ${tricksPlayedThisDeal.value}")
            concludeTrickAndProceed(trickWinningPlayer)
        }
    }

    private fun concludeTrickAndProceed(trickWinner: Player) {
        println("DEBUG: concludeTrickAndProceed started for winner ${trickWinner.name}. Current phase: ${gamePhase.value}")
        if (gamePhase.value != GamePhase.TRICK_COMPLETED) {
            println("DEBUG: concludeTrickAndProceed aborted, phase is ${gamePhase.value}")
            return
        }

        gamePhase.value = GamePhase.CONCLUDING_TRICK

        currentTrickCards.clear()
        leadSuitForCurrentTrick.value = null
        trumpWasRevealedThisTrick.value = false
        cardIndexAtTrumpReveal.value = -1
        playerMustPlayTrumpIfAble.value = false
        playerCanRequestTrumpReveal.value = false
        println("DEBUG: Trick concluded, state reset.")

        currentPlayerTurn.value = trickWinner

        if (tricksPlayedThisDeal.value >= 13) {
            println("DEBUG: All 13 tricks played. Calling endDeal.")
            endDeal()
        } else {
            println("DEBUG: Moving to next trick. Winner ${trickWinner.name} leads. Phase: PLAYER_TURN.")
            gamePhase.value = GamePhase.PLAYER_TURN
            if (trickWinner.id == humanPlayerId) {
                gameMessage.value = "${trickWinner.name} to lead next."
            } else {
                gameMessage.value = "${trickWinner.name} leads."
            }
            checkAndTriggerAiAction()
        }
    }

    private fun determineTrickWinner(): Pair<Player, Card> {
        if (currentTrickCards.isEmpty()) throw IllegalStateException("Cannot determine winner of an empty trick.")

        var currentWinningPair = currentTrickCards.first()
        val leadSuit = currentTrickCards.first().second.suit
        val trumpSuit = revealedTrumpSuit.value

        println("DEBUG: Determining winner. Lead: $leadSuit, Trump: $trumpSuit, Trick: ${currentTrickCards.joinToString { it.second.toString() }}")

        fun isEffectivelyTrump(card: Card, cardIndexInTrick: Int): Boolean {
            if (trumpSuit == null || card.suit != trumpSuit) return false
            return !trumpWasRevealedThisTrick.value || cardIndexInTrick >= cardIndexAtTrumpReveal.value
        }

        for (i in 1 until currentTrickCards.size) {
            val nextPair = currentTrickCards[i]
            val nextCard = nextPair.second
            val cardCurrentlyWinning = currentWinningPair.second

            val indexOfWinnerSoFar = currentTrickCards.indexOfFirst { it.first.id == currentWinningPair.first.id && it.second.instanceId == cardCurrentlyWinning.instanceId }
            if (indexOfWinnerSoFar == -1) {
                println("CRITICAL ERROR: Cannot find index of current winning pair in determineTrickWinner.")
                throw IllegalStateException("Cannot find index of current winning pair in determineTrickWinner")
            }
            val isCurrentWinnerTrump = isEffectivelyTrump(cardCurrentlyWinning, indexOfWinnerSoFar)
            val isNextCardTrump = isEffectivelyTrump(nextCard, i)


            if (isCurrentWinnerTrump) {
                if (isNextCardTrump && nextCard.rank.numericValue > cardCurrentlyWinning.rank.numericValue) {
                    currentWinningPair = nextPair
                }
            } else if (isNextCardTrump) {
                currentWinningPair = nextPair
            } else {
                if (nextCard.suit == leadSuit && cardCurrentlyWinning.suit != leadSuit) {
                    currentWinningPair = nextPair
                } else if (nextCard.suit == leadSuit && nextCard.rank.numericValue > cardCurrentlyWinning.rank.numericValue) {
                    currentWinningPair = nextPair
                }
            }
        }

        println("DEBUG: Final Trick Winner: ${currentWinningPair.first.name} with ${currentWinningPair.second}")
        return currentWinningPair
    }

    private fun endDeal() {
        println("DEBUG: endDeal started.")
        aiTurnJob?.cancel()
        trickConclusionJob?.cancel()
        gamePhase.value = GamePhase.DEAL_COMPLETED
        val teamA = teams.find { it.id == "A" } ?: return
        val teamB = teams.find { it.id == "B" } ?: return

        var dealWinner: Team? = null
        var resultMessage = "\n--- Deal Ended ---\n"
        resultMessage += "${teamA.name}: ${teamA.tensCapturedThisDeal.value} Tens, ${teamA.tricksWonThisDeal.value} Tricks\n"
        resultMessage += "${teamB.name}: ${teamB.tensCapturedThisDeal.value} Tens, ${teamB.tricksWonThisDeal.value} Tricks\n"

        val teamATens = teamA.tensCapturedThisDeal.value
        val teamBTens = teamB.tensCapturedThisDeal.value
        val teamATricks = teamA.tricksWonThisDeal.value
        val teamBTricks = teamB.tricksWonThisDeal.value

        when {
            teamATens == 4 -> { dealWinner = teamA; resultMessage += "${teamA.name} wins by Mendikot (all 4 Tens)!" }
            teamBTens == 4 -> { dealWinner = teamB; resultMessage += "${teamB.name} wins by Mendikot (all 4 Tens)!" }
            teamATens == 3 -> { dealWinner = teamA; resultMessage += "${teamA.name} wins with 3 Tens!" }
            teamBTens == 3 -> { dealWinner = teamB; resultMessage += "${teamB.name} wins with 3 Tens!" }
            teamATens == 2 && teamBTens == 2 -> {
                if (teamATricks >= 7) { dealWinner = teamA; resultMessage += "${teamA.name} wins (2 Tens each, $teamATricks tricks)!" }
                else if (teamBTricks >= 7) { dealWinner = teamB; resultMessage += "${teamB.name} wins (2 Tens each, $teamBTricks tricks)!" }
                else { resultMessage += "Draw on Tens (2-2), and no team has 7+ tricks. Deal is a draw." }
            }
            teamATens > teamBTens -> { dealWinner = teamA; resultMessage += "${teamA.name} wins with more Tens ($teamATens vs $teamBTens)!" }
            teamBTens > teamATens -> { dealWinner = teamB; resultMessage += "${teamB.name} wins with more Tens ($teamBTens vs $teamATens)!" }
            teamATens == teamBTens -> {
                if (teamATricks >= 7) { dealWinner = teamA; resultMessage += "${teamA.name} wins with $teamATricks tricks (Tens were equal)!" }
                else if (teamBTricks >= 7) { dealWinner = teamB; resultMessage += "${teamB.name} wins with $teamBTricks tricks (Tens were equal)!" }
                else { resultMessage += "Draw on Tens ($teamATens-$teamBTens), and no team has 7+ tricks. Deal is a draw." }
            }
        }

        dealWinner?.let { winner ->
            teams.find { it.id == winner.id }?.overallScore?.value++
            println("DEBUG: Deal Winner: ${winner.name}. Overall Score: ${winner.overallScore.value}")
        } ?: println("DEBUG: Deal resulted in a draw.")


        val previousDealer = currentDealer.value ?: players.first()
        val dealerTeam = teams.find { it.players.contains(previousDealer) }!!
        val whitewashAgainstLosingTeam = dealWinner != null && teams.find { it != dealWinner }?.tricksWonThisDeal?.value == 0 && dealWinner.tricksWonThisDeal.value == 13

        if (dealWinner == dealerTeam) {
            resultMessage += "\nDealer's team (${dealerTeam.name}) won the deal."
            if (dealWinner.tricksWonThisDeal.value == 13) resultMessage += " Whitewash (all 13 tricks)!"
            val dealerIndex = players.indexOf(previousDealer)
            currentDealer.value = players[(dealerIndex + 1) % players.size]
            resultMessage += "\n${currentDealer.value?.name} will deal next."
        } else if (dealWinner != null) {
            resultMessage += "\nDealer's team (${dealerTeam.name}) lost the deal."
            if (whitewashAgainstLosingTeam) {
                resultMessage += " Lost by Whitewash to ${dealWinner.name}!"
                currentDealer.value = dealerTeam.players.find { it != previousDealer }!!
                resultMessage += "\n${currentDealer.value?.name} (dealer's partner) will deal next."
            } else {
                currentDealer.value = previousDealer
                resultMessage += "\n${currentDealer.value?.name} (dealer) deals again."
            }
        } else {
            resultMessage += "\nThe deal is a draw."
            currentDealer.value = previousDealer
            resultMessage += "\n${currentDealer.value?.name} (dealer) deals again."
        }

        println("DEBUG: End Deal Message: $resultMessage")
        println("DEBUG: Next Dealer: ${currentDealer.value?.name}")
        gameMessage.value = resultMessage
    }


    private fun createAiGameStateForPlayer(player: Player, isSelectingTrumpPhase: Boolean): AiGameState {
        val playerTeam = teams.find { team -> team.players.any { p -> p.id == player.id } }
            ?: throw IllegalStateException("Player ${player.name} (${player.id}) not found in any team")
        val opponentTeam = teams.find { it != playerTeam }
            ?: throw IllegalStateException("Opponent team not found")
        val tensMap = teams.associateWith { it.tensCapturedThisDeal.value }

        return AiGameState(
            leadSuit = leadSuitForCurrentTrick.value,
            revealedTrumpSuit = revealedTrumpSuit.value,
            hiddenTrumpCardIsSet = hiddenTrumpCardState.value != null,
            bandHukumVariant = bandHukumVariant.value,
            playerCanRequestTrumpReveal = playerCanRequestTrumpReveal.value && currentPlayerTurn.value == player,
            playerMustPlayTrumpIfAble = playerMustPlayTrumpIfAble.value && currentPlayerTurn.value == player,
            currentTrick = currentTrickCards.toList(),
            remainingTricks = 13 - tricksPlayedThisDeal.value,
            isSelectingHiddenTrump = isSelectingTrumpPhase && playerToSelectTrump.value == player,
            playerTeam = playerTeam,
            opponentTeam = opponentTeam,
            teamTensCount = tensMap,
            playedCardsThisDeal = allPlayedCardsInCurrentDeal.toSet()
        )
    }

    private fun checkAndTriggerAiAction() {
        aiTurnJob?.cancel()
        trickConclusionJob?.cancel()

        val currentP = currentPlayerTurn.value
        val currentPhase = gamePhase.value

        println("DEBUG: checkAndTriggerAiAction - CurrentPlayer: ${currentP?.name}, Selector: ${playerToSelectTrump.value?.name}, Phase: $currentPhase")

        if (currentP == null || currentP.id == humanPlayerId) {
            println("DEBUG: AI Action check skipped. Player null or Human.")
            return
        }

        val isAiTurnToSelectTrump = currentPhase == GamePhase.AWAITING_TRUMP_SELECTION &&
                currentP == playerToSelectTrump.value

        val isAiTurnToPlayOrRequest = currentPhase == GamePhase.PLAYER_TURN ||
                currentPhase == GamePhase.AWAITING_TRUMP_REVEAL_CHOICE ||
                currentPhase == GamePhase.TRUMP_REVEALED ||
                (currentPhase == GamePhase.TRUMP_SELECTION_DONE && currentP == playerToSelectTrump.value)

        if (isAiTurnToSelectTrump || isAiTurnToPlayOrRequest) {
            if (currentPhase == GamePhase.CONCLUDING_TRICK || currentPhase == GamePhase.TRICK_COMPLETED) {
                println("DEBUG: AI Action (${currentP.name}) pre-empted, trick is concluding/completed. Phase: $currentPhase")
                return
            }

            println("DEBUG: AI Action Triggered for ${currentP.name}. Is Select Trump Task: $isAiTurnToSelectTrump, Is Play/Request Task: $isAiTurnToPlayOrRequest")

            aiTurnJob = viewModelScope.launch {
                if (isAiTurnToSelectTrump) {
                    println("DEBUG: Delaying 1500ms for Trump Selection message visibility.")
                    delay(1500)
                }

                val livePlayerPreThink = currentPlayerTurn.value
                val livePhasePreThink = gamePhase.value

                val stillNeedsToSelectTrump = livePhasePreThink == GamePhase.AWAITING_TRUMP_SELECTION && livePlayerPreThink == playerToSelectTrump.value
                val stillNeedsToPlayOrRequest = livePlayerPreThink != null && livePlayerPreThink.id != humanPlayerId &&
                        (livePhasePreThink == GamePhase.PLAYER_TURN ||
                                livePhasePreThink == GamePhase.AWAITING_TRUMP_REVEAL_CHOICE ||
                                livePhasePreThink == GamePhase.TRUMP_REVEALED ||
                                (livePhasePreThink == GamePhase.TRUMP_SELECTION_DONE && livePlayerPreThink == playerToSelectTrump.value))

                if (livePlayerPreThink != currentP || !( (isAiTurnToSelectTrump && stillNeedsToSelectTrump) || (isAiTurnToPlayOrRequest && stillNeedsToPlayOrRequest) ) ) {
                    println("DEBUG: AI (${currentP.name}) action cancelled PRE-THINK. Initial Turn: ${currentP.name}, Live Turn: ${livePlayerPreThink?.name}. Initial Phase: $currentPhase, Live Phase: $livePhasePreThink")
                    return@launch
                }

                gamePhase.value = GamePhase.AI_THINKING
                gameMessage.value = "${currentP.name} is thinking..."
                delay(900)

                val livePhase = gamePhase.value
                val liveCurrentPlayer = currentPlayerTurn.value
                if (liveCurrentPlayer != currentP || livePhase != GamePhase.AI_THINKING) {
                    println("DEBUG: AI (${currentP.name}) action cancelled POST-THINK. Expected Player: ${currentP.name}, Got: ${liveCurrentPlayer?.name}. Expected Phase: AI_THINKING, Got: $livePhase")
                    return@launch
                }
                if (currentP.hand.isEmpty()){
                    println("ERROR: AI (${currentP.name}) hand empty before making decision!")
                    gamePhase.value = GamePhase.DEAL_COMPLETED
                    gameMessage.value = "Error: AI ${currentP.name} hand empty. Please restart deal."
                    return@launch
                }

                val aiGameState = createAiGameStateForPlayer(currentP, isAiTurnToSelectTrump)
                val decision = AiPlayerLogic.decideNextAction(currentP, aiGameState)
                println("DEBUG: AI ${currentP.name} - Decision: $decision")
                delay(450)

                val livePhaseAfterDecision = gamePhase.value
                val liveCurrentPlayerAfter = currentPlayerTurn.value
                if (liveCurrentPlayerAfter != currentP || livePhaseAfterDecision != GamePhase.AI_THINKING) {
                    println("DEBUG: AI (${currentP.name}) action cancelled PRE-EXECUTION. Expected Player: ${currentP.name}, Got: ${liveCurrentPlayerAfter?.name}. Expected Phase: AI_THINKING, Got: $livePhaseAfterDecision")
                    return@launch
                }

                when (decision) {
                    is AiDecision.SelectHiddenTrump -> {
                        println("DEBUG: Executing SelectHiddenTrump for ${currentP.name}")
                        val selectedCard = AiPlayerLogic.chooseHiddenTrumpCard(currentP)
                        println("DEBUG: AI ${currentP.name} - Selected Trump Card: $selectedCard")
                        gameMessage.value = "${currentP.name} selects hidden trump."
                        delay(450)
                        selectHiddenTrump(currentP, selectedCard)
                    }
                    is AiDecision.PlayCard -> {
                        println("DEBUG: Executing PlayCard ${decision.card} for ${currentP.name}")
                        gameMessage.value = "${currentP.name} plays ${decision.card}"
                        delay(450)
                        playCard(currentP, decision.card)
                    }
                    is AiDecision.RequestTrumpReveal -> {
                        println("DEBUG: Executing RequestTrumpReveal for ${currentP.name}")
                        gameMessage.value = "${currentP.name} asks to reveal trump."
                        delay(450)
                        requestTrumpReveal(currentP)
                    }
                }
                println("DEBUG: AI (${currentP.name}) action processing complete.")
            }
        } else {
            if (currentP != null && currentP.id == humanPlayerId && currentPhase == GamePhase.AI_THINKING) {
                println("DEBUG: Reverting phase from AI_THINKING as it is now Human's turn (${currentP.name}).")
                val correctHumanPhase = when {
                    playerToSelectTrump.value == currentP && hiddenTrumpCardState.value == null -> GamePhase.AWAITING_TRUMP_SELECTION
                    playerCanRequestTrumpReveal.value && bandHukumVariant.value == BandHukumVariant.VERSION_B && leadSuitForCurrentTrick.value != null && !currentP.hand.any{it.suit == leadSuitForCurrentTrick.value} -> GamePhase.AWAITING_TRUMP_REVEAL_CHOICE
                    else -> GamePhase.PLAYER_TURN
                }
                gamePhase.value = correctHumanPhase
                when (correctHumanPhase) {
                    GamePhase.AWAITING_TRUMP_SELECTION -> gameMessage.value = "${currentP.name}, please select your hidden trump card."
                    GamePhase.AWAITING_TRUMP_REVEAL_CHOICE -> gameMessage.value = "${currentP.name}'s turn. You cannot follow suit ${leadSuitForCurrentTrick.value}. You may ask to reveal trump or play a card."
                    else -> gameMessage.value = "${currentP.name}'s turn."
                }
            }
        }
    }

}
