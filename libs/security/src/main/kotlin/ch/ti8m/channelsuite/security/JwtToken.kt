package ch.ti8m.channelsuite.security.jwt

import ch.ti8m.channelsuite.security.TokenSupportFactory
import ch.ti8m.channelsuite.security.api.SecurityTokenProperties
import ch.ti8m.channelsuite.security.api.TokenValidator

/**
 * Wires up and configures support for jwt tokens as means of transporting user identity
 *
 * @author marcus
 * @since  27.10.17
 */

class JwtTokenSupportFactory(val config: SecurityTokenProperties) : TokenSupportFactory<JwtToken> {
    override fun validator()= if (config.validation.isEnabled) JwtTokenValidator(config) else NoopValidator
    override fun tokenMarshaller() =  JwtTokenMarshaller()
    override fun tokenConverter() = JwtUserInfoTokenConverter(config)
}

private object NoopValidator : TokenValidator<JwtToken> {
    override fun validateToken(securityToken: JwtToken) {}
}


