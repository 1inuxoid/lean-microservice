package ch.ti8m.channelsuite.xservice

import ch.ti8m.channelsuite.eurekaclient.EurekaConfig
import ch.ti8m.channelsuite.eurekaclient.EurekaSchedulerWrapper
import ch.ti8m.channelsuite.kooby.SecurityFilter
import ch.ti8m.channelsuite.stdconfig.AppConfig
import com.typesafe.config.Config
import io.github.config4k.extract
import org.jooby.Jooby.run
import org.jooby.Kooby
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.concurrent.thread

val logger = LoggerFactory.getLogger(ServiceMain::class.qualifiedName) as Logger


class ServiceMain : Kooby({
    use("*", "*", SecurityFilter)

    get {
        val name = param("name").value("Kotlin")
        "Hello $name!"
    }

    get("/health") {
        logger.info("health called")
        "I'm still here"
    }

    onStarted {
        val config = require(Config::class.java)
        val appConfig = config.extract<AppConfig>("channelsuite")

        setupEureka(appConfig.eurekaConfig)
        SecurityFilter.appConfig = appConfig
    }
})


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

/**
 * Run application:
 */
fun main(args: Array<String>) {
    run(::ServiceMain, args)

    logger.info("leaving main ...")
}



