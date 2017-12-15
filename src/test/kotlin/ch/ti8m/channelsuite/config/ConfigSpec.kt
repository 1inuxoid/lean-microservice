package ch.ti8m.channelsuite.config

import ch.ti8m.channelsuite.eurekaclient.EurekaConfig
import ch.ti8m.channelsuite.security.TokenConfig
import ch.ti8m.channelsuite.security.TokenType
import ch.ti8m.channelsuite.stdconfig.AppConfig
import ch.ti8m.channelsuite.stdconfig.DatabaseConfig
import io.github.config4k.toConfig
import org.junit.jupiter.api.Test

class ConfigSpec {

    val config = AppConfig("test", EurekaConfig("http://bla.ch/cockpit", "bla", "/bla","10.10.36.66", "8080"),
            DatabaseConfig("jdbc://oracle","testuser","pw"),
            TokenConfig(TokenType.saml, "roles","userid","tenant","AL_IDENTITY"))

    @Test
    fun serialisatsion_works() {

        println(config.toConfig("bla"))

    }

}