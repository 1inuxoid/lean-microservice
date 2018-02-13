package ch.ti8m.channelsuite.kooby

import com.codahale.metrics.health.HealthCheck
import com.codahale.metrics.health.HealthCheckRegistry
import com.google.inject.Binder
import com.google.inject.Inject
import com.google.inject.Scopes
import com.typesafe.config.Config
import org.jooby.*
import java.lang.management.ManagementFactory


/**
 * Defines the endpoints "/health", "/metrics" and "/trace" which are expected by the channel suite cockpit
 * to deliver data in the format offered by spring actuators.
 */
class SpringActuatorLikeMetricsModule : Jooby.Module {
    override fun configure(env: Env?, conf: Config?, binder: Binder?) {
        binder!!.bind(LegacyHealthAggregator::class.java).`in`(Scopes.SINGLETON)

        with(env!!.router()) {
            get("/health") { r ->
                r.require(LegacyHealthAggregator::class.java).overallHealth()
            }
            get("/metrics", ActuatorMetricsHandler())
            get("/trace") { _ -> "unsupported" }
        }
    }
}

/**
 * Adapts the dropwizard health result to what is expected by a spring actuator health check.
 */
data class LegacyHealth(val detailedResult: HealthCheck.Result) {
    fun getStatus() = if (detailedResult.isHealthy) "UP" else "DOWN"
}

class LegacyHealthAggregator
@Inject constructor(val healthCheckRegistry: HealthCheckRegistry, val config: Config) {
    private fun status(isHealthy: Boolean) = if (isHealthy) "UP" else "DOWN"

    fun overallHealth(): Map<String, Any> {
        val resultMap = healthCheckRegistry.runHealthChecks()
        val h = resultMap.values.fold(true, { allElseGood, h -> allElseGood && h.isHealthy })
        return mapOf(
                "status" to status(h),
                "description" to "Overall health of ${config.getString("application.name")}"
        ) + resultMap.map { result -> Pair(result.key, LegacyHealth(result.value)) }
    }
}

class ActuatorMetricsHandler : Route.Handler {
    private val systemMXBean = ManagementFactory.getOperatingSystemMXBean()
    private val memoryMXBean = ManagementFactory.getMemoryMXBean()


    override fun handle(req: Request?, rsp: Response?) {
        val heapMemoryUsage = memoryMXBean.heapMemoryUsage
        val nonHeapMemoryUsage = memoryMXBean.nonHeapMemoryUsage

        val totalMem = (heapMemoryUsage.committed + nonHeapMemoryUsage.committed)/1024
        val usedTotalMem = (heapMemoryUsage.used + nonHeapMemoryUsage.used)/1024

        rsp!!.status(Status.OK)
                .header("Cache-Control", "no-cache,must-revalidate,no-store")
                .send(
                        mapOf(
                                "mem" to totalMem,
                                "mem.free" to totalMem - usedTotalMem,
                                "processors" to systemMXBean.availableProcessors,
                                "systemload.average" to systemMXBean.systemLoadAverage
                        )
                )
    }
}

