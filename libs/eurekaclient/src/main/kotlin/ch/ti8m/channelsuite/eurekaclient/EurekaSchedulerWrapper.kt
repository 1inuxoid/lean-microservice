package ch.ti8m.channelsuite.eurekaclient

import ch.ti8m.channelsuite.log.LogFactory
import ch.ti8m.channelsuite.serviceregistry.client.DefaultServiceInstance
import ch.ti8m.channelsuite.serviceregistry.client.ServiceRegistry
import ch.ti8m.channelsuite.serviceregistry.client.SimpleServiceRegistryClient
import ch.ti8m.channelsuite.serviceregistry.client.ZoneAwareServiceRegistryClient
import ch.ti8m.channelsuite.serviceregistry.client.api.InstanceStatus.UP
import ch.ti8m.channelsuite.serviceregistry.client.api.RegistryEventCallback
import ch.ti8m.channelsuite.serviceregistry.client.api.ServiceInstance
import ch.ti8m.channelsuite.serviceregistry.client.config.ServiceRegistryClientConstants
import ch.ti8m.channelsuite.serviceregistry.client.eureka.EurekaRestClient
import ch.ti8m.channelsuite.serviceregistry.client.eureka.EurekaServiceURLProvider
import ch.ti8m.channelsuite.serviceregistry.client.schedule.FetchRegistryTask
import ch.ti8m.channelsuite.serviceregistry.client.schedule.SendHeartbeatTask
import ch.ti8m.channelsuite.serviceregistry.client.schedule.ServiceRegistryScheduler
import java.lang.Integer.parseInt
import java.util.HashMap
import kotlin.collections.ArrayList

/**
 * Defines the configuration of the Eureka client.
 */
data class EurekaConfig(val client:Client, val instance: Instance)

data class Instance(val serviceName: String, val hostName: String, val port: Int,
                    val serviceContext: String = "",
                    val zone: String,
                    val metadata: Map<String, Any>)

data class Client(var preferSameZone: Boolean = true,
                  var serviceRegistryUrl: Map<String, List<String>>,
                  var heartbeatIntervalInMs: Int ,
                  var fetchRegistryIntervalInMs: Int
)
/**
 * Wraps the active component (the scheduler) in the Eureka client library.
 * The scheduler
 *  * announces this instances state to the central registry,
 *  * confirms its validity (by sending a heartbeat) and
 *  * maintains a local copy of the central registry.
 *
 */
class EurekaSchedulerWrapper(val config: EurekaConfig) {

    private val instanceId = with(config){ "${instance.hostName}:${instance.serviceName}:${instance.port}"}

    private val defaultServiceInstance = createDefaultInstance()
    private val eurekaServiceURLProvider = createEurekaServiceURLProvider()

    private fun createEurekaServiceURLProvider(): EurekaServiceURLProvider {
        val eurekaServiceUrls = config.client.serviceRegistryUrl
        return EurekaServiceURLProvider(eurekaServiceUrls, config.client.preferSameZone, config.instance.zone)
    }

    //TODO wire in jooby/dropwizard health-checks here.
    private val eurekaRestClient = EurekaRestClient(eurekaServiceURLProvider)

    private val registryEventCallback = NoOPRegistryEventCallback()
    private val serviceRegistry = ServiceRegistry(registryEventCallback)
    private val fetchRegistryTask = FetchRegistryTask(eurekaRestClient, serviceRegistry, config.instance.serviceName)
    private val sendHeartbeatTask = SendHeartbeatTask(eurekaRestClient, defaultServiceInstance, config.instance.serviceName)

    private val registryScheduler = ServiceRegistryScheduler(defaultServiceInstance, fetchRegistryTask, sendHeartbeatTask, eurekaRestClient, config.client.heartbeatIntervalInMs, config.client.fetchRegistryIntervalInMs)

    val registryClient = if (config.instance.zone == ServiceRegistryClientConstants.DEFAULT_ZONE )
                            SimpleServiceRegistryClient(serviceRegistry)
                         else
                            ZoneAwareServiceRegistryClient(serviceRegistry, config.instance.zone)

    private fun createDefaultInstance(): ServiceInstance {
        return with(config.instance){
            DefaultServiceInstance(instanceId, serviceName,  hostName,
                    port, false, UP,
                    serviceContext,  metadata.map { it.key to it.value.toString()}.toMap(), hostName, ArrayList())
        }
    }

    fun start() {
        registryScheduler.start()
    }

    fun stop() {
        registryScheduler.shutdown()
    }
}

class NoOPRegistryEventCallback : RegistryEventCallback {
    private val logger = object : LogFactory {}.classLogger()

    override fun serviceRegistryUpdate(availableServices: MutableList<String>?) {
        logger.info("serviceRegistryUpdate triggered")
    }

}