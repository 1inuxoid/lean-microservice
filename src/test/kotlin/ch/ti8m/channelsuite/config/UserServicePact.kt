package ch.ti8m.channelsuite.config

import au.com.dius.pact.consumer.ConsumerPactTestMk2
import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.dsl.PactDslWithProvider
import au.com.dius.pact.model.RequestResponsePact
import io.restassured.RestAssured.given
import org.hamcrest.Matchers.equalTo

class UserServicePact : ConsumerPactTestMk2() {
    override fun runTest(mockServer: MockServer?) {
        given().`when`().get(mockServer!!.getUrl()+"/users/hugo").then().statusCode(200)
                .body("userId", equalTo("hugo"))

    }

    override fun createPact(builder: PactDslWithProvider?): RequestResponsePact =
        builder!!.uponReceiving("Obtain details for a user by user-id")
                .path("/users/hugo").method("GET")
                .willRespondWith().status(200)
                .headers(mapOf("Content-type" to "application/json"))
                .body("""
                    {
                       "userId" : "hugo",
                       "firstName" : "Hugo",
                       "lastName" : "Hurtig"
                    }
                """.trimIndent()).toPact()


    override fun providerName() = "user-service"
    override fun consumerName() = "x-service"

}