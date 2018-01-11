package ch.ti8m.channelsuite.config

import ch.ti8m.channelsuite.xservice.ServiceMain
import ch.ti8m.channelsuite.xservice.UserCreationRequest
import io.restassured.RestAssured.`when`
import io.restassured.RestAssured.given
import io.restassured.http.Header
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Sample for an integration test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiTest  {
    val app = ServiceMain()
    @BeforeAll fun startService() {app.start("server.join=false", "junit")}
    @AfterAll fun stopService() {app.stop()}

    val user = UserCreationRequest("hugo", "Hugo", "Hurtig")

    val authHeaderForAdmin = Header("AL_IDENTITY", "admin|[everything,admin]")
    val authHeaderForUnauthorisedUser = Header("AL_IDENTITY", "noob|[stuff]")


    @Test
    fun posting_users_works() {

        posting_a_user_returns_status_created()

        posted_users_can_be_retrieved()

        posted_users_can_be_retrieved_by_id()
    }

    @Test
    fun posting_users_requires_permission_to_create_them() {
        given()
                .body(user).contentType("application/json")
                .header(authHeaderForUnauthorisedUser)
                .`when`()
                .post("/users").
                then()
                .statusCode(403)
    }

    @Test
    fun posting_anyomously_results_in_401() {
        given()
                .body(user).contentType("application/json")
                .`when`()
                .post("/users").
                then()
                .statusCode(401)
    }

    private fun posting_a_user_returns_status_created() {
        given()
                .body(user).contentType("application/json")
                .header(authHeaderForAdmin)
                .`when`()
                .post("/users").
                then()
                .statusCode(201)
    }

    fun posted_users_can_be_retrieved() {
        `when`().get("/users")
                .then()
                .statusCode(200)
                .body("[0].username", equalTo("hugo"))
    }

    fun posted_users_can_be_retrieved_by_id() {
        `when`().get("/users/1")
                .then()
                .statusCode(200)
                .body("username", equalTo("hugo"))
    }
}
