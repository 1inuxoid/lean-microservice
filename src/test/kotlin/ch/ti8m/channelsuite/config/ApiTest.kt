package ch.ti8m.channelsuite.config

import ch.ti8m.channelsuite.xservice.ServiceMain
import ch.ti8m.channelsuite.xservice.UserCreationRequest
import cucumber.api.java.After
import cucumber.api.java.Before
import cucumber.api.java.en.Given
import cucumber.api.java.en.Then
import cucumber.api.java.en.When
import io.restassured.RestAssured.`when`
import io.restassured.RestAssured.given
import io.restassured.http.Header
import org.hamcrest.Matchers.*
import org.junit.Ignore
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

    @Before
    @BeforeAll fun startService() {app.start("server.join=false", "junit")}

    @After
    @AfterAll fun stopService() {app.stop()}

    val user = UserCreationRequest("hugo", "Hugo", "Hurtig")
    val userNotCreated = UserCreationRequest("claus", "Claus", "Tralala")

    lateinit var authHeaderForAdmin: Header
    lateinit var authHeaderForUnauthorisedUser: Header


    @Given("^a logged in and permitted user$")
    fun setupAdmin() {
        authHeaderForAdmin = Header("AL_IDENTITY", "admin|[everything,admin]")
    }

    @Given("^a not authorized user$")
    fun setupUnauthorizedUser() {
        authHeaderForUnauthorisedUser = Header("AL_IDENTITY", "noob|[stuff]")
    }

    @Test
    fun posting_users_works() {

        posting_a_user_returns_status_created()

        posted_users_can_be_retrieved()

        posted_users_can_be_retrieved_by_id()
    }

    @Test
    @When("^he tries to add a user$")
    fun posting_users_requires_permission_to_create_them() {
        // given
        setupUnauthorizedUser()


        given()
                .body(userNotCreated).contentType("application/json")
                .header(authHeaderForUnauthorisedUser)
                .`when`()
                .post("/users").
                then()
                .statusCode(403)
    }

    @Test
    fun posting_anonymously_results_in_401() {
        given()
                .body(userNotCreated).contentType("application/json")
                .`when`()
                .post("/users").
                then()
                .statusCode(401)
    }

    @When("^he adds a new user$")
    fun posting_a_user_returns_status_created() {
        setupAdmin()

        given()
                .body(user).contentType("application/json")
                .header(authHeaderForAdmin)
                .`when`()
                .post("/users").
                then()
                .statusCode(201)
    }

    @Then("^the new user is not there$")
    fun check_claus_user_not_created() {
        `when`().get("/users")
                .then()
                .statusCode(200)
                .body(not(hasItem(userNotCreated)))
    }

    @Then("^he will receive a list of users$")
    fun posted_users_can_be_retrieved() {
        `when`().get("/users")
                .then()
                .statusCode(200)
                .body("[0].username", equalTo("hugo"))
    }

    @Then("^he can be received by id$")
    fun posted_users_can_be_retrieved_by_id() {
        `when`().get("/users/1")
                .then()
                .statusCode(200)
                .body("username", equalTo("hugo"))
    }
}
