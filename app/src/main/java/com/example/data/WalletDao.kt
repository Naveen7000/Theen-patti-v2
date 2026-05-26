package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Query("SELECT * FROM user_wallet WHERE id = 1")
    fun getUserWallet(): Flow<UserWallet?>

    @Query("SELECT * FROM user_wallet WHERE id = 1")
    suspend fun getUserWalletDirect(): UserWallet?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserWallet(wallet: UserWallet)

    @Query("SELECT * FROM transaction_history ORDER BY timestamp DESC LIMIT 50")
    fun getTransactionHistory(): Flow<List<TransactionHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionHistory)

    @Transaction
    suspend fun updateWalletBalance(amountChange: Long, type: String, desc: String) {
        val currentWallet = getUserWalletDirect() ?: UserWallet()
        val newBalance = (currentWallet.balance + amountChange).coerceAtLeast(0L)
        
        val updatedWon = if (amountChange > 0 && type == "WIN") currentWallet.gamesWon + 1 else currentWallet.gamesWon
        val updatedPlayed = if (type == "WIN" || type == "LOOSE") currentWallet.gamesPlayed + 1 else currentWallet.gamesPlayed
        val biggestWin = if (amountChange > currentWallet.biggestWin) amountChange else currentWallet.biggestWin
        
        // Add level progression xp
        val addedXp = if (amountChange > 0) (amountChange / 100).toInt().coerceAtMost(100) else 10
        val tempXp = currentWallet.xp + addedXp
        val shouldLevelUp = tempXp >= currentWallet.level * 500
        val finalLevel = if (shouldLevelUp) currentWallet.level + 1 else currentWallet.level
        val finalXp = if (shouldLevelUp) tempXp - (currentWallet.level * 500) else tempXp

        insertUserWallet(currentWallet.copy(
            balance = newBalance,
            gamesPlayed = updatedPlayed,
            gamesWon = updatedWon,
            biggestWin = biggestWin,
            level = finalLevel,
            xp = finalXp
        ))

        insertTransaction(TransactionHistory(
            amount = amountChange,
            transType = type,
            description = desc
        ))
    }
}
