package ch.ti8m.channelsuite.kooby

import java.util.function.Predicate


/**
 * Allows running code depending whether the environment matches some regular expression-
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