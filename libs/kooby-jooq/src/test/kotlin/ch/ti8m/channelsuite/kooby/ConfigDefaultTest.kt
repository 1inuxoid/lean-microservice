package ch.ti8m.channelsuite.kooby

import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.Test

/**
 *
 * @author marcus
 * @since  27.05.18
 */
internal class ConfigDefaultTest {

    @Test
    fun testDefaults() {
        val config = ConfigFactory.load()
        // this should not throw:
        securityTokenProperties(config.getConfig("channelsuite.security"))
    }
}