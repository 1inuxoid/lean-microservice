package ch.ti8m.channelsuite

import ch.ti8m.channelsuite.xservice.ServiceMain
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.restassured.RestAssured
import java.util.*

open class ServiceBaseIT {
    val app = ServiceMain()
    lateinit var localBaseUrl: String

    open fun environment() = "junit"
    open fun initialiseDb(conf: Config) {}

    open fun startService() {
        initialiseDb(ConfigFactory.load("application." + environment()))

        val randomPort = (10_000..65_535).randomPort()
        localBaseUrl = "http://localhost:$randomPort"
        app.port(randomPort)
        app.start("server.join=false", environment())
        RestAssured.baseURI = localBaseUrl
    }

    open fun stopService() {
        app.stop()
    }

    private fun ClosedRange<Int>.randomPort() = Random().nextInt(endInclusive - start) + start
}
