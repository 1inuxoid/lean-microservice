package ch.ti8m.channelsuite.kooby

import ch.ti8m.channelsuite.eurekaclient.EurekaConfig
import ch.ti8m.channelsuite.eurekaclient.EurekaSchedulerWrapper
import ch.ti8m.channelsuite.log.LogFactory
import ch.ti8m.channelsuite.security.TokenConfig
import ch.ti8m.channelsuite.security.api.RequestInfoImpl
import ch.ti8m.channelsuite.security.api.UserInfo
import ch.ti8m.channelsuite.security.securityTemplate
import com.google.inject.Binder
import com.typesafe.config.Config
import io.github.config4k.extract
import org.jooby.Env
import org.jooby.Jooby
import kotlin.concurrent.thread

/**
 * A Jooby modules setting up a channelsuite security-context for each request to an
 * application resource.
 */
class ChannelsuiteSecurity : Jooby.Module {
    private val log = object : LogFactory {}.classLogger()

    override fun configure(env: Env?, conf: Config?, binder: Binder?) {

        val tokenConfig = conf!!.extract<TokenConfig>("channelsuite.tokenConfig")

        env!!.router().use("*", "*") { req, rsp, chain ->
            log.info("filtering request $req")
            val token = req.header(tokenConfig.tokenName)
            val securityTemplate = securityTemplate(tokenConfig)
            if (token.isSet)
                securityTemplate.setupSecurityContext(token.value())
            else
                securityTemplate.setupSecurityContext(
                        conf.getString("application.name"),
                        UserInfo.ANONYMOUS,
                        RequestInfoImpl.EMPTY,
                        null)

            chain.next(req, rsp)
            securityTemplate.tearDownSecurityContext()
        }
        log.info("channelsuite security integration set up successfully.")
    }
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
    }
}
