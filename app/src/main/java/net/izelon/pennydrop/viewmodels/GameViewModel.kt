package net.izelon.pennydrop.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.izelon.pennydrop.data.GameState
import net.izelon.pennydrop.data.GameStatus
import net.izelon.pennydrop.data.GameWithPlayers
import net.izelon.pennydrop.data.PennyDropDatabase
import net.izelon.pennydrop.data.PennyDropRepository
import net.izelon.pennydrop.game.GameHandler
import net.izelon.pennydrop.game.TurnEnd
import net.izelon.pennydrop.game.TurnResult
import net.izelon.pennydrop.types.Player
import net.izelon.pennydrop.types.Slot
import net.izelon.pennydrop.types.clear
import java.time.OffsetDateTime

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private var clearText: Boolean = false

    private val repository: PennyDropRepository

    val currentGame = MediatorLiveData<GameWithPlayers>()
    val currentGameStatuses: LiveData<List<GameStatus>>
    val currentPlayer: LiveData<Player>
    val currentStandingsText: LiveData<String>

    val slots: LiveData<List<Slot>>

    val canRoll: LiveData<Boolean>
    val canPass: LiveData<Boolean>

    init {
        this.repository =
            PennyDropDatabase.getDatabase(application, viewModelScope).pennyDropDao().let { dao ->
                PennyDropRepository.getInstance(dao)
            }

        this.currentGameStatuses = this.repository.getCurrentGameStatuses()

        this.currentGame.addSource(this.repository.getCurrentGameWithPlayers()) { gameWithPlayers ->
            updateCurrentGame(gameWithPlayers, this.currentGameStatuses.value)
        }

        this.currentGame.addSource(this.currentGameStatuses) { gameStatuses ->
            updateCurrentGame(this.currentGame.value, gameStatuses)
        }

        this.currentPlayer = Transformations.map(this.currentGame) { gameWithPlayers ->
            gameWithPlayers?.players?.firstOrNull { it.isRolling }
        }

        this.currentStandingsText = Transformations.map(this.currentGame) { gameWithPlayers ->
            gameWithPlayers?.players?.let { players ->
                this.generateCurrentStandings(players)
            }
        }

        this.slots = this.currentGame.map { gameWithPlayers ->
            Slot.mapFromGame(gameWithPlayers.game)
        }

        this.canRoll = Transformations.map(this.currentPlayer) { player ->
            player?.isHuman == true && currentGame.value?.game?.canRoll == true
        }

        this.canPass = Transformations.map(this.currentPlayer) { player ->
            player?.isHuman == true && currentGame.value?.game?.canPass == true
        }


    }

    private var players: List<Player> = emptyList()

    val currentTurnText = MutableLiveData<String>("")

    suspend fun startGame(playersForNewGame: List<Player>) {
        repository.startGame(playersForNewGame)
    }

    fun roll() {
        val game = this.currentGame.value?.game
        val players = this.currentGame.value?.players
        val currentPlayer = this.currentPlayer.value
        val slots = this.slots.value

        if (game != null && players != null && currentPlayer != null && slots != null && game.canRoll) {
            updateFromGameHandler(GameHandler.roll(players, currentPlayer, slots))
        }
    }

    fun pass() {

        val game = this.currentGame.value?.game
        val players = this.currentGame.value?.players
        val currentPlayer = this.currentPlayer.value

        if (game != null && players != null && currentPlayer != null && game.canPass) {
            updateFromGameHandler(
                GameHandler.pass(players, currentPlayer)
            )
        }
    }

    private fun <T> MutableLiveData<List<T>>.notifyChange() {
        this.value = this.value
    }

    private fun updateFromGameHandler(result: TurnResult) {
        val game = currentGame.value?.let { currentGameWithPlayers ->
            currentGameWithPlayers.game.copy(
                gameState = if (result.isGameOver) GameState.Finished else GameState.Started,
                lastRoll = result.lastRoll,
                filledSlots = updateFilledSlots(result, currentGameWithPlayers.game.filledSlots),
                currentTurnText = generateTurnText(result),
                canPass = result.canPass,
                canRoll = result.canRoll,
                endTime = if (result.isGameOver) OffsetDateTime.now() else null
            )

        } ?: return

        val statuses = currentGameStatuses.value?.map { status ->
            when (status.playerId) {
                result.previousPlayer?.playerId -> {
                    status.copy(
                        isRolling = false,
                        pennies = status.pennies + (result.coinChangeCount ?: 0)
                    )
                }

                result.currentPlayer?.playerId -> {
                    status.copy(
                        isRolling = !result.isGameOver,
                        pennies = status.pennies + if (!result.playerChanged) {
                            result.coinChangeCount ?: 0
                        } else 0
                    )
                }

                else -> status
            }
        } ?: emptyList()

        viewModelScope.launch {
            repository.updateGameAndStatuses(game, statuses)
            if (result.currentPlayer?.isHuman == false) {
                playAITurn()
            }
        }

    }

    private fun updateFilledSlots(result: TurnResult, filledSlots: List<Int>) = when {
        result.clearSlots -> emptyList()
        result.lastRoll != null && result.lastRoll != 6 -> filledSlots + result.lastRoll
        else -> filledSlots
    }


    private fun generateCurrentStandings(
        players: List<Player>,
        headerText: String = "Current Standings:"
    ) =
        players.sortedBy { it.pennies }.joinToString(
            separator = "\n",
            prefix = "$headerText\n"
        ) {
            "\t${it.playerName} - ${it.pennies} pennies"
        }

    private fun generateGameOverText(): String {
        val statuses = this.currentGameStatuses.value
        val players = this.currentGame.value?.players?.map { player ->
            player.apply {
                this.pennies = statuses?.firstOrNull { it.playerId == playerId }?.pennies
                    ?: Player.defaultPennyCount
            }
        }

        val winningPlayer = players
            ?.firstOrNull { !it.penniesLeft() || it.isRolling }
            ?.apply { this.pennies = 0 }

        if (players == null || winningPlayer == null) return "N/A"

        return """Game Over!
                  |${winningPlayer.playerName} is the winner!
                  |
                  |${generateCurrentStandings(players, "Final Scores:")}
                  """.trimMargin()
    }


    private fun generateTurnText(result: TurnResult): String {
        val currentText = if (clearText) "" else currentGame.value?.game?.currentTurnText ?: ""
        clearText = result.turnEnd != null

        val currentPlayerName = result.currentPlayer?.playerName ?: "???"

        return when {
            result.isGameOver -> generateGameOverText()
            result.turnEnd == TurnEnd.Bust -> "${
                ohNoPhrases.shuffled().first()
            } ${result.previousPlayer?.playerName} rolled a ${result.lastRoll}.  They collected ${result.coinChangeCount} pennies for a total of ${result.previousPlayer?.pennies}.\n$currentText"

            result.turnEnd == TurnEnd.Pass -> "${result.previousPlayer?.playerName} passed.  They currently have ${result.previousPlayer?.pennies} pennies.\n$currentText"
            result.lastRoll != null -> "$currentPlayerName rolled a ${result.lastRoll}.\n$currentText"
            else -> ""
        }
    }


    private fun playAITurn() {
        viewModelScope.launch {
            delay(1000)
            val game = currentGame.value?.game
            val players = currentGame.value?.players
            val currentPlayer = currentPlayer.value
            val slots = slots.value

            if (game != null && players != null && currentPlayer != null && slots != null) {
                GameHandler.playAITurn(players, currentPlayer, slots, game.canPass)?.let { result ->
                    updateFromGameHandler(result)
                }
            }


        }
    }

    private val ohNoPhrases = listOf(
        "Oh no!",
        "Bummer",
        "Dang.",
        "Whoops",
        "Ah, fiddlesticks.",

        )

    private fun updateCurrentGame(
        gameWithPlayers: GameWithPlayers?,
        gameStatuses: List<GameStatus>?
    ) {
        this.currentGame.value = gameWithPlayers?.updateStatuses(gameStatuses)
    }


}
