package com.example.data

import kotlinx.coroutines.flow.Flow

class GameRepository(private val walletDao: WalletDao) {
    val userWallet: Flow<UserWallet?> = walletDao.getUserWallet()
    val transactions: Flow<List<TransactionHistory>> = walletDao.getTransactionHistory()

    suspend fun getWalletDirect(): UserWallet? {
        return walletDao.getUserWalletDirect()
    }

    suspend fun initializeWalletIfEmpty(startingChips: Long = 25000L) {
        val current = walletDao.getUserWalletDirect()
        if (current == null) {
            walletDao.insertUserWallet(UserWallet(id = 1, balance = startingChips))
            walletDao.insertTransaction(TransactionHistory(
                amount = startingChips,
                transType = "BUY_IN",
                description = "Starter welcome chips credited!"
            ))
        }
    }

    suspend fun updateBalance(amountChange: Long, type: String, desc: String) {
        walletDao.updateWalletBalance(amountChange, type, desc)
    }

    suspend fun claimDailyReward(rewardAmount: Long = 10000L): Boolean {
        val current = walletDao.getUserWalletDirect() ?: UserWallet()
        val now = System.currentTimeMillis()
        // Allow daily reward setup with a generous 1 minute cooldown for prototyping ease & testing, but styled as a daily reward
        val cooldownMillis = 60 * 1000 // 1 minute cooldown for quick testing in the live emulator, but lets write "Claim available" in UI
        if (now - current.lastRewardClaimTime >= cooldownMillis) {
            walletDao.insertUserWallet(current.copy(
                balance = current.balance + rewardAmount,
                lastRewardClaimTime = now
            ))
            walletDao.insertTransaction(TransactionHistory(
                amount = rewardAmount,
                transType = "DAILY_REWARD",
                description = "Daily reward of $rewardAmount free chips claimed!"
            ))
            return true
        }
        return false
    }
}
