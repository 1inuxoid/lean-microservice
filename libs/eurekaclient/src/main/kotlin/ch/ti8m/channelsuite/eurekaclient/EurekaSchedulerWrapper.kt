package ch.ti8m.channelsuite.eurekaclient

import ch.ti8m.channelsuite.log.LogFactory
import ch.ti8m.channelsuite.serviceregistry.client.DefaultServiceInstance
import ch.ti8m.channelsuite.serviceregistry.client.ServiceRegistry
import ch.ti8m.channelsuite.serviceregistry.client.SimpleServiceRegistryClient
import ch.ti8m.channelsuite.serviceregistry.client.ZoneAwareServiceRegistryClient
import ch.ti8m.channelsuite.serviceregistry.client.api.InstanceStatus.UP
import ch.ti8m.channelsuite.serviceregistry.client.api.RegistryEventCallback
import ch.ti8m.channelsuite.serviceregistry.client.api.ServiceInstance
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
data class EurekaConfig(val serviceRegistryUrl: String,
                        val serviceName: String,
                        val serviceContext: String,
                        val serviceIp: String,
                        val servicePort: String,
                        val hasGui: Boolean,
                        val zone:String)

/**
 * Wraps the active component (the scheduler) in the Eureka client library.
 * The scheduler
 *  * announces this instances state to the central registry,
 *  * confirms its validity (by sending a heartbeat) and
 *  * maintains a local copy of the central registry.
 *
 */
class EurekaSchedulerWrapper(val config: EurekaConfig) {

    private val instanceId = with(config){ "${serviceIp}:${serviceName}:${servicePort}"}

    private val defaultServiceInstance = createDefaultInstance()
    private val eurekaServiceURLProvider = createEurekaServiceURLProvider()

    private fun createEurekaServiceURLProvider(): EurekaServiceURLProvider {
        val eurekaServiceUrls = HashMap<String, List<String>>()
        val urls = listOf(config.serviceRegistryUrl)

        eurekaServiceUrls.put("default", urls)

        return EurekaServiceURLProvider(eurekaServiceUrls, true, "default")
    }

    //TODO wire in jooby/dropwizard health-checks here.
    private val eurekaRestClient = EurekaRestClient(eurekaServiceURLProvider)

    private val registryEventCallback = NoOPRegistryEventCallback()
    private val serviceRegistry = ServiceRegistry(registryEventCallback)
    private val fetchRegistryTask = FetchRegistryTask(eurekaRestClient, serviceRegistry, config.serviceName)
    private val sendHeartbeatTask = SendHeartbeatTask(eurekaRestClient, defaultServiceInstance, config.serviceName)

    private val registryScheduler = ServiceRegistryScheduler(defaultServiceInstance, fetchRegistryTask, sendHeartbeatTask, eurekaRestClient, 1000, 100000)

    val registryClient = if (config.zone.isNullOrBlank())
                            SimpleServiceRegistryClient(serviceRegistry)
                         else
                            ZoneAwareServiceRegistryClient(serviceRegistry, config.zone)

    private fun createDefaultInstance(): ServiceInstance {
        return with(config){
            val metaData = hashMapOf(
                    "hasGUI" to  hasGui.toString(),
                    "serviceContext" to serviceContext,
                    "name" to serviceName
            )
            DefaultServiceInstance(instanceId, serviceName, serviceIp,
                    parseInt(servicePort), false, UP,
                    serviceContext, metaData, serviceIp, ArrayList())
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