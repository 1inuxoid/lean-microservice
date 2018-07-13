package ch.ti8m.channelsuite.errorhandler

import com.fasterxml.jackson.annotation.JsonValue

const val ERROR_SERVICE_CLASS = "ServiceError"
const val ERROR_INTERNAL_CLASS = "InternalError"
const val ERROR_CLIENT_CLASS = "ClientError"

/**
 * Identifies an argument.
 */
data class ArgumentIdentifier(
        /**
         * The primary purpose of the path is to identify the value/argument that caused the problem and act as a key.
         * Depending on the content type, different syntax is suitable to identify the offending argument. Therefore, if the
         * content refers to a
         *  * JSON Content, the key MUST be expressed as a valid JSONPath expression.
         *  * XML Content, the key MUST be expressed as a valid XPath expression.
         *  * Any other format (e.g. application/x-www-form-urlencoded), they SHOULD be expressed as a valid PCRE
         * Regular Expression.
         *
         * @return path
         */
        var path: String,
        /**
         * A code that in a given service context uniquely identifies the error. The code MAY also be used as message key
         * for i18. Every service must implement, document &amp; publish their error codes.
         *
         * @return argumentErrorCode
         */
        var argumentErrorCode: String,
        /**
         * Informative(!) description about what went wrong @return message.
         */
        var message: String,

        var argumentAdvice: ArgumentIdentifierArgumentAdvice? = null
)

/**
 * Provides the client with some structured value advice such as maxSize&#x3D;2048.
 */
data class ArgumentIdentifierArgumentAdvice(
        var name: String? = null,
        var value: String? = null
)

/**
 * StackTrace model.
 */
data class StackTrace(var message: String, var trace: List<String>)


/**
 * The error code MAY also be used as message key for i18.
 * Every service must implement and document &amp; publish their error codes.
 *
 *  The ErrorCode (essentially the union of the ErrorCode properties serviceId and code) MUST be unique within
 * ti&amp;m and partner companies.
 */
data class ErrorCode(
        /**
         * A unique service identifier that identifies the service implementation. This service identifier SHOULD
         * correspond with the Module-ID.
         *
         * @return serviceId
         */
        var serviceId: String,

        /**
         * A (error) code that is unique within the context of a service (serviceId).
         *
         * @return code
         */
        var code: String) {

    companion object {

        /**
         * Default Error code for Channelsuite (Unspecified).
         */
        const val DEFAULT_ERROR_CODE = "CS-001"
    }
}


/**
 * Defines what data needs to be conveyed to the client for a client-caused error.
 *
 * @author marcus
 * @since 12/08/16
 */
interface CustomErrorData {
    /**
     * Simple exception name, e.g. AuthenticationException (created with class.getSimpleName())
     */
    val code: String?

    /**
     * The actual error message found in the exception.
     */
    val message: String?

    /**
     * In the event of a validation error (i.e., properties failing constraint checks), here you will get
     * the properties failing the validation (the keys) and the error message of the validation (the values).
     */
    val violations: Map<String, String>

    /**
     * The type of error. It is simpler to classify the error into a type. See [ErrorType] for
     * which classes of errors we defined.
     */
    val errorType: ErrorType
}

/**
 * Combines multiple types of errors into a group. So that for example the frontend can display a meaningful message
 * to the user if such an error occurs.
 */
enum class ErrorType {

    /**
     * For data related errors.
     */
    ILLEGAL_DATA,
    /**
     * For database related errors.
     */
    TRANSACTION_ERROR,
    /**
     * For generic errors.
     */
    UNDEFINED,
    /**
     * For http request related errors.
     */
    CLIENT_ERROR,
    /**
     * For access related errors.
     */
    ACCESS_ERROR

}

/**
 * Error type enumeration.
 */
enum class ErrorTypeEnum(@get:JsonValue val value: String) {

    CLIENTERROR(ERROR_CLIENT_CLASS),

    SERVICEERROR(ERROR_SERVICE_CLASS),

    INTERNALERROR(ERROR_INTERNAL_CLASS);

    override fun toString(): String {
        return value
    }
}

/**
 * Defines the contract for exceptions caused by an erroneous usage (wrong parameters / context) of the service by
 * its client.
 * Exception of this type will be converted to end-user friendly messages
 *
 * @since 12/08/16
 */
@Suppress("unused")
abstract class ClientErrorException : RuntimeException, CustomErrorData {

    override val violations = emptyMap<String, String>()

    constructor() : super()

    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(cause: Throwable) : super(cause)
}
