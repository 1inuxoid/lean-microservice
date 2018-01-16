package ch.ti8m.channelsuite.kooby

import ch.ti8m.channelsuite.eurekaclient.EurekaConfig
import ch.ti8m.channelsuite.eurekaclient.EurekaSchedulerWrapper
import ch.ti8m.channelsuite.log.LogFactory
import ch.ti8m.channelsuite.security.TokenConfig
import ch.ti8m.channelsuite.security.api.RequestSecurityContext
import ch.ti8m.channelsuite.security.hasCurrentUserPermission
import ch.ti8m.channelsuite.security.securityTemplate
import ch.ti8m.channelsuite.security.tokenProducer
import ch.ti8m.channelsuite.serviceregistry.client.api.ServiceRegistryClient
import com.google.inject.Binder
import com.typesafe.config.Config
import io.github.config4k.extract
import org.jooby.*
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

        val tokenConfig = conf!!.extract<TokenConfig>("channelsuite.tokenConfig")
        mappings = conf.extract("channelsuite.permissions")

        env!!.router().use("*", "*") { req, rsp, chain ->
            log.info("filtering request $req")
            val token = req.header(tokenConfig.tokenName)
            val securityTemplate = securityTemplate(tokenConfig, conf.getString("application.name"))

            securityTemplate.performLoggedInWith(token.value("")) {
                chain.next(req, rsp)
            }

        }
        tokenName = tokenConfig.tokenName
        tokenSource = tokenProducer(tokenConfig)
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

    data class TokenHeader(val name: String, val token: String)

    fun tokenHeader() = TokenHeader(tokenName, tokenSource())
}

/**
 * A Jooby module tying start and stop of the Eureka client to the lifecycle of the Jooby app.
 */
class EurekaClient : Jooby.Module {
    private val logger = object : LogFactory{}.classLogger()

    override fun configure(env: Env?, conf: Config?, binder: Binder?) {
        val config = conf!!.extract<EurekaConfig>("channelsuite.eurekaConfig")

        val eurekaScheduler = EurekaSchedulerWrapper(config)

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

