package com.example.mindicotgame.logic

import com.example.mindicotgame.model.*
import kotlin.random.Random

sealed class AiDecision {
    data class PlayCard(val card: Card) : AiDecision()
    object RequestTrumpReveal : AiDecision()
    object SelectHiddenTrump : AiDecision()
}

data class AiGameState(
    val leadSuit: Suit?,
    val revealedTrumpSuit: Suit?,
    val hiddenTrumpCardIsSet: Boolean,
    val bandHukumVariant: BandHukumVariant,
    val playerCanRequestTrumpReveal: Boolean,
    val playerMustPlayTrumpIfAble: Boolean,
    val currentTrick: List<Pair<Player, Card>>,
    val remainingTricks: Int,
    val isSelectingHiddenTrump: Boolean = false,
    val playerTeam: Team,
    val opponentTeam: Team,
    val teamTensCount: Map<Team, Int>,
    val playedCardsThisDeal: Set<Card>
)

object AiPlayerLogic {
    enum class Difficulty {
        EASY, MEDIUM, HARD
    }

    var difficulty: Difficulty = Difficulty.MEDIUM
    private val random = Random.Default

    fun chooseHiddenTrumpCard(player: Player): Card {
        require(player.hand.isNotEmpty()) { "AI player's hand is empty for trump selection. Player: ${player.name}" }

        if (difficulty == Difficulty.EASY) {
            return player.hand.random(random)
        }

        val suitStrengths = player.hand.groupBy { it.suit }
            .map { (suit, cards) ->
                var score = cards.size * 100
                score += cards.sumOf { it.rank.numericValue * 2 }
                if (cards.any { it.rank == Rank.ACE }) score += 50
                if (cards.any { it.rank == Rank.KING }) score += 40
                if (cards.any { it.rank == Rank.QUEEN }) score += 30
                if (cards.any { it.rank == Rank.TEN }) score += 60
                if (cards.any {it.rank == Rank.TEN} && cards.none {it.rank.numericValue > Rank.TEN.numericValue} && cards.size < 3) {
                    score -= 25
                }
                Pair(suit, score)
            }

        val bestSuit = suitStrengths.maxByOrNull { it.second }?.first
            ?: return player.hand.random(random)

        val cardsInBestSuit = player.hand.filter { it.suit == bestSuit }
        val tenInBestSuit = cardsInBestSuit.find { it.rank == Rank.TEN }
        if (tenInBestSuit != null && (cardsInBestSuit.size >= 4 || cardsInBestSuit.any { it.rank.numericValue > Rank.TEN.numericValue })) {
            return tenInBestSuit
        }
        return cardsInBestSuit.maxByOrNull { it.rank.numericValue } ?: player.hand.random(random)
    }

    fun decideNextAction(player: Player, gameState: AiGameState): AiDecision {
        require(player.hand.isNotEmpty()) { "AI player's hand is empty for deciding action. Player: ${player.name}" }

        if (gameState.isSelectingHiddenTrump) return AiDecision.SelectHiddenTrump
        if (shouldRequestTrumpReveal(player, gameState)) return AiDecision.RequestTrumpReveal
        if (mustPlayTrump(player, gameState)) {
            return playTrumpCard(player, gameState, true)
        }

        return decideCardToPlay(player, gameState)
    }

    private fun shouldRequestTrumpReveal(player: Player, gameState: AiGameState): Boolean {
        if (gameState.bandHukumVariant != BandHukumVariant.VERSION_B ||
            gameState.revealedTrumpSuit != null ||
            !gameState.hiddenTrumpCardIsSet ||
            !gameState.playerCanRequestTrumpReveal) {
            return false
        }

        val cantFollowSuit = gameState.leadSuit?.let { lead -> player.hand.none { it.suit == lead } } ?: false
        if (!cantFollowSuit) return false

        return when (difficulty) {
            Difficulty.EASY -> random.nextBoolean()
            Difficulty.MEDIUM -> {
                player.hand.groupBy { it.suit }.any { (_, cards) ->
                    cards.size >= 3 && cards.any { it.rank.numericValue >= Rank.JACK.numericValue }
                }
            }
            Difficulty.HARD -> {
                val unplayedTensCount = countUnplayedTens(gameState.playedCardsThisDeal)
                val strongSuitPotential = player.hand.groupBy { it.suit }.any { (_, cards) ->
                    cards.size >= 4 && cards.any { it.rank.numericValue >= Rank.KING.numericValue }
                }
                val behindInTens = (gameState.teamTensCount[gameState.opponentTeam] ?: 0) > (gameState.teamTensCount[gameState.playerTeam] ?: 0)
                (behindInTens && unplayedTensCount > 0) || strongSuitPotential || (unplayedTensCount <= 1 && random.nextDouble() < 0.7)
            }
        }
    }

    private fun mustPlayTrump(player: Player, gameState: AiGameState): Boolean {
        if (gameState.playerMustPlayTrumpIfAble && gameState.revealedTrumpSuit != null) {
            if (gameState.leadSuit != null && player.hand.any { it.suit == gameState.leadSuit }) {
                return false
            }
            return player.hand.any { it.suit == gameState.revealedTrumpSuit }
        }
        return false
    }

    private fun playTrumpCard(player: Player, gameState: AiGameState, isObligated: Boolean): AiDecision {
        val revealedTrump = gameState.revealedTrumpSuit ?: return decideDiscardCard(player, gameState, true)
        val trumpsInHand = player.hand.filter { it.suit == revealedTrump }

        if (trumpsInHand.isEmpty()) {
            return decideDiscardCard(player, gameState, true)
        }

        val (currentWinningPlayerInTrick, highestCardInTrick) = if (gameState.currentTrick.isNotEmpty()) {
            findCurrentTrickWinner(gameState.currentTrick, gameState.leadSuit, gameState.revealedTrumpSuit)
        } else {
            Pair(null, Card(Suit.SPADES, Rank.TWO, "dummy_low_card_for_lead_trump"))
        }

        val isPartnerWinningTrick = currentWinningPlayerInTrick != null && gameState.playerTeam.players.contains(currentWinningPlayerInTrick)
        val highestTrumpInTrickSoFar = if(gameState.currentTrick.isNotEmpty() && highestCardInTrick.suit == revealedTrump) highestCardInTrick else null

        if (isPartnerWinningTrick && highestCardInTrick.suit == revealedTrump && !isObligated && difficulty != Difficulty.HARD) {
            val canOpponentOvertrumpPartner = anyOpponentCanOvertrump(gameState, highestCardInTrick)
            if (!canOpponentOvertrumpPartner) {
                return AiDecision.PlayCard(trumpsInHand.minByOrNull { it.rank.numericValue }!!)
            }
        }

        val winningTrumps = if (highestTrumpInTrickSoFar != null) {
            trumpsInHand.filter { it.rank.numericValue > highestTrumpInTrickSoFar.rank.numericValue }
        } else if (gameState.currentTrick.isEmpty() || highestCardInTrick.suit != revealedTrump) {
            trumpsInHand
        } else {
            trumpsInHand
        }


        if (winningTrumps.isNotEmpty()) {
            val cardToPlay = if (difficulty == Difficulty.HARD && winningTrumps.size > 1 && gameState.remainingTricks > 3 &&
                (highestTrumpInTrickSoFar == null || highestTrumpInTrickSoFar.rank.numericValue < Rank.JACK.numericValue) &&
                !gameState.currentTrick.any{ it.second.rank.isTen && !gameState.playerTeam.players.contains(it.first)}) {
                winningTrumps.sortedBy { it.rank.numericValue }.let { sortedWinners ->
                    if (sortedWinners.size > 1 && sortedWinners.first().rank.numericValue < Rank.NINE.numericValue) sortedWinners[1] else sortedWinners.first()
                }
            } else {
                winningTrumps.minByOrNull { it.rank.numericValue }!!
            }
            return AiDecision.PlayCard(cardToPlay)
        }

        return AiDecision.PlayCard(trumpsInHand.minByOrNull { it.rank.numericValue }!!)
    }


    private fun decideCardToPlay(player: Player, gameState: AiGameState): AiDecision {
        val leadSuit = gameState.leadSuit
        val cardsOfLeadSuit = if (leadSuit != null) player.hand.filter { it.suit == leadSuit } else emptyList()

        if (leadSuit == null) {
            return decideLeadCard(player, gameState)
        } else if (cardsOfLeadSuit.isNotEmpty()) {
            return decideFollowCard(player, gameState, cardsOfLeadSuit)
        } else {
            if (gameState.revealedTrumpSuit != null && player.hand.any { it.suit == gameState.revealedTrumpSuit }) {
                val (trickWinningPlayer, trickWinningCard) = if (gameState.currentTrick.isNotEmpty()) {
                    findCurrentTrickWinner(gameState.currentTrick, gameState.leadSuit, gameState.revealedTrumpSuit)
                } else { Pair(null, Card(Suit.SPADES,Rank.TWO,"dummy"))}

                val isPartnerWinning = trickWinningPlayer != null && gameState.playerTeam.players.contains(trickWinningPlayer)

                var trumpIt = false
                if (!isPartnerWinning) trumpIt = true
                if (isPartnerWinning && trickWinningCard.rank.isTen && difficulty == Difficulty.HARD) trumpIt = true
                if (gameState.currentTrick.any { (p,c) -> !gameState.playerTeam.players.contains(p) && c.rank.isTen }) trumpIt = true

                if (trumpIt && canWinWithTrump(player, gameState, trickWinningCard)) {
                    return playTrumpCard(player, gameState, false)
                }
            }
            return decideDiscardCard(player, gameState, false)
        }
    }

    private fun decideLeadCard(player: Player, gameState: AiGameState): AiDecision {
        if (player.hand.isEmpty()) throw IllegalStateException("AI hand empty when leading. Player: ${player.name}")
        if (difficulty == Difficulty.EASY) return AiDecision.PlayCard(player.hand.random(random))

        for (card in player.hand.sortedByDescending { it.rank.numericValue }) {
            if (card.rank.isTen && isHighestRemainingInSuit(card, gameState.playedCardsThisDeal)) {
                val cardsInSuitCount = player.hand.count { it.suit == card.suit }
                if (card.suit == gameState.revealedTrumpSuit || cardsInSuitCount >= (if(difficulty == Difficulty.HARD) 3 else 4)) {
                    return AiDecision.PlayCard(card)
                }
            }
        }

        val potentialHighLeads = player.hand.filter {
            (it.rank == Rank.ACE || it.rank == Rank.KING) &&
                    isHighestRemainingInSuit(it, gameState.playedCardsThisDeal) &&
                    it.suit != gameState.revealedTrumpSuit
        }.sortedWith(compareByDescending<Card> { it.rank.numericValue }
            .thenBy { player.hand.count { c -> c.suit == it.suit } })

        if (potentialHighLeads.isNotEmpty()) {
            return AiDecision.PlayCard(potentialHighLeads.first())
        }

        if (gameState.revealedTrumpSuit != null && difficulty == Difficulty.HARD) {
            val trumpsInHand = player.hand.filter { it.suit == gameState.revealedTrumpSuit }
            val unplayedTrumpsCount = countUnplayedSuit(gameState.revealedTrumpSuit, gameState.playedCardsThisDeal)
            if (trumpsInHand.size >= 4 || (trumpsInHand.size >=3 && trumpsInHand.size.toDouble() / unplayedTrumpsCount.coerceAtLeast(1) > 0.4) ) {
                return AiDecision.PlayCard(trumpsInHand.maxByOrNull { it.rank.numericValue }!!)
            }
        }

        val bestNonTrumpLead = player.hand
            .filter { it.suit != gameState.revealedTrumpSuit }
            .groupBy { it.suit }
            .filter { it.value.isNotEmpty() }
            .mapNotNull { (suit, cardsInSuit) ->
                val cardToConsider = cardsInSuit
                    .filterNot { it.rank.isTen && cardsInSuit.size <= (if(difficulty == Difficulty.HARD) 2 else 1) && !isHighestRemainingInSuit(it, gameState.playedCardsThisDeal) }
                    .maxByOrNull { it.rank.numericValue }
                cardToConsider?.let { Pair(it, cardsInSuit.size) }
            }
            .maxByOrNull { it.second }
            ?.first

        if (bestNonTrumpLead != null) {
            return AiDecision.PlayCard(bestNonTrumpLead)
        }

        return AiDecision.PlayCard(player.hand.maxByOrNull { it.rank.numericValue } ?: player.hand.first())
    }

    private fun decideFollowCard(player: Player, gameState: AiGameState, cardsOfLeadSuit: List<Card>): AiDecision {
        val (currentWinningPlayer, currentWinningCard) = if(gameState.currentTrick.isNotEmpty()) {
            findCurrentTrickWinner(gameState.currentTrick, gameState.leadSuit!!, gameState.revealedTrumpSuit)
        } else { throw IllegalStateException("decideFollowCard called with empty currentTrick") }

        val isPartnerWinning = currentWinningPlayer != null && gameState.playerTeam.players.contains(currentWinningPlayer)
        val winningCardsInHand = cardsOfLeadSuit.filter { it.rank.numericValue > currentWinningCard.rank.numericValue }

        if (winningCardsInHand.isNotEmpty()) {
            if (isPartnerWinning && !currentWinningCard.rank.isTen && difficulty != Difficulty.HARD) {
                return AiDecision.PlayCard(cardsOfLeadSuit.minByOrNull { it.rank.numericValue }!!)
            }
            val tenToWinWith = winningCardsInHand.find { it.rank.isTen }
            if (tenToWinWith != null) {
                return AiDecision.PlayCard(tenToWinWith)
            }
            return AiDecision.PlayCard(winningCardsInHand.minByOrNull { it.rank.numericValue }!!)
        }
        return AiDecision.PlayCard(cardsOfLeadSuit.minByOrNull { it.rank.numericValue }!!)
    }

    private fun decideDiscardCard(player: Player, gameState: AiGameState, attemptedTrumpButFailed: Boolean): AiDecision {
        var discardOptions = player.hand.toList()
        if (discardOptions.isEmpty()) throw IllegalStateException("AI hand empty when discarding. Player: ${player.name}")

        val nonTens = discardOptions.filter { !it.rank.isTen }
        if (nonTens.isNotEmpty()) {
            discardOptions = nonTens
        }

        if (gameState.revealedTrumpSuit != null) {
            val nonTrumpsFromOptions = discardOptions.filter { it.suit != gameState.revealedTrumpSuit }
            if (nonTrumpsFromOptions.isNotEmpty()) {
                discardOptions = nonTrumpsFromOptions
            }
        }
        if (discardOptions.isEmpty()) {
            discardOptions = player.hand.toList()
            val nonTensFromFullHand = discardOptions.filter { !it.rank.isTen }
            if (nonTensFromFullHand.isNotEmpty()) discardOptions = nonTensFromFullHand
        }
        if (discardOptions.isEmpty()) discardOptions = player.hand.toList()


        if (difficulty == Difficulty.EASY || discardOptions.size == 1) {
            return AiDecision.PlayCard(discardOptions.random(random))
        }

        val cardToDiscard = discardOptions
            .minWithOrNull(
                compareBy<Card> { card -> player.hand.count { it.suit == card.suit } }
                    .thenBy { it.rank.numericValue }
            )
        return AiDecision.PlayCard(cardToDiscard ?: discardOptions.random(random))
    }

    private fun isHighestRemainingInSuit(card: Card, playedCards: Set<Card>): Boolean {
        for (rankValue in (card.rank.numericValue + 1)..Rank.ACE.numericValue) {
            val higherRank = Rank.values().firstOrNull { it.numericValue == rankValue }
            if (higherRank != null && !playedCards.any { it.suit == card.suit && it.rank == higherRank }) {
                return false
            }
        }
        return true
    }

    private fun isCardGenerallyHighestRemaining(card: Card, playedCards: Set<Card>, trumpSuit: Suit?): Boolean {
        if (card.suit == trumpSuit) {
            return isHighestRemainingInSuit(card, playedCards.filter { it.suit == trumpSuit }.toSet())
        } else {
            if (!isHighestRemainingInSuit(card, playedCards)) return false
            return true
        }
    }


    private fun isCardEffectivelyPlayed(cardToCheck: Card, playedCardsThisDeal: Set<Card>): Boolean {
        return playedCardsThisDeal.any { it.suit == cardToCheck.suit && it.rank == cardToCheck.rank }
    }

    private fun countUnplayedTens(playedCards: Set<Card>): Int {
        var count = 0
        Suit.values().forEach { suit ->
            if (!isCardEffectivelyPlayed(Card(suit, Rank.TEN, "dummyId"), playedCards)) {
                count++
            }
        }
        return count
    }

    private fun countSuitInHand(player: Player, suit: Suit): Int {
        return player.hand.count { it.suit == suit }
    }

    private fun countUnplayedSuit(suit: Suit, playedCards: Set<Card>): Int {
        val totalInSuit = Rank.values().size
        val playedInSuit = playedCards.count { it.suit == suit }
        return totalInSuit - playedInSuit
    }


    private fun findCurrentTrickWinner(
        trick: List<Pair<Player, Card>>,
        leadSuitInTrick: Suit?,
        revealedTrumpSuit: Suit?
    ): Pair<Player, Card> {
        if (trick.isEmpty()) throw IllegalStateException("Cannot determine winner of an empty trick.")

        var winningPair = trick.first()
        val actualLeadSuit = leadSuitInTrick ?: winningPair.second.suit

        for (i in 1 until trick.size) {
            val currentPair = trick[i]
            val currentCard = currentPair.second
            val currentWinningCard = winningPair.second

            val currentCardIsTrump = revealedTrumpSuit != null && currentCard.suit == revealedTrumpSuit
            val winningCardIsTrump = revealedTrumpSuit != null && currentWinningCard.suit == revealedTrumpSuit

            if (currentCardIsTrump) {
                if (winningCardIsTrump) {
                    if (currentCard.rank.numericValue > currentWinningCard.rank.numericValue) winningPair = currentPair
                } else {
                    winningPair = currentPair
                }
            } else {
                if (!winningCardIsTrump) {
                    if (currentCard.suit == actualLeadSuit && currentWinningCard.suit != actualLeadSuit) {
                        winningPair = currentPair
                    } else if (currentCard.suit == actualLeadSuit && currentCard.rank.numericValue > currentWinningCard.rank.numericValue) {
                        winningPair = currentPair
                    }
                }
            }
        }
        return winningPair
    }

    private fun canWinWithTrump(player: Player, gameState: AiGameState, currentWinningCardInTrick: Card): Boolean {
        val revealedTrump = gameState.revealedTrumpSuit ?: return false
        val trumpsInHand = player.hand.filter { it.suit == revealedTrump }
        if (trumpsInHand.isEmpty()) return false

        if (gameState.currentTrick.isEmpty()) return true

        if (currentWinningCardInTrick.suit == revealedTrump) {
            return trumpsInHand.any { it.rank.numericValue > currentWinningCardInTrick.rank.numericValue }
        }
        return true
    }

    private fun anyOpponentCanOvertrump(gameState: AiGameState, partnerTrumpCard: Card): Boolean {
        val trump = gameState.revealedTrumpSuit ?: return false
        for (rankValue in (partnerTrumpCard.rank.numericValue + 1)..Rank.ACE.numericValue) {
            val higherRank = Rank.values().firstOrNull { it.numericValue == rankValue } ?: continue
            if (!isCardEffectivelyPlayed(Card(trump, higherRank, "dummyId"), gameState.playedCardsThisDeal)) {
                return true
            }
        }
        return false
    }
}
