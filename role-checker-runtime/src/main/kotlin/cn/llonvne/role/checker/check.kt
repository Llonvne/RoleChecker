package cn.llonvne.role.checker

fun Collection<Role>.pass(required: Role): Boolean {
    return any {
        it.pass(required)
    }
}