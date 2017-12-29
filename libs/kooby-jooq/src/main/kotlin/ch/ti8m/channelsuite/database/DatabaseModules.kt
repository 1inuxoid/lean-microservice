package ch.ti8m.channelsuite.database

import ch.ti8m.channelsuite.log.LogFactory
import ch.ti8m.channelsuite.kooby.DatabaseConfig
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
import javax.sql.DataSource

/**
 *
 * @author marcus
 * @since  16.12.17
 */
val dbConfigPath = "channelsuite.databaseConfig"
val logger = object : LogFactory{}.packageLogger()

private fun databaseConfig(conf: Config?): DatabaseConfig {
    return conf!!.extract(dbConfigPath)
}

class ChannelsuitePersistence : Jooby.Module {

    private fun dialect(jdbcUrl:String): SQLDialect = when {
        jdbcUrl.contains("h2") -> SQLDialect.H2
        jdbcUrl.contains("postgres") -> SQLDialect.POSTGRES
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
                        .withRenderNameStyle(RenderNameStyle.AS_IS))
                .set(TransactionListenerProvider { TransactionLogger })

        binder!!.bind(DSLContext::class.java).toProvider(Provider<DSLContext> { DSL.using(jooqConfig) })

        binder.bind(DataSource::class.java).toInstance(hikariDataSource)
        binder.bind(DatabaseConfig::class.java).toInstance(dbConfig)
    }
}

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

class H2EmbeddedServer : Jooby.Module {
    override fun configure(env: Env?, conf: Config?, binder: Binder?) {
        val port = "9092"
        val webServer = Server.createWebServer(
                "-webAllowOthers", "-webPort", port)

        env!!.onStart { _ ->
            webServer.start()
            logger.info("H2 web console started at http://localhost:$port/")
        }

        env.onStop { _ ->
            webServer.stop()
        }
    }
}

class LiquibaseIntegration(private val changeLogFile: String = "db/changelog/initial.xml") : Jooby.Module {

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
