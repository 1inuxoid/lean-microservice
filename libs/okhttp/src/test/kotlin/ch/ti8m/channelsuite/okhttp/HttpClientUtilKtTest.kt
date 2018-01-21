package ch.ti8m.channelsuite.okhttp

import ch.ti8m.channelsuite.security.TokenHeader
import ch.ti8m.channelsuite.security.TokenHeaderProvider
import ch.ti8m.channelsuite.serviceregistry.client.DefaultServiceInstance
import ch.ti8m.channelsuite.serviceregistry.client.api.InstanceStatus
import ch.ti8m.channelsuite.serviceregistry.client.api.ServiceRegistryClient
import com.typesafe.config.Config
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.jooby.Request
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/**
 * @author marcus
 * @since 21.01.18
 */
internal class HttpClientUtilKtTest {

    val tokenHeaderProvider = object:TokenHeaderProvider {
        override fun invoke() = TokenHeader("a","b")
    }

    val request = mock(Request::class.java)
    val config = mock(Config::class.java)
    val svcRegistry = mock(ServiceRegistryClient::class.java)

    @BeforeEach
    fun prepareMocks() {
        `when`(request.require(Config::class.java)).thenReturn(config)
        `when`(request.require(TokenHeaderProvider::class.java)).thenReturn(tokenHeaderProvider)
        `when`(request.require(ServiceRegistryClient::class.java)).thenReturn(svcRegistry)
    }

    fun handleMockRequest(handle: Request.() -> Unit) {
        request.handle()
    }

    @Test
    fun building_a_mock_url_works() {

        `when`(config.hasPath(mockUrlConfigKey)).thenReturn(true)
        `when`(config.getString(mockUrlConfigKey)).thenReturn("http://localhost:1234")

        handleMockRequest {
            val r = requestToCSSvc("providerSvc") {
                addPathSegments("api/bla")
            }

            assertThat( r.url().toString(), equalTo("http://localhost:1234/providerSvc/api/bla") )
        }
    }

    @Test
    fun using_a_eureka_url_works_and_token_gets_added() {

        `when`(config.hasPath(mockUrlConfigKey)).thenReturn(false)
        `when`(svcRegistry.getNextServiceInstance("providerSvc")).thenReturn(DefaultServiceInstance(
                "id","providerSvc","host.ch", 8080, false, InstanceStatus.UP, "providerSvc", emptyMap(), "",
                emptyList())
        )

        handleMockRequest {
            val r = requestToCSSvc("providerSvc") {
                addPathSegments("api/bla")
            }

            assertThat( r.url().toString(), equalTo("http://host.ch:8080/providerSvc/api/bla") )
            assertThat( r.headers("a")[0], equalTo("b"))
        }
    }
}