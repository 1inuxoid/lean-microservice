package ch.ti8m.channelsuite.errorhandler

import java.time.OffsetDateTime
import java.util.*

/**
 * Abstract General Error Base.
 */
abstract class GeneralError(
        /**
         * Get errorType
         *
         * @return errorType
         */
        var errorType: ErrorTypeEnum,

        /**
         * An Error Code. The code MAY also be used as message key for i18. Every service must implement and document
         * &amp; publish their error codes. dictionary.
         *
         * @return code
         */
        var code: ErrorCode,

        /**
         * Informative(!) description about what went wrong
         *
         * @return message
         */
        var message: String,

        /**
         * ISO8601 date-time that indicates the time at which this error was initially occured (was raised). It is
         * recommended to use zulu time (UTC), however it is also perfectly acceptable to work with a time zone offset
         * \&quot;(the UTC offset) e.g. 2017-10-30T16:05:05+01:00\&quot;
         *
         * @return timestamp
         */
        var timestamp: OffsetDateTime,
        /**
         * A request id that MUST get propagated downstream starting at the point of entry (typically the WAF) into our
         * little service universe.
         *
         * @return requestId
         */
        var requestId: String? = null,

        /**
         * Optionally a session id that SHOULD get propagated downstream starting at the point of entry (typically the
         * WAF) into our little service universe.
         *
         * @return sessionId
         */
        var sessionId: String? = null,

        /**
         * Optionally a stacktraces (top to root cause) may be included to ease debugging. Stacktrace propagation MUST not
         * be used if a service consumer is outside the company boundary or the boundaries of trusted partner companies.
         * However, a service may still propagate them as long as a WAF with suitable content filtering is present between
         * service provider and service consumer (e.g. a client facing we browser).
         *
         * @return stacktraces
         */
        var stacktraces: List<StackTrace>? = null
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val generalError = other as GeneralError?
        return (this.errorType == generalError!!.errorType
                && this.requestId == generalError.requestId
                && this.sessionId == generalError.sessionId
                && this.code == generalError.code
                && this.message == generalError.message
                && this.timestamp == generalError.timestamp
                && this.stacktraces == generalError.stacktraces)
    }

    override fun hashCode(): Int {
        return Objects.hash(errorType, requestId, sessionId, code, message, timestamp, stacktraces)
    }

    override fun toString(): String {
        return """GeneralError{
                errorType=$errorType
                , requestId='$requestId'
                , sessionId='$sessionId'
                , code=$code
                , message='$message'
                , timestamp=$timestamp
                , stacktraces=$stacktraces
                }"""
    }
}

