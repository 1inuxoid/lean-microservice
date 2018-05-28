package ch.ti8m.channelsuite.security

import ch.ti8m.channelsuite.log.LogFactory
import ch.ti8m.channelsuite.security.api.*
import ch.ti8m.channelsuite.security.api.SecurityTokenProperties.TokenType.JWT
import ch.ti8m.channelsuite.security.api.SecurityTokenProperties.TokenType.SAML
import ch.ti8m.channelsuite.security.jwt.JwtTokenSupportFactory
import ch.ti8m.channelsuite.security.rest.HttpCookieTokenTransport
import ch.ti8m.channelsuite.security.rest.HttpRequestHeaderTokenTransport
import ch.ti8m.channelsuite.security.rest.SimpleHttpUrlConnectionTokenAdder
import ch.ti8m.channelsuite.security.saml.SamlTokenSupportFactory

private val logger = object : LogFactory {}.packageLogger()

enum class Transport { cookie, header }

data class TokenTransportConfig(
        val name: String,
        val transport: Transport
)

data class TokenAdderExtractorConfig(
        val adder: TokenTransportConfig,
        val extractor: TokenTransportConfig
)

fun httpTokenTranport( config:TokenTransportConfig) =
        when ( config.transport) {
            Transport.cookie -> HttpCookieTokenTransport(config.name)
            Transport.header -> HttpRequestHeaderTokenTransport(config.name)
        }

fun  tokenAdder(config: SecurityTokenProperties, transportConfig:TokenTransportConfig) : SimpleHttpUrlConnectionTokenAdder {
    val tokenSupportFactory = tokenSupportFactory(config)
    return SimpleHttpUrlConnectionTokenAdder(contextTokenProvider(config),tokenSupportFactory.tokenMarshaller(),
            httpTokenTranport(transportConfig))
}

fun tokenProducer(config: SecurityTokenProperties) =
        {
            val tokenSupportFactory = tokenSupportFactory(config)
            tokenSupportFactory.tokenMarshaller().marshal(
                   contextTokenProvider(config)
                            .tokenForCurrentContext()
            )
        }

fun contextTokenProvider(config: SecurityTokenProperties) :ContextTokenProvider {
    val tokenSupportFactory = tokenSupportFactory(config)
    return ContextTokenProvider(tokenSupportFactory.tokenConverter())
}

fun securityTemplate(config: SecurityTokenProperties, appName: String): SecurityContextTemplate {
    val tokenSupportFactory = tokenSupportFactory(config)
    return SecurityContextTemplate(
            tokenSupportFactory.tokenMarshaller(),
            tokenSupportFactory.tokenConverter(),
            tokenSupportFactory.validator(),
            listOf( // just because we can ...
                    object : SecurityContextDistributor {
                        override fun distribute(userInfo: UserInfo?, token: SecurityToken?) {
                            logger.info("User {} logged in.", userInfo)
                        }

                        override fun cleanup() {
                            logger.info("logging out again.")
                        }
                    }), appName)
}


// Wiring specific to some token-type

interface TokenSupportFactory<T : SecurityToken> {
    fun validator(): TokenValidator<T>
    fun tokenMarshaller(): TokenMarshaller
    fun tokenConverter(): UserInfoTokenConverter
}

fun tokenSupportFactory(config: SecurityTokenProperties) =
        when (config.type!!) {
            SAML -> SamlTokenSupportFactory(config)
            JWT -> JwtTokenSupportFactory(config)
        }


fun keystore(keystoreConf: SecurityTokenProperties.TokenKeyStore) = KeystoreLoader.loadKeystore(
        keystoreConf.getPath(), keystoreConf.getPassword())
