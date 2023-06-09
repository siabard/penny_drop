package net.izelon.pennydrop.game

import net.izelon.pennydrop.types.Player

data class TurnResult(
    val lastRoll: Int? = null,
    val coinChangeCount: Int? = null,
    val previousPlayer: Player? = null,
    val currentPlayer: Player? = null,
    val playerChanged: Boolean = false,
    val turnEnd: TurnEnd? = null,
    val canRoll: Boolean = false,
    val canPass: Boolean = false,
    val clearSlots: Boolean = false,
    val isGameOver: Boolean = false,
)

enum class TurnEnd {
    Pass, Bust, Win
}