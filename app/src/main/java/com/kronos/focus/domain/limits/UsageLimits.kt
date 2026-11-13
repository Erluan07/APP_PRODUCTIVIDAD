package com.kronos.focus.domain.limits

import com.kronos.focus.domain.rules.AppRulesConfig

/** Motivo por el que una app con límite está bloqueada ahora mismo. */
sealed interface LimitBlock {
    /** Pasaste [AppRulesConfig.USAGE_CONTINUOUS_MAX_MIN] min seguidos: bloqueada hasta [untilMs]. */
    data class Cooldown(val untilMs: Long) : LimitBlock
    /** Llegaste al máximo diario: bloqueada hasta que acabe el día. */
    data object DailyExhausted : LimitBlock
}

object UsageLimits {
    /** Estado de una app con límite. null = se puede usar. */
    fun blockFor(usedTodayMs: Long, dailyLimitMin: Int, cooldownUntilMs: Long, now: Long): LimitBlock? = when {
        now < cooldownUntilMs -> LimitBlock.Cooldown(cooldownUntilMs)
        usedTodayMs >= dailyLimitMin * 60_000L -> LimitBlock.DailyExhausted
        else -> null
    }

    /** Un límite ya puesto solo se puede endurecer (menos minutos), nunca aflojar ni quitar. */
    fun canSet(current: Int?, requested: Int): Boolean =
        requested in AppRulesConfig.USAGE_MIN_LIMIT_MIN..AppRulesConfig.USAGE_MAX_LIMIT_MIN && (current == null || requested <= current)

    /** ¿Lleva demasiado tiempo seguido? */
    fun continuousExceeded(continuousMs: Long): Boolean = continuousMs >= AppRulesConfig.USAGE_CONTINUOUS_MAX_MIN * 60_000L
}
