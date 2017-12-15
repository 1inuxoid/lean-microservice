package ch.ti8m.channelsuite.stdconfig

import ch.ti8m.channelsuite.eurekaclient.EurekaConfig
import ch.ti8m.channelsuite.security.TokenConfig


data class AppConfig(val name: String,
                     val eurekaConfig: EurekaConfig,
                     val databaseConfig: DatabaseConfig,
                     val tokenConfig: TokenConfig)


data class DatabaseConfig(
        val jdbcUrl: String,
        val username: String,
        val password: String
)

