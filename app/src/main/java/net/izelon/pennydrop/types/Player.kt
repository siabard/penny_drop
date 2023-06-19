package net.izelon.pennydrop.types

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import net.izelon.pennydrop.game.AI

@Entity(tableName = "players")
data class Player(
    @PrimaryKey(autoGenerate = true) var playerId: Long = 0,
    val playerName: String = "",
    val isHuman: Boolean = true,
    val selectedAI: AI? = null,
) {

    @Ignore
    var pennies: Int = defaultPennyCount

    fun addPennies(count: Int = 1) {
        pennies += count
    }

    @Ignore
    var isRolling: Boolean = false

    fun penniesLeft(subtractPenny: Boolean = false) = (pennies - (if (subtractPenny) 1 else 0)) > 0

    @Ignore
    var gamePlayerNumber: Int = -1

    companion object {
        const val defaultPennyCount = 10
    }
}