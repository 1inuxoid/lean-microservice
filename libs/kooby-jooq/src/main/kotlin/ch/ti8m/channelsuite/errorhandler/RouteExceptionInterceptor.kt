package ch.ti8m.channelsuite.errorhandler

import ch.ti8m.channelsuite.log.LogFactory
import com.google.inject.Binder
import com.google.inject.Scopes
import com.typesafe.config.Config
import org.jooby.*
import org.jooby.Status.BAD_REQUEST

val logger = object : LogFactory {}.packageLogger()

class RouteExceptionInterceptor : Jooby.Module {
    private var errorMappingForAllMediaTypes = false

    override fun configure(env: Env?, conf: Config?, binder: Binder?) {
        binder!!.bind(GeneralErrorConverter::class.java).`in`(Scopes.SINGLETON)
        errorMappingForAllMediaTypes = conf?.getBoolean("channelsuite.errorMappingForAllMediaTypes") ?: false

        with(env!!.router()) {

            err(IllegalArgumentException::class.java) { request, response, error ->
                response.error(request.require(GeneralErrorConverter::class.java).convertToCsErrorModel(error, ErrorType.ILLEGAL_DATA), BAD_REQUEST)
            }

            err(ClientErrorException::class.java) { request, response, error ->
                if (error.cause != null && error.cause is ClientErrorException) {
                    val clientErrorException = error.cause as ClientErrorException
                    val clientError: ClientError = request.require(GeneralErrorConverter::class.java).convertToCsErrorModel(clientErrorException, clientErrorException.errorType)

                    clientError.argumentsRejected = extractViolations(clientErrorException, clientError.code)

                    if (clientErrorException.code != null) {
                        clientError.code.code = clientErrorException.code!!
                    }
                    response.error(clientError, BAD_REQUEST)

                } else {
                    response.error(request.require(GeneralErrorConverter::class.java).convertToCsErrorModel(error), BAD_REQUEST)
                }
            }

            err { request, response, error ->
                // handle non-HTML requests (e.g. JSON) manually and let others bubble up for Jooby to handle
                if (!request.`is`(MediaType.html) || errorMappingForAllMediaTypes) {
                    val errorCode = error.statusCode()
                    if ((400..499).contains(errorCode)) {
                        handleClientError(request, response, error)
                    } else if (errorCode >= 500) {
                        handleServerError(request, response, error)
                    }
                }
            }

            err(RuntimeException::class.java) { request, response, error ->
                response.error(request.require(GeneralErrorConverter::class.java).convertToCsErrorModel(error), Status.SERVER_ERROR)
            }
        }
    }

    private fun handleServerError(request: Request, response: Response, error: Err) =
            response.error(request.require(GeneralErrorConverter::class.java).convertToCsErrorModel(error), Status.valueOf(error.statusCode()))

    private fun handleClientError(request: Request, response: Response, error: Err) {
        val clientError: ClientError = request.require(GeneralErrorConverter::class.java).convertToCsErrorModel(error, ErrorType.CLIENT_ERROR)
        response.error(clientError, Status.valueOf(error.statusCode()))
    }

    private fun extractViolations(clientErrorException: ClientErrorException, errorCode: ErrorCode) =
            clientErrorException.violations.entries.map { it -> ArgumentIdentifier(it.key, errorCode.code, it.value) }.toList()

}

private fun Response.error(e: GeneralError, status: Status) {
    logger.warn("Exception of code ${e.code.code} for service ${e.code.serviceId} - treating as http status ${status.value()}")
    send(Results.json(e).status(status))
}
