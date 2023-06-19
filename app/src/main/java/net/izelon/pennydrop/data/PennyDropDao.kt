package net.izelon.pennydrop.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import net.izelon.pennydrop.types.Player
import java.time.OffsetDateTime

@Dao
abstract class PennyDropDao {
    @Query("SELECT * FROM players WHERE playerName = :playerName")
    abstract fun getPlayer(playerName: String): Player?

    @Transaction
    @Query("SELECT * FROM games ORDER By startTime DESC LIMIT 1")
    abstract fun getCurrentGameWithPlayers(): LiveData<GameWithPlayers>

    @Transaction
    @Query(
        """
        SELECT * FROM game_statuses
        WHERE gameId = (
            SELECT gameId from games
            WHERE endTime IS NULL
            ORDER BY startTime DESC
            LIMIT 1
        )
    """
    )
    abstract fun getCurrentGameStatuses(): LiveData<List<GameStatus>>

    @Insert
    abstract suspend fun insertGame(game: Game): Long

    @Insert
    abstract suspend fun insertPlayer(player: Player): Long

    @Insert
    abstract suspend fun insertPlayers(players: List<Player>): List<Long>

    @Update
    abstract suspend fun updategame(game: Game)

    @Query(
        """
        UPDATE games 
        SET endTime = :endDate, gameState = :gameState
        WHERE endTime IS NULL
    """
    )
    abstract suspend fun closeOpenGames(
        endDate: OffsetDateTime = OffsetDateTime.now(),
        gameState: GameState = GameState.Cancelled
    )

    @Insert
    abstract suspend fun insertGameStatuses(gameStatuses: List<GameStatus>)

    @Transaction
    open suspend fun startGame(players: List<Player>): Long {
        this.closeOpenGames()

        val gameId = this.insertGame(
            Game(

                gameState = GameState.Started,
                currentTurnText = "The game has begun!\n",
                canRoll = true
            )
        )

        val playerIds = players.map { player: Player ->
            getPlayer(player.playerName)?.playerId ?: insertPlayer(player)
        }

        this.insertGameStatuses(
            playerIds.mapIndexed { index, playerId ->
                GameStatus(
                    gameId,
                    playerId,
                    index,
                    index == 0
                )

            }
        )

        return gameId
    }

    @Update
    abstract suspend fun updateGameStatuses(gameStatuses: List<GameStatus>)

    @Transaction
    open suspend fun updateGameAndStatuses(
        game: Game,
        statuses: List<GameStatus>
    ) {
        this.updategame(game)
        this.updateGameStatuses(statuses)
    }
}