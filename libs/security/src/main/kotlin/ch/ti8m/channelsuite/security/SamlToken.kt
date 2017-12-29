package ch.ti8m.channelsuite.security

import ch.ti8m.channelsuite.log.LogFactory
import ch.ti8m.channelsuite.security.api.SecurityContextDistributor
import ch.ti8m.channelsuite.security.api.SecurityContextTemplate
import ch.ti8m.channelsuite.security.api.SecurityToken
import ch.ti8m.channelsuite.security.api.UserInfo
import ch.ti8m.channelsuite.security.saml.SamlConfiguration
import ch.ti8m.channelsuite.security.saml.SamlTokenMarshaller


/**
 * Wires up and configures support for SAML tokens as means of transporting user identity
 *
 * @author marcus
 * @since  27.10.17
 */

private val logger = object : LogFactory {}.packageLogger()

private val factory = SamlConfiguration()

//TODO support token validation here
@Suppress("DEPRECATION")
private val marshaller = SamlTokenMarshaller()

private fun converter(config: TokenConfig)  =
    factory.samlTokenProvider(
            config.rolesAttributeName,
            config.useridAttributeName,
            config.tenantAttributeName)



fun samlSecurityContextTemplate(config: TokenConfig) =
        SecurityContextTemplate(marshaller, converter(config),
            listOf( // just because we can ...
                    object : SecurityContextDistributor {
                        override fun distribute(userInfo: UserInfo?, token: SecurityToken?) {
                            logger.info("User {} logged in.", userInfo)
                        }

                        override fun cleanup() {
                            logger.info("logging out again.")
                        }
                    }), "kotlin-poc")



