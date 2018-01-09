package ch.ti8m.channelsuite.xservice

import ch.ti8m.channelsuite.database.ChannelsuitePersistence
import ch.ti8m.channelsuite.database.H2EmbeddedServer
import ch.ti8m.channelsuite.database.LiquibaseIntegration
import ch.ti8m.channelsuite.database.PersistenceHealthCheck
import ch.ti8m.channelsuite.kooby.ChannelsuiteSecurity
import ch.ti8m.channelsuite.kooby.EnvMatcher
import ch.ti8m.channelsuite.kooby.EurekaClient
import ch.ti8m.channelsuite.log.LogFactory
import ch.ti8m.channelsuite.xservice.jooq.Tables
import ch.ti8m.channelsuite.xservice.jooq.tables.pojos.PortalUser
import com.codahale.metrics.jvm.FileDescriptorRatioGauge
import com.codahale.metrics.jvm.GarbageCollectorMetricSet
import com.codahale.metrics.jvm.MemoryUsageGaugeSet
import com.codahale.metrics.jvm.ThreadStatesGaugeSet
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.jooby.*
import org.jooby.Jooby.run
import org.jooby.apitool.ApiTool
import org.jooby.json.Jackson
import org.jooby.metrics.Metrics
import org.jooq.DSLContext


private val logger = object: LogFactory {}.packageLogger()
/**
 * A request to create a user
 */
data class UserCreationRequest(val username:String, val firstname:String, val lastname:String)

class ServiceMain : Kooby({

    use(ChannelsuitePersistence())
    use(LiquibaseIntegration())
    use(Jackson().doWith { it.registerModule(KotlinModule()) } )

    on("junit"){_->}.orElse{ _->
        use(EurekaClient())
        use(ChannelsuiteSecurity())
    }

    on("dev") { _ ->
        use(H2EmbeddedServer())
        use(ApiTool().swagger("/apidocs").raml("/raml"))
    }

    on(EnvMatcher.from("(dev|junit)"), Runnable{})
            .orElse { _ ->
        use( Metrics()
                .request()
                .threadDump()
                .healthCheck("db", PersistenceHealthCheck::class.java)
                .metric("memory", MemoryUsageGaugeSet())
                .metric("threads", ThreadStatesGaugeSet())
                .metric("gc", GarbageCollectorMetricSet())
                .metric("fs", FileDescriptorRatioGauge())
        )
    }


    /**
     * Access and creation of users.
     */
    path("/users") {

        /**
         * make users api transaction-per-request
         */
        use("*", "*") { req, rsp, chain ->
            require(DSLContext::class.java).transaction { _ -> chain.next(req, rsp)  }
        }

        /**
         * get a list of all users
         */
        get {
            val dslContext = require(DSLContext::class)
            dslContext.select().from(Tables.PORTAL_USER).map { it.into(PortalUser::class.java) }
        }

        /**
         * adds a new user to the database
         *
         * @return the newly created user with status ```201``` created.
         */
        post {
            val user = body<UserCreationRequest>()
            val dslContext = require(DSLContext::class)
            val i = dslContext.newRecord(Tables.PORTAL_USER, user).store()
            Results.with(i,Status.CREATED)
        }

        /**
         * gets a user by id
         *
         * @param id the user's technical id
         */
        get(":id") {
            val dslContext = require(DSLContext::class)
            dslContext.select().from(Tables.PORTAL_USER).where(
                    Tables.PORTAL_USER.ID.eq(param<Int>("id")))
                    .fetchOne().into(PortalUser::class.java)

        }
    }

})
/**
 * Run application:
 */
fun main(args: Array<String>) {
    run(::ServiceMain, args)

    logger.info("leaving main ...")
}




