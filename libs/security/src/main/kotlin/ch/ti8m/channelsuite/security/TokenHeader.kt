package ch.ti8m.channelsuite.security

/**
 * A header containing the channel suite securit token.
 *
 * @author marcus
 * @since  20.01.18
 */
data class TokenHeader(val name: String, val token: String)

/**
 * Provides access to the [TokenHeader] corresponding to the currently active security context
 * suitable to be passed on to downstream calls.
 */
interface TokenHeaderProvider : () -> TokenHeader
