package com.example.model

enum class Suit(val symbol: String, val colorRed: Boolean) {
    HEARTS("♥", true),
    DIAMONDS("♦", true),
    CLUBS("♣", false),
    SPADES("♠", false)
}

enum class Rank(val value: Int, val symbol: String) {
    TWO(2, "2"),
    THREE(3, "3"),
    FOUR(4, "4"),
    FIVE(5, "5"),
    SIX(6, "6"),
    SEVEN(7, "7"),
    EIGHT(8, "8"),
    NINE(9, "9"),
    TEN(10, "10"),
    JACK(11, "J"),
    QUEEN(12, "Q"),
    KING(13, "K"),
    ACE(14, "A")
}

data class Card(val suit: Suit, val rank: Rank) {
    fun displayName() = "${rank.symbol}${suit.symbol}"
}

enum class HandType(val displayName: String, val power: Int) {
    HIGH_CARD("High Card", 1),
    PAIR("Pair", 2),
    COLOR("Color / Flush", 3),
    SEQUENCE("Sequence / Straight", 4),
    PURE_SEQUENCE("Pure Sequence / Straight Flush", 5),
    TRIO("Trio / Trail", 6)
}

data class HandEvaluation(
    val handType: HandType,
    val primaryPower: Int,
    val kicker1: Int = 0,
    val kicker2: Int = 0
) : Comparable<HandEvaluation> {
    override fun compareTo(other: HandEvaluation): Int {
        if (this.handType.power != other.handType.power) {
            return this.handType.power.compareTo(other.handType.power)
        }
        if (this.primaryPower != other.primaryPower) {
            return this.primaryPower.compareTo(other.primaryPower)
        }
        if (this.kicker1 != other.kicker1) {
            return this.kicker1.compareTo(other.kicker1)
        }
        return this.kicker2.compareTo(other.kicker2)
    }
}

object DealerRanker {
    fun evaluate(cards: List<Card>): HandEvaluation {
        if (cards.size < 3) return HandEvaluation(HandType.HIGH_CARD, 0)
        
        // Take first 3 cards and sort them descending by Rank value
        val sorted = cards.take(3).sortedByDescending { it.rank.value }
        val c1 = sorted[0]
        val c2 = sorted[1]
        val c3 = sorted[2]

        val isSameSuit = c1.suit == c2.suit && c2.suit == c3.suit
        
        // Check Sequence (A-2-3 is special highest or lowest sequence depending on house rules; in standard Teen Patti: A-2-3 of same suit is highest, A-K-Q is second, otherwise A-2-3 is highest, A-K-Q second etc.)
        // We will treat A-2-3 as supreme sequence (indicated by custom power 15) and standard sequential values (e.g. A-K-Q as 14, K-Q-J as 13)
        val isNormalSeq = (c1.rank.value - c2.rank.value == 1) && (c2.rank.value - c3.rank.value == 1)
        val isA23Seq = (c1.rank == Rank.ACE && c2.rank == Rank.THREE && c3.rank == Rank.TWO)
        val isSequence = isNormalSeq || isA23Seq
        
        val sequencePower = if (isA23Seq) 15 else c1.rank.value

        // 1. Trio/Trail
        if (c1.rank == c2.rank && c2.rank == c3.rank) {
            return HandEvaluation(
                handType = HandType.TRIO,
                primaryPower = c1.rank.value
            )
        }

        // 2. Pure Sequence (Straight Flush)
        if (isSameSuit && isSequence) {
            return HandEvaluation(
                handType = HandType.PURE_SEQUENCE,
                primaryPower = sequencePower
            )
        }

        // 3. Sequence (Straight)
        if (isSequence) {
            return HandEvaluation(
                handType = HandType.SEQUENCE,
                primaryPower = sequencePower
            )
        }

        // 4. Color (Flush)
        if (isSameSuit) {
            return HandEvaluation(
                handType = HandType.COLOR,
                primaryPower = c1.rank.value,
                kicker1 = c2.rank.value,
                kicker2 = c3.rank.value
            )
        }

        // 5. Pair
        if (c1.rank == c2.rank) {
            return HandEvaluation(
                handType = HandType.PAIR,
                primaryPower = c1.rank.value,
                kicker1 = c3.rank.value
            )
        }
        if (c2.rank == c3.rank) {
            return HandEvaluation(
                handType = HandType.PAIR,
                primaryPower = c2.rank.value,
                kicker1 = c1.rank.value
            )
        }
        // sorted descend so c1 and c3 won't match without c2 unless they are all same, handled above, but technically:
        if (c1.rank == c3.rank) {
            return HandEvaluation(
                handType = HandType.PAIR,
                primaryPower = c1.rank.value,
                kicker1 = c2.rank.value
            )
        }

        // 6. High Card
        return HandEvaluation(
            handType = HandType.HIGH_CARD,
            primaryPower = c1.rank.value,
            kicker1 = c2.rank.value,
            kicker2 = c3.rank.value
        )
    }

    fun makeDeck(): List<Card> {
        val deck = mutableListOf<Card>()
        for (suit in Suit.values()) {
            for (rank in Rank.values()) {
                deck.add(Card(suit, rank))
            }
        }
        return deck.shuffled()
    }
}
