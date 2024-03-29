package cn.llonvne.role.checker

import kotlin.reflect.KClass

/**
 * 指示被被标注类是权限模型标识符号类型
 */
@Deprecated("ON DEVELOP", level = DeprecationLevel.HIDDEN)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Id

/**
 * 指示该权限模型包含改 ID
 */
@Deprecated("ON DEVELOP", level = DeprecationLevel.HIDDEN)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class UseId(val idCls: KClass<*>)