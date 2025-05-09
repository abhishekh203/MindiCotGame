package com.example.mindicotgame.model

data class Player(val id: String, val name: String) {
    val hand = mutableListOf<Card>()

    fun addCards(cardsToAdd: List<Card>) {
        hand.addAll(cardsToAdd)
    }

    fun removeCard(cardToRemove: Card) {
        hand.find { it.instanceId == cardToRemove.instanceId }?.let { hand.remove(it) }
    }

    fun clearHand() {
        hand.clear()
    }

    fun sortHand() {
        hand.sortWith(compareBy<Card> { it.suit.ordinal }.thenByDescending { it.rank.numericValue })
    }

    override fun toString(): String = name
}