package net.izelon.pennydrop.game

import net.izelon.pennydrop.types.Slot
import net.izelon.pennydrop.types.fullSlots

data class AI(
    val name: String,
    val rollAgain: (slots: List<Slot>) -> Boolean
) {
    override fun toString() = name


    companion object {
        @JvmStatic
        val basicAI = listOf(
            AI("TwoFace") { slots -> slots.fullSlots() < 3 || (slots.fullSlots() == 3 && coinFlipHeads()) },
            AI("No Go Noah") { slots -> slots.fullSlots() == 0 },
            AI("Bail out Beulah") { slots -> slots.fullSlots() <= 1 },
            AI("Fearful Fred") { slots -> slots.fullSlots() <= 2 },
            AI("Even Steven") { slots -> slots.fullSlots() <= 3 },
            AI("Riverboat Ron") { slots -> slots.fullSlots() <= 4 },
            AI("Sammy Sixties") { slots -> slots.fullSlots() <= 5 },
            AI("Random Rachael") { coinFlipHeads() }
        )
    }
}

fun coinFlipHeads() = (Math.random() * 2).toInt() == 0