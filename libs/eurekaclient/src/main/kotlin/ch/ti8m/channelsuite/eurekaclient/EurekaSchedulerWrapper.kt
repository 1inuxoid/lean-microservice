package ch.ti8m.channelsuite.eurekaclient

import ch.ti8m.channelsuite.log.LogFactory
import ch.ti8m.channelsuite.security.TokenTransportConfig
import ch.ti8m.channelsuite.security.api.RequestInfo
import ch.ti8m.channelsuite.security.api.RequestInfoImpl
import ch.ti8m.channelsuite.security.api.SecurityTokenProperties
import ch.ti8m.channelsuite.security.api.UserInfoFactory
import ch.ti8m.channelsuite.security.securityTemplate
import ch.ti8m.channelsuite.security.tokenAdder
import ch.ti8m.channelsuite.security.tokenSupportFactory
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
import ch.ti8m.channelsuite.serviceregistry.client.utils.HostNameUtil
import ch.ti8m.channelsuite.serviceregistry.client.utils.SimpleUrlConnectionUtil
import org.slf4j.MDC
import java.net.UnknownHostException
import java.util.*

/**
 * Defines the configuration of the Eureka client.
 */
data class EurekaConfig(val client: Client, val instance: Instance)

data class TechUserConfig(val id: String, val loginId: String, val roles: Set<String>, val tenant: String)

data class Instance(val serviceName: String, val hostName: String?, val port: Int = 8080,
                    val serviceContext: String = "",
                    val zone: String,
                    val metadata: Map<String, String>)

data class Client(var preferSameZone: Boolean = true,
                  var serviceRegistryUrl: Map<String, List<String>>,
                  var heartbeatIntervalInMs: Int,
                  var fetchRegistryIntervalInMs: Int,
                  var enabled: Boolean = true
)

/**
 * Wraps the active component (the scheduler) in the Eureka client library.
 * The scheduler
 *  * announces this instances state to the central registry,
 *  * confirms its validity (by sending a heartbeat) and
 *  * maintains a local copy of the central registry.
 *
 */
class EurekaSchedulerWrapper(private val appName: String, val config: EurekaConfig, private val techUserConfig: TechUserConfig,
                             tokenConfig: SecurityTokenProperties, transportConfig: TokenTransportConfig) {

    private val userInfoTokenConverter = tokenSupportFactory(tokenConfig).tokenConverter()
    private val securityContextTemplate = securityTemplate(tokenConfig, appName)

    private val defaultServiceInstance = createDefaultInstance()
    private val eurekaServiceURLProvider = createEurekaServiceURLProvider()

    private fun createEurekaServiceURLProvider(): EurekaServiceURLProvider {
        val eurekaServiceUrls = config.client.serviceRegistryUrl
        return EurekaServiceURLProvider(eurekaServiceUrls, config.client.preferSameZone, config.instance.zone)
    }

    //TODO wire in jooby/dropwizard health-checks here.
    private val eurekaRestClient = EurekaRestClient(eurekaServiceURLProvider,
            SimpleUrlConnectionUtil(tokenAdder(tokenConfig, transportConfig), 10000, 10000))

    private val registryEventCallback = NoOPRegistryEventCallback()
    private val serviceRegistry = ServiceRegistry(registryEventCallback)
    private val fetchRegistryTask = object : FetchRegistryTask(eurekaRestClient, serviceRegistry, appName) {
        override fun doExecute() {
            runInTechUserContext { super.doExecute() }
        }
    }
    private val sendHeartbeatTask = object : SendHeartbeatTask(eurekaRestClient, defaultServiceInstance, appName) {
        override fun doExecute() {
            runInTechUserContext { super.doExecute() }
        }
    }

    private val registryScheduler = ServiceRegistryScheduler(defaultServiceInstance, fetchRegistryTask, sendHeartbeatTask, eurekaRestClient, config.client.heartbeatIntervalInMs, config.client.fetchRegistryIntervalInMs)

    val registryClient = if (config.instance.zone == ServiceRegistryClientConstants.DEFAULT_ZONE)
        SimpleServiceRegistryClient(serviceRegistry)
    else
        ZoneAwareServiceRegistryClient(serviceRegistry, config.instance.zone)

    private fun createDefaultInstance(): ServiceInstance {
        val hostname = discoverHostName(false)
        return with(config.instance) {
            val instanceId = with(config) { "$hostname:${instance.serviceName}:${instance.port}" }

            DefaultServiceInstance(instanceId, serviceName, hostname,
                    port, false, UP,
                    serviceContext, metadata.map { it.key to it.value }.toMap(), hostname, ArrayList())
        }
    }

    fun start() {
        if (this.config.client.enabled) {
            runInTechUserContext { registryScheduler.start() }
        }
    }

    fun stop() {
        if (this.config.client.enabled) {
            runInTechUserContext { registryScheduler.shutdown() }
        }
    }

    private fun runInTechUserContext(someFunction: () -> Unit) {
        setupTechUserContext()
        someFunction()
        teardownTechUserContext()
    }

    private fun teardownTechUserContext() {
        securityContextTemplate.tearDownSecurityContext()
    }

    private fun setupTechUserContext() {
        MDC.put("MODULE-ID", config.instance.serviceName)
        val userInfo = UserInfoFactory.userInfoFor(techUserConfig.loginId, techUserConfig.id, techUserConfig.roles, techUserConfig.tenant)
        val requestInfo = technicalCallRequestInfo()
        val securityToken = userInfoTokenConverter.createTokenForUser(userInfo, requestInfo)

        securityContextTemplate.setupSecurityContext(appName, userInfo, requestInfo, securityToken)
    }

    private fun technicalCallRequestInfo(): RequestInfo {
        val requestIdForTechCall = "technical-background-proc-" + UUID.randomUUID()
        val sessionIdForTechCall = RequestInfoImpl.EMPTY.frontSessionId

        MDC.put("REQUEST-ID", requestIdForTechCall)
        MDC.put("SESSION-ID", sessionIdForTechCall)
        return RequestInfoImpl(sessionIdForTechCall, requestIdForTechCall)
    }

    private fun discoverHostName(isPreferIpAddress: Boolean): String {
        return config.instance.hostName ?: return try {
            if (isPreferIpAddress) {
                HostNameUtil.getLocalHostAddress()
            } else {
                HostNameUtil.getLocalHostName(2)
            }
        } catch (e: UnknownHostException) {
            "localhost"
        }
    }
}
    class NoOPRegistryEventCallback : RegistryEventCallback {
        private val logger = object : LogFactory {}.classLogger()

        override fun serviceRegistryUpdate(availableServices: MutableList<String>?) {
            logger.debug("serviceRegistryUpdate triggered")
        }

    }
