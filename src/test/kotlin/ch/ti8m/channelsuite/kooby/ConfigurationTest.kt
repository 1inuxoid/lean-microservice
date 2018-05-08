package ch.ti8m.channelsuite.kooby

import ch.ti8m.channelsuite.eurekaclient.Client
import ch.ti8m.channelsuite.eurekaclient.EurekaConfig
import ch.ti8m.channelsuite.eurekaclient.Instance
import com.typesafe.config.ConfigFactory
import io.github.config4k.extract
import io.github.config4k.toConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

internal class ConfigurationTest {


    val eurekaConfig = EurekaConfig(Client(true, mapOf("default" to listOf("url")),10,10),
            Instance("name", "host", 8080, "ctxt", "default", mapOf( "hasGui" to "true") ) )



    @Test
    fun serializeConfig() {

        println( eurekaConfig.toConfig("channelsuite.eureka").root().render() )

    }

    @Test
    fun deserializeConfig() {
        val config = ConfigFactory.parseString(serialized);

        val readConfig = config.extract<EurekaConfig>("channelsuite.eureka")

        assertTrue { readConfig.instance.zone == "default" }
    }

    val serialized =
"""
   {
    # hardcoded value
    "channelsuite" : {
        # hardcoded value
        "eureka" : {
            # hardcoded value
            "client" : {
                # hardcoed value
                enabled: false,
                # hardcoded value
                "fetchRegistryIntervalInMs" : 30000,
                # hardcoded value
                "heartbeatIntervalInMs" : 30000,
                # hardcoded value
                "preferSameZone" : true,
                # hardcoded value
                "serviceRegistryUrl" : {
                    # hardcoded value
                    "default" : [
                        # hardcoded value
                        "url"
                    ]
                }
            },
            # hardcoded value
            "instance" : {
                # hardcoded value
                "hostName" : "host",
                # hardcoded value
                "metadata" : {
                    # hardcoded value
                    "hasGui" : true
                },
                # hardcoded value
                "port" : 8080,
                # hardcoded value
                "serviceContext" : "ctxt",
                # hardcoded value
                "serviceName" : "name",
                # hardcoded value
                "zone" : "default"
            }
        }
    }
}
"""

}