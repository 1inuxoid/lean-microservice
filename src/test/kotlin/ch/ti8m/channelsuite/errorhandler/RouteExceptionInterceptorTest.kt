package ch.ti8m.channelsuite.errorhandler

import ch.ti8m.channelsuite.xservice.ServiceMain
import cucumber.api.java.After
import cucumber.api.java.Before
import io.restassured.RestAssured
import io.restassured.http.Header
import io.restassured.http.Headers
import org.hamcrest.Matchers
import org.jooby.Err
import org.jooby.MediaType
import org.jooby.Status
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RouteExceptionInterceptorTest {

    private val app = ServiceMain()

    @Before
    @BeforeAll
    fun startService() {
        app.get("/illegalArgumentException") {
            throw IllegalArgumentException("illegal argument")
        }
        app.get("/clientBusinessError") {
            throw BusinessError()
        }
        app.get("/nestedRuntimeException") {
            throw RuntimeException("outer message", RuntimeException("inner message"))
        }
        app.get("/error4xx") {
            throw Err(Status.CONFLICT, "there is a conflict!")
        }
        app.get("/error5xx") {
            throw Err(Status.INSUFFICIENT_STORAGE, "no more space in space!")
        }
        app.start("server.join=false", "junit")

    }

    @After
    @AfterAll
    fun stopService() {
        app.stop()
    }

    @Test
    fun illegalArgumentException() {
        RestAssured.given().contentType("application/json")
                .`when`()
                .get("/illegalArgumentException")
                .then()
                .statusCode(Status.BAD_REQUEST.value())
                .body("code", Matchers.notNullValue()).and()
                .body("code.code", Matchers.equalTo(ErrorCode.DEFAULT_ERROR_CODE)).and()
                .body("code.serviceId", Matchers.equalTo("hello-jooby")).and()
                .body("errorType", Matchers.equalTo(ErrorTypeEnum.CLIENTERROR.value)).and()
                .body("message", Matchers.equalTo("illegal argument")).and()
                .body("timestamp", Matchers.notNullValue())
    }

    @Test
    fun clientBusinessErrorShouldBeNicelyRenderable() {
        RestAssured.given().contentType("application/json")
                .`when`()
                .get("/clientBusinessError")
                .then()
                .statusCode(Status.BAD_REQUEST.value())
                .body("code", Matchers.notNullValue()).and()
                .body("code.code", Matchers.equalTo("some.msg.code")).and()
                .body("code.serviceId", Matchers.equalTo("hello-jooby")).and()
                .body("errorType", Matchers.equalTo(ErrorTypeEnum.CLIENTERROR.value)).and()
                .body("message", Matchers.equalTo("description about what went wrong")).and()
                .body("argumentsRejected", Matchers.iterableWithSize<Any>(1)).and()
                .body("argumentsRejected[0].path",  Matchers.equalTo("field.name")).and()
                .body("argumentsRejected[0].argumentErrorCode",  Matchers.equalTo(ErrorCode.DEFAULT_ERROR_CODE)).and()
                .body("argumentsRejected[0].message",  Matchers.equalTo("Das Format ist Falsch"))

    }

    @Test
    fun nestedRuntimeExceptionShouldReturnRootCause() {
        RestAssured.given().contentType("application/json")
                .`when`()
                .get("/nestedRuntimeException")
                .then()
                .statusCode(Status.SERVER_ERROR.value())
                .body("code", Matchers.notNullValue()).and()
                .body("code.serviceId", Matchers.equalTo("hello-jooby")).and()
                .body("message", Matchers.equalTo("inner message")).and()
                .body("stacktraces", Matchers.iterableWithSize<Any>(1)).and()
                .body("stacktraces[0].trace", Matchers.iterableWithSize<Any>(32))
    }

    @Test
    fun error4xx() {

        RestAssured.given().contentType("application/json")
                .`when`()
                .get("/error4xx")
                .then()
                .statusCode(Status.CONFLICT.value())
                .body("message", Matchers.containsString("there is a conflict!")).and()
                .body("errorType", Matchers.equalTo(ErrorTypeEnum.CLIENTERROR.value)).and()
                .body("timestamp", Matchers.notNullValue()).and()
                .body("code.code", Matchers.equalTo(ErrorCode.DEFAULT_ERROR_CODE)).and()
                .body("code.serviceId", Matchers.equalTo("hello-jooby")).and()
                .body("argumentsRejected", Matchers.iterableWithSize<Any>(0)).and()


    }

    @Test
    fun error5xx() {
        RestAssured.given().contentType("application/json")
                .`when`()
                .get("/error5xx")
                .then()
                .statusCode(Status.INSUFFICIENT_STORAGE.value())
                .body("message", Matchers.containsString("no more space in space!")).and()
                .body("errorType", Matchers.equalTo(ErrorTypeEnum.INTERNALERROR.value)).and()
                .body("timestamp", Matchers.notNullValue()).and()
                .body("code.code", Matchers.equalTo(ErrorCode.DEFAULT_ERROR_CODE)).and()
                .body("code.serviceId", Matchers.equalTo("hello-jooby"))


    }


    class BusinessError: ClientErrorException() {
        override val code
            get() = "some.msg.code"
        override val errorType
            get() = ErrorType.ILLEGAL_DATA
        override val message: String?
            get() = "description about what went wrong"

        override val violations
            get() = Collections.singletonMap("field.name", "Das Format ist Falsch")

    }

}
