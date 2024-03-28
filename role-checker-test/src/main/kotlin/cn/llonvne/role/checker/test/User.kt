package cn.llonvne.role.checker.test

import cn.llonvne.role.checker.*

@AsRole(log = true)
data class UserRole(val id: Int)

@AsRole(log = true)
data class RoleAdmin(val placeHolder: Unit) {
    @Pass(UserRole::class)
    fun pass(userRole: UserRole): Boolean {
        println(userRole)
        return true
    }
}

@AsRole
data class KickMember(val teamId: Int)

@AsRole(log = true)
data class TeamMember(val teamId: Int)

@AsRole(log = true)
data class TeamAdministrator(val teamId: Int) {
    @Pass(TeamMember::class)
    fun member(teamMember: TeamMember): Boolean {
        return teamMember.teamId == teamId
    }

    @Pass(KickMember::class)
    fun kick(kickMember: KickMember): Boolean {
        return kickMember.teamId == teamId
    }
}

@AsRole
data class TeamRoot(val placeHolder: Unit) {
    @Pass(TeamAdministrator::class)
    fun pass(ignored: TeamAdministrator): Boolean {
        return true
    }

    @To(Root::class)
    fun toRoot(): Root {
        println("ToRoot")
        return Root(Unit)
    }
}


@AsRole
data class Root(val placeHolder: Unit) {
    @To(TeamRoot::class)
    fun toTeamRoot(): TeamRoot {
        println("ToTeamRoot")
        return TeamRoot(Unit)
    }
}

fun main() {
    val root = RoleConstructor.createRoot()
    println(root.pass(RoleConstructor.createUserRole(1)))
}