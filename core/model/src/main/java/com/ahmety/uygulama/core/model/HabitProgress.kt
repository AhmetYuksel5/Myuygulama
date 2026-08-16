package com.ahmety.uygulama.core.model

import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Hafif ilerleme/oyunlaştırma. Bilinçli olarak sade: puan ve seviye sayı olarak
 * — rozet, emoji, kutlama animasyonu yok. Amaç motivasyonu görünür kılmak,
 * uygulamayı oyuncağa çevirmek değil.
 *
 * Saf fonksiyon: veritabanına ve saate bağlı değil, test edilebilir.
 */
object HabitProgress {

    /**
     * Puan iki şeyden gelir: geçmişteki toplam tamamlama (kalıcı emek) ve şu an
     * süren serilerin toplamı (güncel süreklilik ödülü). Seri ağırlıklı, çünkü
     * asıl ödüllendirmek istediğimiz davranış süreklilik.
     */
    fun score(totalCompletions: Int, activeStreakSum: Int): Int =
        totalCompletions * POINTS_PER_COMPLETION + activeStreakSum * POINTS_PER_STREAK_DAY

    data class Level(
        val level: Int,
        val score: Int,
        val currentLevelFloor: Int,
        val nextLevelFloor: Int,
    ) {
        /** Bu seviyede ne kadar ilerlendiği (0..1). Son seviyede 1. */
        val progress: Float
            get() {
                val span = nextLevelFloor - currentLevelFloor
                if (span <= 0) return 1f
                return ((score - currentLevelFloor).toFloat() / span).coerceIn(0f, 1f)
            }

        val pointsToNext: Int get() = (nextLevelFloor - score).coerceAtLeast(0)
    }

    /**
     * Seviye eşikleri karesel büyür: her seviye bir öncekinden biraz daha zor.
     * Seviye n'in tabanı `BASE * (n-1)^2`. Böylece ilk seviyeler hızlı, sonrakiler
     * yavaş gelir — klasik ve dengeli bir eğri.
     */
    fun levelFor(score: Int): Level {
        val safeScore = score.coerceAtLeast(0)
        val level = (1 + floor(sqrt(safeScore.toDouble() / BASE)).toInt()).coerceAtLeast(1)
        return Level(
            level = level,
            score = safeScore,
            currentLevelFloor = floorFor(level),
            nextLevelFloor = floorFor(level + 1),
        )
    }

    private fun floorFor(level: Int): Int = BASE * (level - 1) * (level - 1)

    private const val POINTS_PER_COMPLETION = 10
    private const val POINTS_PER_STREAK_DAY = 5
    private const val BASE = 100
}
