package ch.ti8m.channelsuite.kooby

import ch.ti8m.channelsuite.security.api.RequestInfoImpl
import ch.ti8m.channelsuite.security.api.UserInfo
import ch.ti8m.channelsuite.security.securityTemplate
import ch.ti8m.channelsuite.stdconfig.AppConfig
import org.jooby.Kooby
import org.jooby.Request
import org.jooby.Response
import org.jooby.Route
import org.slf4j.Logger
import org.slf4j.LoggerFactory


private class Package {
    val logger: Logger = LoggerFactory.getLogger(this.javaClass.`package`.name)
}

val log = Package().logger

object SecurityFilter : Route.Filter {
    lateinit var appConfig : AppConfig //config is available only after routes/filters are set up

    override fun handle(req: Request?, rsp: Response?, chain: Route.Chain?) {
        log.info("filtering request $req")
        val token = req!!.header(appConfig.tokenConfig.tokenName)
        val securityTemplate = securityTemplate(appConfig.tokenConfig)
        if (token.isSet)
            securityTemplate.setupSecurityContext(token.value())
        else
            securityTemplate.setupSecurityContext(
                    appConfig.name,
                    UserInfo.ANONYMOUS,
                    RequestInfoImpl.EMPTY,
                    null)

        chain!!.next(req, rsp)
        securityTemplate.tearDownSecurityContext()
    }

}