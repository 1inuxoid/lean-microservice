package ch.ti8m.channelsuite.security.saml

import ch.ti8m.channelsuite.security.TokenSupportFactory
import ch.ti8m.channelsuite.security.api.SecurityTokenProperties
import ch.ti8m.channelsuite.security.api.TokenValidator
import ch.ti8m.channelsuite.security.keystore


/**
 * Wires up and configures support for SAML tokens as a means of transporting user identity
 *
 * @author marcus
 * @since  27.10.17
 */

internal class SamlTokenSupportFactory(val config: SecurityTokenProperties) : TokenSupportFactory<SamlToken> {
    override fun validator()
            = if (config.validation.isEnabled)
                    SamlTokenValidator(config, SamlTokenSignatureValidator(keystore(config.signing.keystore)))
              else NoopValidator

    override fun tokenMarshaller()
            = SamlTokenMarshaller()

    override fun tokenConverter()
            = SamlUserInfoTokenConverter(config, if (config.signing.isEnabled) SamlTokenSigner(config, keystore(config.signing.keystore)) else null )
}

private object NoopValidator : TokenValidator<SamlToken> {
    override fun validateToken(securityToken: SamlToken) {}
}


