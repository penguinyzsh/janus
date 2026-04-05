package org.pysh.janus.hook.lyric

/**
 * A single timed lyric line.
 * Times are in milliseconds.
 */
data class TimedLine(
    val beginMs: Long,
    val endMs: Long,
    val text: String,
)
