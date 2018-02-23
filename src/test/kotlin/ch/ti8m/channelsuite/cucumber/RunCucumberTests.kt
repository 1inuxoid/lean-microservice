package ch.ti8m.channelsuite.cucumber

import cucumber.api.CucumberOptions
import cucumber.api.junit.Cucumber
import org.junit.runner.RunWith

/**
 * Runner to run cucumber bases test from the IDE.
 *
 * <p> the gradle task is called cucumber: <i>gradlew cucumber</i>  </p>
 */
@RunWith(Cucumber::class)
@CucumberOptions(
        plugin = ["pretty"],
        features = ["src/test/resources/cucumber"],
        glue = ["ch.ti8m.channelsuite.config"],
        tags = ["not @Ignored"]
)
class CucumberRunnerTest