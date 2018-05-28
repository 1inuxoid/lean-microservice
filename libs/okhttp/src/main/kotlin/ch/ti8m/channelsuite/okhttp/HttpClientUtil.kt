package ch.ti8m.channelsuite.okhttp

import ch.ti8m.channelsuite.security.TokenHeaderProvider
import ch.ti8m.channelsuite.serviceregistry.client.api.ServiceInstance
import ch.ti8m.channelsuite.serviceregistry.client.api.ServiceRegistryClient
import com.fasterxml.jackson.databind.ObjectMapper
import com.typesafe.config.Config
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

/**
 * Convenient use of okhttp with channel suite stuff
 *
 * @author marcus
 * @since  20.01.18
 */


val http = OkHttpClient()

fun org.jooby.Request.requestToCSSvc(
        svcName: String,
        method: String = "get",
        body: RequestBody? = null,
        configCode: HttpUrl.Builder.() -> Unit
) = requestPassingOnCallContext(providerUrl(svcName), method, body, configCode)

val mockUrlConfigKey = "providerMockUrl"

//TODO mock url needs to depend on service to support multiple pact-mock instances
fun org.jooby.Request.providerUrl(service: String): HttpUrl.Builder {
    val cfg = require(Config::class.java)

    if (cfg.hasPath(mockUrlConfigKey)) {
        val mockUrl = cfg.getString(mockUrlConfigKey) + "/" + service
        return HttpUrl.parse(mockUrl)?.newBuilder() ?: throw IllegalArgumentException("Could not parse $mockUrl")
    }

    val instance = require(ServiceRegistryClient::class.java).getNextServiceInstance(service)

    val urlBuilder = HttpUrl.Builder()
    return urlBuilderFromServiceInstance(urlBuilder, instance, service)
}

private fun urlBuilderFromServiceInstance(urlBuilder: HttpUrl.Builder, instance: ServiceInstance, service: String) =
        urlBuilder.scheme("http").port(instance.port).host(instance.hostname)
                .addPathSegment(service)

/**
 * helps building a request that passes on the security context.
 */
fun org.jooby.Request.requestPassingOnCallContext(urlBuilder: HttpUrl.Builder,
                                                  method: String = "get",
                                                  body: RequestBody? = null,
                                                  configCode: HttpUrl.Builder.() -> Unit): Request {
    val tokenHeaderProvider = require(TokenHeaderProvider::class.java)
    val tokenHeader = tokenHeaderProvider()

    val builder = Request.Builder()
            .addHeader(tokenHeader.name, tokenHeader.token)

    urlBuilder.configCode()
    builder.method(method, body)
    builder.url(urlBuilder.build())

    return builder.build()
}

inline fun <reified T> org.jooby.Request.jsonResultOf(r: Request): T {
    val mapper = require(ObjectMapper::class.java)
    val result = http.newCall(r).execute()
    return mapper.readValue(result.body()!!.string(), T::class.java)
}