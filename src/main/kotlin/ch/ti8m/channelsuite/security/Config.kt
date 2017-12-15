package ch.ti8m.channelsuite.security

import ch.ti8m.channelsuite.security.api.SecurityContextTemplate


fun securityTemplate(config: TokenConfig): SecurityContextTemplate =
    when (config.tokenType) {
        TokenType.saml -> samlSecurityContextTemplate(config)
        TokenType.simple -> SimpleSecurityContextTemplate
    }
