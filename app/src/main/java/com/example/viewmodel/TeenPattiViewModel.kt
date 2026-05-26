package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GeminiService
import com.example.data.TransactionHistory
import com.example.data.UserWallet
import com.example.model.Card
import com.example.model.DealerRanker
import com.example.model.HandEvaluation
import com.example.model.HandType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class GamePhase {
    IDLE,
    BETTING,
    SHOWDOWN
}

data class PlayerState(
    val id: Int,
    val name: String,
    val avatarEmoji: String,
    val balance: Long,
    val cards: List<Card>,
    val isSeen: Boolean,
    val isPacked: Boolean,
    val isThinking: Boolean,
    val lastAction: String,
    val handEvaluation: HandEvaluation? = null
)

data class DealerChatEntry(
    val sender: String, // "Dealer" or "You"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

class TeenPattiViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: GameRepository
    
    val walletState: StateFlow<UserWallet?>
    val transactionsState: StateFlow<List<TransactionHistory>>

    // Game Table States
    private val _phase = MutableStateFlow(GamePhase.IDLE)
    val phase: StateFlow<GamePhase> = _phase.asStateFlow()

    private val _players = MutableStateFlow<List<PlayerState>>(emptyList())
    val players: StateFlow<List<PlayerState>> = _players.asStateFlow()

    private val _potAmount = MutableStateFlow(0L)
    val potAmount: StateFlow<Long> = _potAmount.asStateFlow()

    private val _currentChaal = MutableStateFlow(200L) // Minimum standard bet
    val currentChaal: StateFlow<Long> = _currentChaal.asStateFlow()

    private val _activeTurnIndex = MutableStateFlow(-1)
    val activeTurnIndex: StateFlow<Int> = _activeTurnIndex.asStateFlow()

    private val _eventLogs = MutableStateFlow<List<String>>(emptyList())
    val eventLogs: StateFlow<List<String>> = _eventLogs.asStateFlow()

    // AI Dealer / Advisor States
    private val _aiAdvisorMessage = MutableStateFlow("")
    val aiAdvisorMessage: StateFlow<String> = _aiAdvisorMessage.asStateFlow()

    private val _isAiAdvisorLoading = MutableStateFlow(false)
    val isAiAdvisorLoading: StateFlow<Boolean> = _isAiAdvisorLoading.asStateFlow()

    private val _dealerChatLogs = MutableStateFlow<List<DealerChatEntry>>(listOf(
        DealerChatEntry("Dealer", "Welcome to the Royal Table. Claim your daily free chips to get started!")
    ))
    val dealerChatLogs: StateFlow<List<DealerChatEntry>> = _dealerChatLogs.asStateFlow()

    private val _winnerMessage = MutableStateFlow<String?>(null)
    val winnerMessage: StateFlow<String?> = _winnerMessage.asStateFlow()

    private val _showdownDetails = MutableStateFlow<List<String>>(emptyList())
    val showdownDetails: StateFlow<List<String>> = _showdownDetails.asStateFlow()

    private val bootAmount = 200L

    init {
        val db = AppDatabase.getDatabase(application)
        repository = GameRepository(db.walletDao())
        
        walletState = repository.userWallet.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

        transactionsState = repository.transactions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        viewModelScope.launch {
            repository.initializeWalletIfEmpty()
            setupDefaultPlayers()
        }
    }

    private fun setupDefaultPlayers() {
        val initialPlayers = listOf(
            PlayerState(0, "You", "👑", 25000L, emptyList(), isSeen = false, isPacked = false, isThinking = false, lastAction = ""),
            PlayerState(1, "Rajesh", "😎", 18500L, emptyList(), isSeen = false, isPacked = false, isThinking = false, lastAction = ""),
            PlayerState(2, "Priya", "🔥", 32000L, emptyList(), isSeen = false, isPacked = false, isThinking = false, lastAction = ""),
            PlayerState(3, "Ananya", "✨", 22000L, emptyList(), isSeen = false, isPacked = false, isThinking = false, lastAction = "")
        )
        _players.value = initialPlayers
    }

    fun startNewGame() {
        _winnerMessage.value = null
        _showdownDetails.value = emptyList()
        _aiAdvisorMessage.value = ""
        _phase.value = GamePhase.BETTING
        _currentChaal.value = 200L
        _eventLogs.value = listOf("Dealer: Collect Boot of 200 chips from all players.")

        viewModelScope.launch {
            val wallet = repository.getWalletDirect() ?: UserWallet()
            if (wallet.balance < bootAmount) {
                // Not enough chips! Gift some
                repository.updateBalance(10000L, "BUY_IN", "Automated emergency bankroll injection!")
                _eventLogs.value = _eventLogs.value + "Dealer: Credited 10,000 emergency chips to your wallet!"
            }

            // Deduct boot from player in Room Database
            repository.updateBalance(-bootAmount, "BET", "Boot Collected for table state")

            // Re-fetch player balance to synchronize UI
            val freshWallet = repository.getWalletDirect() ?: UserWallet()

            // Deal hands and deduct boot
            val deck = DealerRanker.makeDeck()
            val list = _players.value.map { player ->
                val sliceStart = player.id * 3
                val playerHand = deck.subList(sliceStart, sliceStart + 3)
                val newBal = if (player.id == 0) freshWallet.balance else (player.balance - bootAmount).coerceAtLeast(0L)
                player.copy(
                    balance = newBal,
                    cards = playerHand,
                    isSeen = false,
                    isPacked = false,
                    isThinking = false,
                    lastAction = "Posted Boot (200)",
                    handEvaluation = DealerRanker.evaluate(playerHand)
                )
            }

            _players.value = list
            _potAmount.value = bootAmount * list.size
            _activeTurnIndex.value = 0 // Player starts next turn
            
            triggerDealerReaction("Game starts! Deck is shuffled and Boot is collected.")
        }
    }

    private fun postEvent(event: String) {
        val current = _eventLogs.value.toMutableList()
        current.add(event)
        if (current.size > 25) {
            current.removeAt(0)
        }
        _eventLogs.value = current
    }

    // Bot Decisions State Engine (Simulated Real-Time)
    private fun triggerBotTurn(botIndex: Int) {
        viewModelScope.launch {
            // Update seat state to "Thinking..."
            var list = _players.value.toMutableList()
            list[botIndex] = list[botIndex].copy(isThinking = true, lastAction = "Thinking...")
            _players.value = list

            // Simulate live networking delay
            delay(1800)

            // Re-read fresh players to ensure we have valid remaining participants
            list = _players.value.toMutableList()
            val bot = list[botIndex]
            if (bot.isPacked || _phase.value != GamePhase.BETTING) return@launch

            val isUserSeen = list[0].isSeen
            val isBotSeen = bot.isSeen
            val eval = bot.handEvaluation ?: HandEvaluation(HandType.HIGH_CARD, 0)
            
            // Bot decides whether to SEE cards
            var newlySeen = bot.isSeen
            if (!bot.isSeen) {
                // If bot has a decent hand, they see cards 45% of the time, or 20% on high card
                val seeChance = if (eval.handType.power >= HandType.COLOR.power) 0.65 else 0.35
                if (Math.random() < seeChance) {
                    newlySeen = true
                    postEvent("${bot.name} looked at their cards (SEEN).")
                }
            }

            // Decide action based on Hand Strength
            val requiredBet = if (newlySeen) _currentChaal.value * 2 else _currentChaal.value
            
            val wantToPack = when (eval.handType) {
                HandType.HIGH_CARD -> if (newlySeen) Math.random() < 0.70 else Math.random() < 0.25
                HandType.PAIR -> if (newlySeen) Math.random() < 0.25 else Math.random() < 0.10
                else -> false // Never pack flush, sequences, trails unless short of balance
            }

            if (wantToPack || bot.balance < requiredBet) {
                // PACK
                list = _players.value.toMutableList()
                list[botIndex] = bot.copy(
                    isPacked = true,
                    isThinking = false,
                    isSeen = newlySeen,
                    lastAction = "Packed"
                )
                _players.value = list
                postEvent("${bot.name} packed.")
                nextTurn()
            } else {
                // Call / Chaal or Raise
                val shouldRaise = eval.handType.power >= HandType.SEQUENCE.power && Math.random() < 0.40
                val activeList = list.filter { !it.isPacked }
                
                // If only 2 players left, can decide to click "SHOW" instead!
                val showPromptChance = newlySeen && activeList.size == 2 && Math.random() < 0.35
                
                if (showPromptChance) {
                    // Bot triggers SHOW
                    executeShowdown(botIndex)
                } else {
                    val finalBet = if (shouldRaise) {
                        val raiseAmount = if (_currentChaal.value < 1600L) _currentChaal.value * 2 else _currentChaal.value
                        _currentChaal.value = raiseAmount
                        if (newlySeen) raiseAmount * 2 else raiseAmount
                    } else {
                        requiredBet
                    }

                    list = _players.value.toMutableList()
                    list[botIndex] = bot.copy(
                        balance = bot.balance - finalBet,
                        isThinking = false,
                        isSeen = newlySeen,
                        lastAction = if (shouldRaise) "Raised to $finalBet" else "Chaal $finalBet"
                    )
                    _players.value = list
                    _potAmount.value += finalBet
                    postEvent("${bot.name} bet $finalBet (${if (newlySeen) "Seen" else "Blind"}).")
                    
                    if (shouldRaise) {
                        triggerDealerReaction("${bot.name} raised the stakes! Minimum Chaal is now ${_currentChaal.value}.")
                    }
                    nextTurn()
                }
            }
        }
    }

    fun userSeeCards() {
        if (_phase.value != GamePhase.BETTING) return
        val list = _players.value.toMutableList()
        val user = list[0]
        if (!user.isSeen && !user.isPacked) {
            list[0] = user.copy(isSeen = true, lastAction = "Saw Cards")
            _players.value = list
            postEvent("You saw your cards.")
            
            // In the background, proactively request Gemini Advisor analysis for maximum user delight!
            askGeminiDealerForAdvice()
        }
    }

    fun userPlayChaal() {
        if (_phase.value != GamePhase.BETTING || _activeTurnIndex.value != 0) return
        viewModelScope.launch {
            val list = _players.value.toMutableList()
            val user = list[0]
            val betNeeded = if (user.isSeen) _currentChaal.value * 2 else _currentChaal.value

            if (user.balance < betNeeded) {
                postEvent("System: Insufficient chips to complete bet. Pack or claim free chips!")
                return@launch
            }

            // Secure Balance Update in Database
            repository.updateBalance(-betNeeded, "BET", "Placed Chaal amount $betNeeded")

            // Update UI State with fresh balance
            val freshWallet = repository.getWalletDirect() ?: UserWallet()
            list[0] = user.copy(
                balance = freshWallet.balance,
                lastAction = "Chaal $betNeeded"
            )
            _players.value = list
            _potAmount.value += betNeeded
            postEvent("You bet $betNeeded (${if (user.isSeen) "Seen" else "Blind"}).")

            nextTurn()
        }
    }

    fun userRaise() {
        if (_phase.value != GamePhase.BETTING || _activeTurnIndex.value != 0) return
        viewModelScope.launch {
            val list = _players.value.toMutableList()
            val user = list[0]
            
            // Double the minimum standard chaal
            val nextChaal = if (_currentChaal.value < 1600L) _currentChaal.value * 2 else _currentChaal.value
            if (nextChaal == _currentChaal.value) {
                postEvent("System: Maximum Chaal limit of 1600 chips reached!")
                return@launch
            }

            val betNeeded = if (user.isSeen) nextChaal * 2 else nextChaal

            if (user.balance < betNeeded) {
                postEvent("System: Insufficient chips to double the raise. Chaal or Pack!")
                return@launch
            }

            // Update standard min chaal
            _currentChaal.value = nextChaal

            // Secure Balance Update in Database
            repository.updateBalance(-betNeeded, "BET", "Raised bet to $betNeeded (Chaal: $nextChaal)")

            // Sync with UI
            val freshWallet = repository.getWalletDirect() ?: UserWallet()
            list[0] = user.copy(
                balance = freshWallet.balance,
                lastAction = "Raised to $betNeeded"
            )
            _players.value = list
            _potAmount.value += betNeeded
            postEvent("You raised minimum Chaal to $nextChaal. Bet placed: $betNeeded.")

            triggerDealerReaction("You raised! The table bets just doubled.")
            nextTurn()
        }
    }

    fun userPack() {
        if (_phase.value != GamePhase.BETTING || _activeTurnIndex.value != 0) return
        viewModelScope.launch {
            val list = _players.value.toMutableList()
            val user = list[0]
            list[0] = user.copy(isPacked = true, lastAction = "Packed")
            _players.value = list
            postEvent("You packed.")
            
            // Deduct / record loss transaction securely
            repository.updateBalance(0, "LOOSE", "Packed hand. Round lost.")
            
            nextTurn()
        }
    }

    fun userRequestShow() {
        // Show can only occur if exactly 2 players remain active!
        val activeCount = _players.value.count { !it.isPacked }
        if (activeCount != 2) {
            postEvent("System: Show is only allowed when exactly two players are remaining on the table!")
            return
        }
        if (_activeTurnIndex.value != 0) return
        
        // Execute showdown
        executeShowdown(0)
    }

    private fun nextTurn() {
        val activeList = _players.value.filter { !it.isPacked }
        if (activeList.size <= 1) {
            val winner = activeList.firstOrNull() ?: _players.value[0]
            endGameWithWinner(winner)
            return
        }

        var nextIndex = (_activeTurnIndex.value + 1) % 4
        while (_players.value[nextIndex].isPacked) {
            nextIndex = (nextIndex + 1) % 4
        }

        _activeTurnIndex.value = nextIndex

        if (nextIndex != 0) {
            triggerBotTurn(nextIndex)
        }
    }

    private fun executeShowdown(showRequestorIndex: Int) {
        viewModelScope.launch {
            _phase.value = GamePhase.SHOWDOWN
            _activeTurnIndex.value = -1
            postEvent("${_players.value[showRequestorIndex].name} requested a SHOWDOWN!")

            // Add small delay for suspense
            delay(1500)

            // Evaluate and reveal all active cards
            val activePlayers = _players.value.filter { !it.isPacked }
            
            // Automatically make all active players "Seen" for showdown comparison
            val updatedPlayers = _players.value.map { player ->
                if (!player.isPacked) player.copy(isSeen = true) else player
            }
            _players.value = updatedPlayers

            // Compare HandEvaluations
            var maxEval: HandEvaluation? = null
            var winningPlayer: PlayerState? = null

            val details = mutableListOf<String>()

            for (player in activePlayers) {
                // Re-evaluate to be absolutely safe
                val eval = DealerRanker.evaluate(player.cards)
                details.add("${player.name} holds: ${player.cards.joinToString { it.displayName() }} ($ {eval.handType.displayName})")
                
                if (maxEval == null || eval > maxEval) {
                    maxEval = eval
                    winningPlayer = player
                }
            }

            _showdownDetails.value = details

            val finalWinner = winningPlayer ?: activePlayers.first()
            endGameWithWinner(finalWinner)
        }
    }

    private fun endGameWithWinner(winner: PlayerState) {
        viewModelScope.launch {
            _phase.value = GamePhase.SHOWDOWN
            _activeTurnIndex.value = -1

            val winAmount = _potAmount.value
            _winnerMessage.value = "${winner.name} won physical pot of $winAmount chips!"
            postEvent("Dealer: ${winner.name} wins the pot of $winAmount chips with ${winner.handEvaluation?.handType?.displayName ?: "High Card"}.")

            // If winner is Human, update Room DB securely
            if (winner.id == 0) {
                repository.updateBalance(winAmount, "WIN", "Won Teen Patti Pot!")
            } else {
                // If Human lost, log loose transaction
                repository.updateBalance(0, "LOOSE", "Lost round of Teen Patti.")
                
                // Bot balance increase virtual
                val list = _players.value.toMutableList()
                list[winner.id] = list[winner.id].copy(balance = list[winner.id].balance + winAmount)
                _players.value = list
            }

            _potAmount.value = 0L
            _currentChaal.value = 200L

            triggerDealerReaction("Magnificent round! ${winner.name} takes all the stakes.")
        }
    }

    // --- Gemini Interactive Features ---

    fun askGeminiDealerForAdvice() {
        val user = _players.value.getOrNull(0) ?: return
        if (user.isPacked) return

        _isAiAdvisorLoading.value = true
        _aiAdvisorMessage.value = "Consulting expert Dealer advisor..."

        viewModelScope.launch {
            val response = GeminiService.getHandAdvice(
                cards = user.cards,
                isSeen = user.isSeen,
                pot = _potAmount.value,
                chaal = _currentChaal.value,
                walletBalance = user.balance
            )
            _aiAdvisorMessage.value = response
            _isAiAdvisorLoading.value = false
        }
    }

    private fun triggerDealerReaction(event: String) {
        viewModelScope.launch {
            val reply = GeminiService.getDealerChatMsg(event)
            val current = _dealerChatLogs.value.toMutableList()
            current.add(DealerChatEntry("Dealer", reply))
            if (current.size > 20) current.removeAt(0)
            _dealerChatLogs.value = current
        }
    }

    fun sendUserChatToDealer(msg: String) {
        if (msg.trim().isEmpty()) return
        val current = _dealerChatLogs.value.toMutableList()
        current.add(DealerChatEntry("You", msg))
        _dealerChatLogs.value = current

        viewModelScope.launch {
            val reply = GeminiService.getDealerChatMsg("User sent private chat message: \"$msg\". Comment back kindly, matching their energy.")
            val freshLogs = _dealerChatLogs.value.toMutableList()
            freshLogs.add(DealerChatEntry("Dealer", reply))
            if (freshLogs.size > 20) freshLogs.removeAt(0)
            _dealerChatLogs.value = freshLogs
        }
    }

    // --- Secure Claims ---

    fun claimDailyFreeReward() {
        viewModelScope.launch {
            val success = repository.claimDailyReward()
            if (success) {
                postEvent("System: Securely deposited 10,000 free chips into your vault wallet.")
                val current = _players.value.toMutableList()
                val freshWallet = repository.getWalletDirect() ?: UserWallet()
                current[0] = current[0].copy(balance = freshWallet.balance)
                _players.value = current
                
                triggerDealerReaction("User claimed their daily free chip bonus of 10,000!")
            } else {
                postEvent("System: Free gold reward cooldown active. Next claim available in 1 minute!")
            }
        }
    }
}
