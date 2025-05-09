package com.example.mindicotgame.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf // For Int properties, part of androidx.compose.runtime



class Team(
    val id: String,
    val name: String,
    val players: List<Player>
) {

    var tricksWonThisDeal: MutableState<Int> = mutableIntStateOf(0)
    var tensCapturedThisDeal: MutableState<Int> = mutableIntStateOf(0)
    var overallScore: MutableState<Int> = mutableIntStateOf(0) // Represents deals won or a cumulative score


    fun resetDealStats() {
        tricksWonThisDeal.value = 0
        tensCapturedThisDeal.value = 0
    }
}
