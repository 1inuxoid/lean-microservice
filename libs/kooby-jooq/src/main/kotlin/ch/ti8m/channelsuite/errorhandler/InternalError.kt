package ch.ti8m.channelsuite.errorhandler


import java.time.OffsetDateTime
import java.util.*

/**
 * Indicates that the service (implementation) is broken, either through a code bug or through an invalid configuration.
 * - Or, to put it in other words  THE SERVICE IS BROKEN AND MUST BE FIXED! Upon receiving
 * such an error, the following steps have to be typically taken to handle the &#39;emergency&#39;
 *
 *  1. Abort the requested action.
 *  1. Close any opened resources.
 *  1. Free any allocated resources.
 *  1. Notify the user/client/consumer of the error by way of lying about the actual cause.
 *  1. Log the error and raise an alarm to the ops guys.
 *  1. Inform the developers and have them fixing the cause of this problem.
 *
 */
class InternalError(errorType: ErrorTypeEnum, code: ErrorCode, message: String, timestamp: OffsetDateTime) :
        GeneralError(errorType, code, message, timestamp) {

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return if (other == null || javaClass != other.javaClass) {
            false
        } else super.equals(other)
    }

    override fun hashCode(): Int {
        return Objects.hash(super.hashCode())
    }


}

