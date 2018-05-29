package ch.ti8m.channelsuite.kooby

import ch.ti8m.channelsuite.eurekaclient.EurekaConfig
import ch.ti8m.channelsuite.eurekaclient.EurekaSchedulerWrapper
import ch.ti8m.channelsuite.log.LogFactory
import ch.ti8m.channelsuite.security.*
import ch.ti8m.channelsuite.security.api.RequestSecurityContext
import ch.ti8m.channelsuite.security.api.SecurityContextTemplate
import ch.ti8m.channelsuite.security.api.SecurityTokenProperties
import ch.ti8m.channelsuite.serviceregistry.client.api.ServiceRegistryClient
import com.google.inject.Binder
import com.typesafe.config.Config
import com.typesafe.config.ConfigBeanFactory
import com.typesafe.config.ConfigFactory
import io.github.config4k.extract
import org.jooby.*
import java.util.*
import kotlin.concurrent.thread

/**
 * A Jooby modules setting up a channelsuite security-context for each request to an
 * application resource.
 * You may want to grab hold of this modules instance for convenient authorisation (permission-checkind)
 * using [doIfPermitted]
 */
class ChannelsuiteSecurity : Jooby.Module {
    private val log = object : LogFactory {}.classLogger()

    private lateinit var mappings: Map<String, List<String>>

    private lateinit var tokenSource: () -> String
    private lateinit var tokenName: String

    override fun configure(env: Env?, conf: Config?, binder: Binder?) {

        val securityConfig = channelsuiteSecurityConfig(conf)
        val tokenConfig = securityTokenProperties(securityConfig)
        val transportConfig = adderExtractorConfig(securityConfig)

        mappings = conf!!.extract("channelsuite.permissions")
        val securityTemplate = securityTemplate(tokenConfig, conf.getString("application.name"))

        env!!.router().use("*", "*") { req, rsp, chain ->
            log.info("filtering request $req")

            val token : Optional<String> = when(transportConfig.extractor.transport) {
                Transport.header -> req.header(transportConfig.extractor.name).toOptional()
                Transport.cookie -> req.cookies().find { it.name().equals(transportConfig.extractor.name) }?.value()
                        ?: Optional.empty()
            }

            securityTemplate.performLoggedInWith(token.orElse("")) {
                chain.next(req, rsp)
            }

        }
        tokenName = transportConfig.adder.name
        tokenSource = tokenProducer(tokenConfig)

        // sadly, guice does not support direct binding of kotlin function types yet ...
        val tokenHeaderProvider: TokenHeaderProvider = object : TokenHeaderProvider {
            override fun invoke() = tokenHeader()
        }
        binder!!.bind(TokenHeaderProvider::class.java).toInstance(tokenHeaderProvider)
        binder.bind(SecurityContextTemplate::class.java).toInstance(securityTemplate)

        log.info("channelsuite security integration set up successfully.")
    }


    fun doIfPermitted(permission: String, function: () -> Result): Any =
            when {
                RequestSecurityContext.getUserInfo().isAnonymous -> Results.with(Status.UNAUTHORIZED)
                hasCurrentUserPermission(permission, mappings) -> function()
                else -> {
                    log.info("user {} is not permitted {}", RequestSecurityContext.getUserInfo(), permission)
                    Results.with(Status.FORBIDDEN)
                }
            }


    fun tokenHeader() = TokenHeader(tokenName, tokenSource())
}

//private val defaults = ConfigFactory.parseM

// TODO loading the default-reference explicitly here should not be necessary -- file a Jooby issue?
internal fun channelsuiteSecurityConfig(conf: Config?): Config = conf?.withFallback(ConfigFactory.defaultReference())
        ?.getConfig("channelsuite.security") ?: throw RuntimeException("Missing config for channelsuite.security")

internal fun securityTokenProperties(conf: Config?) : SecurityTokenProperties {
    val properties = ConfigBeanFactory.create(conf?.getConfig("token"), SecurityTokenProperties::class.java)
    if (! properties.signing.isEnabled) properties.signing.keystore.path = null
    return properties
}

fun adderExtractorConfig(conf: Config) : TokenAdderExtractorConfig {
    val defaultConfig = conf.extract<TokenTransportConfig>("identity-token")
    var adderTransport = defaultConfig.transport
    val extractorTransport = defaultConfig.transport

    if (conf.hasPath("identity-token-adder.transport"))
        adderTransport = conf.getEnum(Transport::class.java,"identity-token-adder.transport")

    if (conf.hasPath("identity-token-extractor.transport"))
        adderTransport = conf.getEnum(Transport::class.java,"identity-token-extractor.transport")

    return TokenAdderExtractorConfig(defaultConfig.copy(
            transport = adderTransport),
            defaultConfig.copy(transport = extractorTransport))
}

/**
 * A Jooby module tying start and stop of the Eureka client to the lifecycle of the Jooby app.
 */
class EurekaClient : Jooby.Module {
    private val logger = object : LogFactory{}.classLogger()

    override fun configure(env: Env?, conf: Config?, binder: Binder?) {
        val eurekaConfig = conf!!.extract<EurekaConfig>("channelsuite.eurekaConfig")

        val securityConfig = channelsuiteSecurityConfig(conf)
        val tokenConfig = securityTokenProperties(securityConfig)
        val transportConfig = adderExtractorConfig(securityConfig)

        val eurekaScheduler = EurekaSchedulerWrapper(eurekaConfig , tokenConfig, transportConfig.adder)

        thread(true) { eurekaScheduler.start() }
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                logger.info("Shutting down")
                eurekaScheduler.stop()
            }
        })
        binder!!.bind(ServiceRegistryClient::class.java).toInstance(eurekaScheduler.registryClient)
    }
}

/**
 * looks up the URL for a service registered with the Eureka registry
 *
 * @return the URL for the service, without a trailing slash
 */
fun Registry.serviceUrl(serviceName: String): String {
    val registry = require(ServiceRegistryClient::class.java)

    val serviceInstance = registry.getNextServiceInstance(serviceName)
    val uri = serviceInstance.uri.toString()
    return uri +
            if (!serviceInstance.isServiceContextDefined)
                (if (!uri.endsWith("/")) "/" else "") +
                        serviceName else ""
}

