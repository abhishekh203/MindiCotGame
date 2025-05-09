package com.example.mindicotgame.model

import kotlin.random.Random

class Deck {
    private val cards = mutableListOf<Card>()

    init {
        reset()
    }

    fun reset() {
        cards.clear()
        Suit.values().forEach { suit ->
            Rank.values().forEach { rank ->
                cards.add(Card(suit, rank))
            }
        }
        shuffle()
    }

    fun shuffle() {
        cards.shuffle(Random)
    }

    fun dealCard(): Card = cards.removeAt(0)

    fun dealMultipleCards(count: Int): List<Card> {
        val dealtCards = mutableListOf<Card>()
        repeat(count) {
            if (cards.isNotEmpty()) {
                dealtCards.add(dealCard())
            }
        }
        return dealtCards
    }

    fun remainingCards(): Int = cards.size
}