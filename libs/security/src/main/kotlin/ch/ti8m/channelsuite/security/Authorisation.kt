package ch.ti8m.channelsuite.security

import ch.ti8m.channelsuite.security.api.RequestSecurityContext


private val PERMISSION_COMPONENT_DELIMITER = ':'
private val COMPONENT_ENUMERATION_DELIMTER = ','
private val WILDCARD = "*"


/**
 * Checks whether a Shiro-style wildcard permission implies another one
 */
fun permissionSpecificationImpliesOther(superPermission: String, subPermission: String): Boolean {

    val superPart = superPermission.substringBefore(PERMISSION_COMPONENT_DELIMITER)
    val subPart = subPermission.substringBefore(PERMISSION_COMPONENT_DELIMITER)

    // x:y implies x:y:z:...
    if (superPart.isBlank()) return true

    return ((superPart == WILDCARD)
            || superPart.split(COMPONENT_ENUMERATION_DELIMTER)
                         .containsAll(subPart.split(COMPONENT_ENUMERATION_DELIMTER)))

      && permissionSpecificationImpliesOther(
            superPermission.substringAfter(PERMISSION_COMPONENT_DELIMITER, ""),
            subPermission.substringAfter(PERMISSION_COMPONENT_DELIMITER, ""))
}

/**
 * checks whether the currently logged-in user has a given permission.
 */
fun hasCurrentUserPermission( permission: String, mappings: Map<String, List<String>>) : Boolean {
    val permissionsHeldByUser = RequestSecurityContext.getUserInfo().roles
            .fold(setOf<String>()){ p:Set<String>, role:String -> p + (mappings[role]?: emptyList()) }

    return ! permissionsHeldByUser.find { permissionSpecificationImpliesOther(it, permission)}.isNullOrEmpty()
}