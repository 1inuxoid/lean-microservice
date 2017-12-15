package ch.ti8m.channelsuite.eurekaclient

import ch.ti8m.channelsuite.serviceregistry.client.DefaultServiceInstance
import ch.ti8m.channelsuite.serviceregistry.client.ServiceRegistry
import ch.ti8m.channelsuite.serviceregistry.client.api.InstanceStatus.UP
import ch.ti8m.channelsuite.serviceregistry.client.api.RegistryEventCallback
import ch.ti8m.channelsuite.serviceregistry.client.api.ServiceInstance
import ch.ti8m.channelsuite.serviceregistry.client.eureka.EurekaRestClient
import ch.ti8m.channelsuite.serviceregistry.client.eureka.EurekaServiceURLProvider
import ch.ti8m.channelsuite.serviceregistry.client.schedule.FetchRegistryTask
import ch.ti8m.channelsuite.serviceregistry.client.schedule.SendHeartbeatTask
import ch.ti8m.channelsuite.serviceregistry.client.schedule.ServiceRegistryScheduler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Integer.parseInt
import java.util.HashMap
import kotlin.collections.ArrayList

class EurekaSchedulerWrapper(val config: EurekaConfig) {

    private val instanceId = with(config){ "${serviceIp}:${serviceName}:${servicePort}"}

    private val defaultServiceInstance = createDefaultInstance()
    private val eurekaServiceURLProvider = createEurekaServiceURLProvider()

    private fun createEurekaServiceURLProvider(): EurekaServiceURLProvider {
        val eurekaServiceUrls = HashMap<String, List<String>>()
        val urls = java.util.ArrayList<String>()
        urls.add(config.serviceRegistryUrl)
        eurekaServiceUrls.put("default", urls)

        return EurekaServiceURLProvider(eurekaServiceUrls, true, "default")
    }

    private val eurekaRestClient = EurekaRestClient(eurekaServiceURLProvider)

    private val registryEventCallback = NoOPRegistryEventCallback()
    private val serviceRegistry = ServiceRegistry(registryEventCallback)
    private val fetchRegistryTask = FetchRegistryTask(eurekaRestClient, serviceRegistry, config.serviceName)
    private val sendHeartbeatTask = SendHeartbeatTask(eurekaRestClient, defaultServiceInstance, config.serviceName)
    private val registryScheduler = ServiceRegistryScheduler(defaultServiceInstance, fetchRegistryTask, sendHeartbeatTask, eurekaRestClient, 1000, 100000)


    private fun createDefaultInstance(): ServiceInstance {
        return with(config){
            val metaData = hashMapOf(
                    "hasGUI" to "true",
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
    private val logger = LoggerFactory.getLogger("NoOPRegistryEventCallback") as Logger

    override fun serviceRegistryUpdate(availableServices: MutableList<String>?) {
        logger.info("serviceRegistryUpdate triggered")
    }

}