package ch.ti8m.channelsuite.security

import ch.ti8m.channelsuite.security.api.ContextTokenProvider
import ch.ti8m.channelsuite.security.api.SecurityContextTemplate

enum class TokenType {saml, simple}
data class TokenConfig(
        val tokenType: TokenType,
        val rolesAttributeName: String,
        val useridAttributeName : String,
        val tenantAttributeName : String,
        val tokenName : String
)


fun securityTemplate(config: TokenConfig, appName: String): SecurityContextTemplate =
    when (config.tokenType) {
        TokenType.saml -> samlSecurityContextTemplate(config, appName)
        TokenType.simple -> SimpleSecurityContextTemplate
    }

private fun tokenMarshaller(config: TokenConfig) =
        when (config.tokenType) {
            TokenType.saml -> marshaller
            TokenType.simple -> SimpleTokenMarshaller
        }

private fun tokenConverter(config: TokenConfig) =
        when (config.tokenType) {
            TokenType.saml -> converter(config)
            TokenType.simple -> SimpleTokenConverter
        }

fun tokenProducer(config: TokenConfig) =
        {
            tokenMarshaller(config).marshal(
                    ContextTokenProvider(tokenConverter(config)).tokenForCurrentContext()
            )
        }