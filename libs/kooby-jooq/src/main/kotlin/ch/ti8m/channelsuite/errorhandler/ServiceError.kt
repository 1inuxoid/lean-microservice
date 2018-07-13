package ch.ti8m.channelsuite.errorhandler

import java.time.OffsetDateTime
import java.util.*

/**
 * Indicates that the service provider encountered a resource problem of sorts, like a database that was not
 * available or a file system that couln&#39;t take the shit anymore. This error does not necessarily indicate a
 * programming bug, but rather a robustness or availability problem if this sort of error happens to frequently. Upon
 * receiving such an error, the following steps have to be typically taken to handle the situation
 *
 *  1. Retry at a later time, or at a different service instance *OR* Abort the requested action.
 *  1. Close any opened resources.
 *  1. Free any allocated resources.
 *  1. Notify the user/client/consumer of the error.
 *  1. Log the error and raise an alarm to the ops guys.
 *
 */
class ServiceError(errorType: ErrorTypeEnum, code: ErrorCode, message: String, timestamp: OffsetDateTime) :
        GeneralError(errorType, code, message, timestamp) {
    /**
     * Optionally specify a waiting time for the client in milliseconds after which said client shall retry the same
     * request again. The client is of course not oblige to respect this value.
     * minimum: 0
     *
     * @return retryAfter
     */
    var retryAfter: Int? = null


    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val serviceError = other as ServiceError?
        return this.retryAfter == serviceError!!.retryAfter && super.equals(other)
    }

    override fun hashCode(): Int {
        return Objects.hash(retryAfter, super.hashCode())
    }


    override fun toString(): String {
        return """ServiceError{
                retryAfter=$retryAfter
                } ${super.toString()}"""
    }
}
