package ch.ti8m.channelsuite.security

import ch.ti8m.channelsuite.security.api.*

/*
 *  TODO this is probably not very useful it's more of a placeholder for jwt support really.
 * a simplistic implementation of security token support. Use
 * SimpleSecurityContextTemplate in your filter to enable it.
 *
 */

/**
 * A very simple token, storing user-id and roles only.
 */
data class SimpleToken(val name: String, val roles: List<String>) : SecurityToken {
    override fun wireRepresentation() = "$name|$roles"
}

object SimpleTokenMarshaller : TokenMarshaller {
    override fun marshal(token: SecurityToken?) = token!!.wireRepresentation()

    override fun unmarshal(tokenString: String?): SecurityToken {
        val format = Regex("""(.*)\|\[(.*)]""")
        val match = format.matchEntire(tokenString!!) ?: throw IllegalArgumentException("Illegal token $tokenString")

        return SimpleToken(
                match.groupValues[1],
                match.groupValues[2].split(',').map(String::trim)
        )
    }

    override fun unmarshal(tokenString: String?, requestInfo: RequestInfo?) = TODO("to be implemented")
}

object SimpleTokenConverter : UserInfoTokenConverter {
    override fun createTokenForUser(userInfo: UserInfo?, requestInfo: RequestInfo?)
      = SimpleToken(userInfo!!.loginId, userInfo.roles.toList())

    override fun obtainUserInfoFromToken(securityToken: SecurityToken?): UserInfo {
        if ( securityToken !is SimpleToken)
            throw IllegalArgumentException("Unsupported token type $securityToken")

        return UserInfoFactory.userInfoFor(
                securityToken.name, securityToken.name,
                securityToken.roles.toMutableSet())
    }

    override fun obtainRequestInfoFromToken(securityToken: SecurityToken?): RequestInfo
      = RequestInfoImpl.EMPTY

}

object SimpleSecurityContextTemplate :
        SecurityContextTemplate(SimpleTokenMarshaller, SimpleTokenConverter, emptyList(), "kotlin-poc")
