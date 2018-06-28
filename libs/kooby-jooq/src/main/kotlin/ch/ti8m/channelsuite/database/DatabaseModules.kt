package ch.ti8m.channelsuite.database

import ch.ti8m.channelsuite.log.LogFactory
import com.codahale.metrics.health.HealthCheck
import com.google.inject.Binder
import com.google.inject.Inject
import com.google.inject.Provider
import com.typesafe.config.Config
import com.zaxxer.hikari.HikariDataSource
import io.github.config4k.extract
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.h2.tools.Server
import org.jooby.Env
import org.jooby.Jooby
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.TransactionContext
import org.jooq.TransactionListenerProvider
import org.jooq.conf.RenderNameStyle
import org.jooq.conf.Settings
import org.jooq.impl.*
import java.sql.DriverManager
import java.sql.SQLException
import javax.sql.DataSource



/**
 *
 * @author marcus
 * @since  16.12.17
 */
val logger = object : LogFactory{}.packageLogger()

/**
 * Defines the configuration for accessing a relational database.
 */
val dbConfigPath = "channelsuite.databaseConfig"
data class DatabaseConfig(
        val jdbcUrl: String,
        val username: String,
        val password: String,
        val checkStatement: String,
        val optimisticLocking: Boolean? = false
)

private fun databaseConfig(conf: Config?): DatabaseConfig {
    return conf!!.extract(dbConfigPath)
}

/**
 * A Jooby module combining the Hikari connection pool and the Jooq DSL into a database
 * access layer.
 */
class ChannelsuitePersistence : Jooby.Module {

    private fun dialect(jdbcUrl:String): SQLDialect = when {
        jdbcUrl.contains("h2") -> SQLDialect.H2
        jdbcUrl.contains("postgres") -> SQLDialect.POSTGRES
        jdbcUrl.contains("oracle") ->
            if (jdbcUrl.toUpperCase().contains("XE")) SQLDialect.ORACLE11G else SQLDialect.ORACLE12C
        else -> SQLDialect.DEFAULT
    }

    override fun configure(env: Env?, conf: Config?, binder: Binder?) {
        val dbConfig = databaseConfig(conf)

        val hikariDataSource = HikariDataSource()
        with(hikariDataSource) {
            jdbcUrl = dbConfig.jdbcUrl
            username = dbConfig.username
            password = dbConfig.password
        }

        val jooqConfig = DefaultConfiguration()
                .set(ThreadLocalTransactionProvider(
                        DataSourceConnectionProvider(hikariDataSource)))
                .set(dialect(dbConfig.jdbcUrl))
                .set(Settings().withRenderSchema(false)
                        .withRenderNameStyle(RenderNameStyle.AS_IS)
                        .withExecuteWithOptimisticLocking(dbConfig.optimisticLocking))
                .set(TransactionListenerProvider { TransactionLogger })
        binder!!.bind(DSLContext::class.java).toProvider(Provider<DSLContext> { DSL.using(jooqConfig) })

        binder.bind(DataSource::class.java).toInstance(hikariDataSource)
        binder.bind(DatabaseConfig::class.java).toInstance(dbConfig)

        env?.onStop { _ ->
            // First close any background tasks which may be using the DB -> can't do that in the library module
            // Then close any DB connection pools
            hikariDataSource.close()
            // Now deregister JDBC drivers in this context's ClassLoader:
            deregisterJdbcDrivers()
        }
    }

    private fun deregisterJdbcDrivers() {
        // Get the webapp's ClassLoader
        val cl = Thread.currentThread().contextClassLoader
        // Loop through all drivers
        val drivers = DriverManager.getDrivers()
        while (drivers.hasMoreElements()) {
            val driver = drivers.nextElement()
            if (driver.javaClass.getClassLoader() === cl) {
                // This driver was registered by the webapp's ClassLoader, so deregister it:
                try {
                    logger.info("Deregistering JDBC driver {}", driver)
                    DriverManager.deregisterDriver(driver)
                } catch (ex: SQLException) {
                    logger.error("Error deregistering JDBC driver {}", driver, ex)
                }
            } else {
                // driver was not registered by the webapp's ClassLoader and may be in use elsewhere
                logger.trace("Not deregistering JDBC driver {} as it does not belong to this webapp's ClassLoader", driver)
            }
        }
    }
}

/**
 * A health check for the database connection. The statement to be used for the checks can be
 * customised through the [DatabaseConfig.checkStatement] property.
 */
class PersistenceHealthCheck
    @Inject constructor(private val ds : DataSource, private val dbConfig : DatabaseConfig) : HealthCheck() {
    override fun check(): Result {
        try {
            ds.connection.createStatement().execute(dbConfig.checkStatement)
        } catch (e: Exception) {
            return Result.unhealthy(e)
        }
        return Result.healthy()
    }
}

/**
 * A Jooby module exposing an embedded H2 instance through a web-console. Meant for local,
 * exploratory testing by developers.
 */
class H2EmbeddedServer : Jooby.Module {
    override fun configure(env: Env?, conf: Config?, binder: Binder?) {
        // TODO: allow using custom db parameters (url / user)
        val port = conf!!.getInt("channelsuite.databaseConfig.h2-embedded-port")
        val webServer = Server.createWebServer(
                "-webAllowOthers", "-webPort", port.toString())

        env!!.onStart { _ ->
            webServer.start()
            logger.info("H2 web console started at http://localhost:$port/")
        }

        env.onStop { _ ->
            webServer.stop()
        }
    }
}

/**
 * A Jooby module running liquibase-update. The default changelog `db/changelog/master.xml` can
 * be overridden by passing in the changeLogFile property via the constructor.
 */
class LiquibaseIntegration(private val changeLogFile: String = "db/changelog/master.xml") : Jooby.Module {

    override fun configure(env: Env?, conf: Config?, binder: Binder?) {
        if (conf!!.getBoolean(dbConfigPath + ".updateSchema")) {
            env!!.onStart { registry ->
                val connection = JdbcConnection(registry.require(DataSource::class.java).connection)
                val liquibase = Liquibase(changeLogFile, ClassLoaderResourceAccessor(), connection)
                liquibase.update("default")
            }
        }
    }
}

/**
 * Listens to starts and ends of database transactions and simply logs them.
 */
object TransactionLogger : DefaultTransactionListener() {
    private val logger = object : LogFactory {}.classLogger()
    override fun beginStart(ctx: TransactionContext?) {
        logger.info("starting transaction.")
    }

    override fun commitEnd(ctx: TransactionContext?) {
        logger.info("committed transaction.")
    }

    override fun rollbackEnd(ctx: TransactionContext?) {
        logger.info("rolled back transaction.")
    }
}

