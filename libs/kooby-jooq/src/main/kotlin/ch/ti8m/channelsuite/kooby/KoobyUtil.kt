package ch.ti8m.channelsuite.kooby

import java.util.function.Predicate


/**
 *
 * @author marcus
 * @since  29.12.17
 */

class EnvMatcher(private val regex: Regex) : Predicate<String> {
    companion object {
        fun from( regex: String) = EnvMatcher(Regex.fromLiteral(regex))
    }
    override fun test(t: String) = t.matches(regex)
}