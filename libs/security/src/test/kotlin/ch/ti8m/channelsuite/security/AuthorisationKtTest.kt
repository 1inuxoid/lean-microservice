package ch.ti8m.channelsuite.security

import ch.ti8m.channelsuite.security.api.*
import ch.ti8m.channelsuite.security.api.UserInfo.ANONYMOUS
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class AuthorisationKtTest {

    @ParameterizedTest
    @CsvSource("a:b, a:b",   "a, a",  "a, a:b:c",  "'a:b,c', a:c", "'a:b,c,d', 'a:b'",
            "a:*:c, a:b:c")
    fun valid_implications_work(superPermission:String, impliedPermission:String) {

        assertTrue( permissionSpecificationImpliesOther(superPermission, impliedPermission),
                superPermission +" should imply " +impliedPermission + " - but it doesn't.")
    }

    @ParameterizedTest
    @CsvSource("a:b,a:c","a,b", "a:b:c,a", "a:b:c,a:b", "a:*:c, a:b:d")
    fun invalid_implications_fail(superPermission:String, impliedPermission:String) {

        assertFalse( permissionSpecificationImpliesOther(superPermission, impliedPermission),
                superPermission +" should not imply " +impliedPermission + " - but does.")
    }

    @Test
    fun checking_permissions_for_loggedin_user_works() {

        val admin = UserInfoFactory.userInfoFor("admin", "admin", "everything")
        val reader = UserInfoFactory.userInfoFor("reader", "reader", "read-any")
        val mappings = mapOf(
                "everything" to listOf("*"),  "read-any" to listOf("xservice:*:read")
        )

       assertPermissionCheck( ANONYMOUS, mappings, "xservice:user:read", false)
       assertPermissionCheck( admin, mappings, "xservice:user:read", true)
       assertPermissionCheck( reader, mappings, "xservice:user:read", true)
       assertPermissionCheck( reader, mappings, "xservice:user:write", false)
       assertPermissionCheck( admin, mappings, "xservice:user:write", true)

    }

    fun assertPermissionCheck( user:UserInfo, mappings: Map<String, List<String>>, permission:String, shouldHavePermission: Boolean) {
        SimpleSecurityContextTemplate.performLoggedInWith(user, RequestInfoImpl.EMPTY){
            assertEquals( shouldHavePermission, hasCurrentUserPermission(permission, mappings))
        }
    }
}

//__ simple mock implementation, useful to test the authentication/authorisation infra ___

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
        SecurityContextTemplate(
                SimpleTokenMarshaller,
                SimpleTokenConverter,
                { securityToken ->  },
                emptyList(),
                "kotlin-poc"
        )

