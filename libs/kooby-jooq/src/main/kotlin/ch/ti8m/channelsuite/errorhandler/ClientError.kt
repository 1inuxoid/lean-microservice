package ch.ti8m.channelsuite.errorhandler


import java.time.OffsetDateTime
import java.util.*

/**
 * Indicates an API violation. Upon receiving such an error, the following steps have to be typically taken to handle
 * the situation:
 *
 *
 *  1. Abort the request action.
 *  1. Close any opened resources.
 *  1. Free any allocated resources.
 *  1. Notify the user/client/consumer of the error.
 *  1. Log the error for debugging purposes.
 *
 */
class ClientError(errorType: ErrorTypeEnum, code: ErrorCode, message: String, timestamp: OffsetDateTime) :
        GeneralError(errorType, code, message, timestamp) {

    var argumentsRejected: List<ArgumentIdentifier> = ArrayList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val clientError = other as ClientError?
        return this.argumentsRejected == clientError!!.argumentsRejected && super.equals(other)
    }

    override fun hashCode(): Int {
        return Objects.hash(argumentsRejected, super.hashCode())
    }

    override fun toString(): String {
        return """ClientError{
                argumentsRejected=$argumentsRejected
                } ${super.toString()}"""
    }
}

