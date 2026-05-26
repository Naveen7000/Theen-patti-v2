package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_wallet")
data class UserWallet(
    @PrimaryKey val id: Int = 1,
    val balance: Long = 25000L, // Generous starting balance
    val gamesPlayed: Int = 0,
    val gamesWon: Int = 0,
    val biggestWin: Long = 0L,
    val level: Int = 1,
    val xp: Int = 0,
    val lastRewardClaimTime: Long = 0L
)

@Entity(tableName = "transaction_history")
data class TransactionHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Long, // e.g. +5000 or -1000
    val transType: String, // "BET", "WIN", "LOOSE", "DAILY_REWARD", "BUY_IN"
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)
