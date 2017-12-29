package ch.ti8m.channelsuite.security

import ch.ti8m.channelsuite.security.api.SecurityContextTemplate

enum class TokenType {saml, simple}
data class TokenConfig(
        val tokenType: TokenType,
        val rolesAttributeName: String,
        val useridAttributeName : String,
        val tenantAttributeName : String,
        val tokenName : String
)


fun securityTemplate(config: TokenConfig): SecurityContextTemplate =
    when (config.tokenType) {
        TokenType.saml -> samlSecurityContextTemplate(config)
        TokenType.simple -> SimpleSecurityContextTemplate
    }
