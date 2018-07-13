package ch.ti8m.channelsuite.errorhandler

import ch.ti8m.channelsuite.security.api.RequestSecurityContext
import com.google.inject.Inject
import com.typesafe.config.Config
import org.apache.commons.lang3.exception.ExceptionUtils
import org.slf4j.LoggerFactory.getLogger
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.time.OffsetDateTime
import java.util.*
import java.util.regex.Pattern

/**
 * Converts Exceptions to the channel suite error model (i.e. [GeneralError]s et.al.).
 */
@Suppress("UNCHECKED_CAST")
class GeneralErrorConverter @Inject constructor(config: Config) {

    private val appName: String = config.getString("application.name")

    fun <T : GeneralError> convertToCsErrorModel(exception: Exception, errorType: ErrorType): T {

        val mappedError: GeneralError

        val rootCause = rootCauseOrIdentity(exception)
        val message = rootCause.message ?: ""
        val errorCode = ErrorCode(appName, ErrorCode.DEFAULT_ERROR_CODE)
        val requestInfo = RequestSecurityContext.getRequestInfo()

        when {
            CRITICAL_ERRORS.contains(errorType) -> {
                mappedError = ch.ti8m.channelsuite.errorhandler.InternalError(
                        ErrorTypeEnum.INTERNALERROR,
                        errorCode,
                        message,
                        OffsetDateTime.now()
                )
            }
            CLIENT_ERRORS.contains(errorType) -> {
                mappedError = ClientError(ErrorTypeEnum.CLIENTERROR,
                        errorCode,
                        message,
                        OffsetDateTime.now()
                )
            }
            else -> {
                mappedError = ServiceError(ErrorTypeEnum.SERVICEERROR,
                        errorCode,
                        message,
                        OffsetDateTime.now()
                )
            }
        }

        val stackTrace = mapStackTrace(exception)
        mappedError.stacktraces = listOfNotNull(stackTrace)
        mappedError.requestId = requestInfo?.frontRequestId
        mappedError.sessionId = requestInfo?.frontSessionId

        return mappedError as T
    }

    fun <T : GeneralError> convertToCsErrorModel(e: Exception): T {
        return convertToCsErrorModel(e, ErrorType.UNDEFINED)
    }

    /**
     * Maps a Throwable to a single StackTrace element.
     *
     * @param exception the throwable.
     * @return stacktrace element or null.
     */
    private fun mapStackTrace(exception: Throwable): StackTrace? {

        try {
            StringWriter().use { sw ->
                PrintWriter(sw).use { pw ->
                    exception.printStackTrace(pw)
                    val stackTraceByLine = NEW_LINE_PATTERN.split(sw.toString())

                    if (stackTraceByLine.isNotEmpty()) {

                        // Take first line as message from exception
                        return StackTrace(stackTraceByLine[0], Arrays.asList(*stackTraceByLine))
                    }

                }
            }
        } catch (e: IOException) {
            logger.warn("Could not map Stacktrace: {}", e.message)
        }

        return null
    }

    private fun rootCauseOrIdentity(e: Exception): Throwable {
        return ExceptionUtils.getRootCause(e) ?: return e
    }

    companion object {

        private val logger = getLogger(GeneralErrorConverter::class.java)

        private val CRITICAL_ERRORS = EnumSet.of(ErrorType.UNDEFINED, ErrorType.TRANSACTION_ERROR)
        private val CLIENT_ERRORS = EnumSet.of(ErrorType.CLIENT_ERROR, ErrorType.ILLEGAL_DATA, ErrorType.ACCESS_ERROR)
        private val NEW_LINE_PATTERN = Pattern.compile("\\r?\\n")
    }
}
