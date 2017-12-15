package ch.ti8m.channelsuite.kooby

import ch.ti8m.channelsuite.eurekaclient.EurekaConfig
import ch.ti8m.channelsuite.eurekaclient.EurekaSchedulerWrapper
import ch.ti8m.channelsuite.security.api.RequestInfoImpl
import ch.ti8m.channelsuite.security.api.UserInfo
import ch.ti8m.channelsuite.security.securityTemplate
import ch.ti8m.channelsuite.stdconfig.AppConfig
import ch.ti8m.channelsuite.xservice.logger
import com.google.inject.Binder
import com.typesafe.config.Config
import io.github.config4k.extract
import org.jooby.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.concurrent.thread


interface Package

val log = object : Package {
    val logger: Logger = LoggerFactory.getLogger(this.javaClass.`package`.name)
}.logger


class ChannelsuiteIntegration : Jooby.Module {
    override fun configure(env: Env?, conf: Config?, binder: Binder?) {

        val appConfig = conf!!.extract<AppConfig>("channelsuite")

        setupEureka(appConfig.eurekaConfig)

        env!!.router().use("*", "*") { req, rsp, chain ->
            log.info("filtering request $req")
            val token = req.header(appConfig.tokenConfig.tokenName)
            val securityTemplate = securityTemplate(appConfig.tokenConfig)
            if (token.isSet)
                securityTemplate.setupSecurityContext(token.value())
            else
                securityTemplate.setupSecurityContext(
                        appConfig.name,
                        UserInfo.ANONYMOUS,
                        RequestInfoImpl.EMPTY,
                        null)

            chain.next(req, rsp)
            securityTemplate.tearDownSecurityContext()
        }
    }


    private fun setupEureka(config: EurekaConfig) {
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
