package cn.llonvne.role.checker

import java.util.UUID
import kotlin.reflect.KClass


inline fun <reified T : Any> check(id: UUID, thisRole: T, invoker: T.() -> Boolean): Boolean {

    if (CheckContext.isCycle(id, T::class)) {
        throw CycleToException()
    }

    val ret = thisRole.invoker()

    if (ret) {
        CheckContext.clean(id)
    }

    return thisRole.invoker()
}

object CheckContext {
    private val idMaps = mutableMapOf<UUID, Set<String>>()

    fun clean(id: UUID) {
        idMaps.remove(id)
    }

    fun isCycle(id: UUID, cls: KClass<*>): Boolean {
        if (id !in idMaps.keys) {
            idMaps[id] = setOf(cls.qualifiedName!!)
            return false
        } else {
            val set = idMaps[id]!!
            if (set.contains(cls.qualifiedName)) {
                idMaps.remove(id)
                return true
            } else {
                idMaps[id] = set + cls.qualifiedName!!
                return false
            }
        }
    }
}