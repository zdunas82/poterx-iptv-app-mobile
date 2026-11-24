package tv.own.owntv.mobile.poterx

/**
 * Poterx: things asked once a day while the app stays open. A television keeps the app in memory for
 * days, so "once per launch" meant a new version, or a subscription about to end, went unseen until
 * the app happened to be restarted. They are asked again when the app comes back to the front, once
 * a day has passed since the last time.
 */
object PoterxDaily {
    const val DAY_MS = 24L * 60 * 60 * 1000

    /** True when [lastAt] (0 = never) is a day old. A clock moved back since counts as due: the wait must not stretch. */
    fun due(lastAt: Long, now: Long): Boolean = lastAt <= 0L || now < lastAt || now - lastAt >= DAY_MS
}
