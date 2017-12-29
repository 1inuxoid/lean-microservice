package ch.ti8m.channelsuite.log

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Helps with easily defining loggers for classes and packages without the need
 * to explicitly reference either.
 *
 * Usage inside a class:
 * ```
 *     private val logger = object : LogFactory{}.classLogger()
 * ```
 *
 * Outside a class you would do:
 * ```
 *     private val logger = object : LogFactory{}.packageLogger()
 * ```
 *
 * @author marcus
 * @since  16.12.17
 */
interface LogFactory {
    /**
     * returns a logger suitable for saving in a package-level `val`.
     */
    fun packageLogger(): Logger = LoggerFactory.getLogger(this.javaClass.`package`.name)

    /**
     * returns a logger suitable for saving in a package-level `val`.
     */
    fun classLogger(): Logger = LoggerFactory.getLogger(this.javaClass.enclosingClass)
}