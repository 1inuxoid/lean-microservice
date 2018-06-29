package ch.ti8m.channelsuite.kooby

import ch.ti8m.channelsuite.database.extractDbConfigFrom
import com.codahale.metrics.health.HealthCheck
import com.codahale.metrics.health.HealthCheckRegistry
import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.inject.Binder
import com.google.inject.Inject
import com.google.inject.Scopes
import com.typesafe.config.Config
import org.apache.commons.lang3.StringUtils
import org.jooby.*
import java.io.File
import java.lang.management.ManagementFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Defines the endpoints "/health", "/metrics" and "/trace" which are expected by the channel suite cockpit
 * to deliver data in the format offered by Spring Boot actuators.
 */
class SpringActuatorAdapter : Jooby.Module {
    override fun configure(env: Env?, conf: Config?, binder: Binder?) {
        binder!!.bind(ActuatorHealthAggregator::class.java).`in`(Scopes.SINGLETON)

        with(env!!.router()) {
            get("/health") { r ->
                r.require(ActuatorHealthAggregator::class.java).overallHealth()
            }
            get("/metrics", ActuatorMetricsHandler())
            get("/trace", ActuatorTraceHandler())
        }
    }
}

/**
 * Adapts the dropwizard health result to what is delivered by a Spring Boot actuator health check. Unfortunately, the
 * old dropwizard metrics Result class is not extendable. Earlier versions fix this but Jooby should upgrade that first.
 */
private open class ActuatorHealthCheckResult(val detailedResult: HealthCheck.Result) {
    fun getStatus() = if (detailedResult.isHealthy) "UP" else "DOWN"
}
private class ActuatorDbHealthCheckResult(detailedResult: HealthCheck.Result, @JsonIgnore val config: Config) : ActuatorHealthCheckResult(detailedResult) {

    fun getDatabase(): String? {
        val jdbcUrl = extractDbConfigFrom(config).jdbcUrl
        return if (StringUtils.isEmpty(jdbcUrl)) "n/a" else jdbcUrl.split(":")[1]
    }
    fun getCheckStatement() = extractDbConfigFrom(config).checkStatement
}
private class ActuatorDiskSpaceHealthCheckResult(detailedResult: HealthCheck.Result) : ActuatorHealthCheckResult(detailedResult) {
    private val file = File("/")
    // contrary to the memory values (see ActuatorMetricsHandler) this has to be in bytes rather than KB
    fun getTotal() = file.totalSpace
    fun getFree() = file.freeSpace
}

class ActuatorHealthAggregator
@Inject constructor(private val healthCheckRegistry: HealthCheckRegistry, private val config: Config) {
    private fun status(isHealthy: Boolean) = if (isHealthy) "UP" else "DOWN"

    fun overallHealth(): Map<String, Any> {
        val resultMap = mutableMapOf<String, HealthCheck.Result>()
        resultMap.putAll(healthCheckRegistry.runHealthChecks())
        resultMap["diskSpace"] = HealthCheck.Result.healthy()
        val h = resultMap.values.fold(true) { allElseGood, h -> allElseGood && h.isHealthy }
        return mapOf(
                "status" to status(h),
                "description" to "Overall health of ${config.getString("application.name")}"
        ) + resultMap.map { result -> mapHealthCheckResult(result) }
    }

    private fun mapHealthCheckResult(result: Map.Entry<String, HealthCheck.Result>): Pair<String, ActuatorHealthCheckResult> {
        return when(result.key){
            "db" -> Pair(result.key, ActuatorDbHealthCheckResult(result.value, config))
            "diskSpace" -> Pair(result.key, ActuatorDiskSpaceHealthCheckResult(result.value))
            else -> Pair(result.key, ActuatorHealthCheckResult(result.value))
        }
    }
}

// Additional logic for CPU usage "inspired" by https://github.com/micrometer-metrics/micrometer/
// blob/master/micrometer-core/src/main/java/io/micrometer/core/instrument/binder/system/ProcessorMetrics.java
class ActuatorMetricsHandler : Route.Handler {
    // List of public, exported interface class names from supported JVM implementations.
    companion object {
        private val OPERATING_SYSTEM_BEAN_CLASS_NAMES = listOf(
            "com.sun.management.OperatingSystemMXBean", // HotSpot
            "com.ibm.lang.management.OperatingSystemMXBean" // J9
        )
    }
    private val operatingSystemBean = ManagementFactory.getOperatingSystemMXBean()
    private val operatingSystemBeanClass = getFirstClassFound(OPERATING_SYSTEM_BEAN_CLASS_NAMES)
    private val systemCpuUsage = detectMethod("getSystemCpuLoad")
    private val memoryBean = ManagementFactory.getMemoryMXBean()

    override fun handle(req: Request?, rsp: Response?) {
        val heapMemoryUsage = memoryBean.heapMemoryUsage
        val nonHeapMemoryUsage = memoryBean.nonHeapMemoryUsage

        // Boot reports KB rather than bytes: https://github.com/spring-projects/spring-boot/blob/v1.5.6.RELEASE
        // /spring-boot-actuator/src/main/java/org/springframework/boot/actuate/endpoint/SystemPublicMetrics.java#L136
        // -> divide by 1024
        val totalMem = (heapMemoryUsage.committed + nonHeapMemoryUsage.committed) / 1024
        val usedTotalMem = (heapMemoryUsage.used + nonHeapMemoryUsage.used) / 1024

        rsp!!.status(Status.OK)
            .header("Cache-Control", "no-cache,must-revalidate,no-store")
            .send(
                mapOf(
                    "mem" to totalMem,
                    "mem.free" to totalMem - usedTotalMem,
                    "processors" to operatingSystemBean.availableProcessors,
                    "systemload.average" to operatingSystemBean.systemLoadAverage,
                    "system.cpu.usage" to invoke(systemCpuUsage)
                )
            )
    }

    private operator fun invoke(method: Method?): Double {
        return try {
            if (method == null) Double.NaN else method.invoke(operatingSystemBean) as Double
        } catch (e: IllegalAccessException) {
            java.lang.Double.NaN
        } catch (e: IllegalArgumentException) {
            java.lang.Double.NaN
        } catch (e: InvocationTargetException) {
            java.lang.Double.NaN
        }
    }

    private fun detectMethod(name: String): Method? {
        if (operatingSystemBeanClass == null) {
            return null
        }
        return try {
            // ensure the Bean we have is actually an instance of the interface (provoking ClassCastException otherwise)
            operatingSystemBeanClass.cast(operatingSystemBean)
            operatingSystemBeanClass.getDeclaredMethod(name)
        } catch (e: ClassCastException) {
            null
        } catch (e: NoSuchMethodException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    private fun getFirstClassFound(classNames: List<String>): Class<*>? {
        for (className in classNames) {
            try {
                return Class.forName(className)
            } catch (ignore: ClassNotFoundException) {
            }
        }
        return null
    }
}

// Contrary to the Spring Boot trace actuator Jooby does not collect (and keep) this information. We would have to build
// it ourselves. Instead just return a dummy message in the expected JSON structure.
class ActuatorTraceHandler : Route.Handler {
    private data class TraceRequest(
        val timestamp: Long = 0, // unix epoch start
        val info: RequestInfo = RequestInfo())

    private data class RequestInfo(
        val method: String = "GET",
        val path: String = "/feature-not-supported-by-this-module",
        val headers: Headers = Headers()
    )

    private data class Headers (
        val request: Map<String, String> = mapOf(),
        val response: Map<String, String> = mapOf(Pair("status", "200"))
    )

    override fun handle(req: Request?, rsp: Response?) {
        rsp!!.send(Results.json(listOf(TraceRequest())).header("Cache-Control", "no-cache,must-revalidate,no-store"))
    }
}
