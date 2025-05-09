package com.example.mindicotgame.model

data class Card(
    val suit: Suit,
    val rank: Rank,
    val instanceId: String = "${System.currentTimeMillis()}-${(0..1000).random()}" // Unique ID for each card instance
) {
    override fun toString(): String = "${rank.symbol}${suit.symbol}"
}

val Suit.symbol: String
    get() = when (this) {
        Suit.HEARTS -> "♥"
        Suit.DIAMONDS -> "♦"
        Suit.CLUBS -> "♣"
        Suit.SPADES -> "♠"
    }

val Rank.symbol: String
    get() = when (this) {
        Rank.ACE -> "A"
        Rank.KING -> "K"
        Rank.QUEEN -> "Q"
        Rank.JACK -> "J"
        Rank.TEN -> "10"
        Rank.NINE -> "9"
        Rank.EIGHT -> "8"
        Rank.SEVEN -> "7"
        Rank.SIX -> "6"
        Rank.FIVE -> "5"
        Rank.FOUR -> "4"
        Rank.THREE -> "3"
        Rank.TWO -> "2"
    }