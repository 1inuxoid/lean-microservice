package ch.ti8m.channelsuite.xservice

import ch.ti8m.channelsuite.kooby.ChannelsuiteIntegration
import org.jooby.Jooby.run
import org.jooby.Kooby
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger = LoggerFactory.getLogger(ServiceMain::class.qualifiedName) as Logger


class ServiceMain : Kooby({

    use( ChannelsuiteIntegration() )

    get {
        val name = param("name").value("Kotlin")
        "Hello $name!"
    }

    get("/health") {
        logger.info("health called")
        "I'm still here"
    }

})

/**
 * Run application:
 */
fun main(args: Array<String>) {
    run(::ServiceMain, args)

    logger.info("leaving main ...")
}



