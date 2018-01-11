package ch.ti8m.channelsuite.security

import ch.ti8m.channelsuite.security.api.RequestInfoImpl
import ch.ti8m.channelsuite.security.api.UserInfo
import ch.ti8m.channelsuite.security.api.UserInfo.ANONYMOUS
import ch.ti8m.channelsuite.security.api.UserInfoFactory
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
