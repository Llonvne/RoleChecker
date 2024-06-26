package cn.llonvne.role.checker

import kotlin.reflect.KClass

/**
 * 指示被标注的方法是一个针对于 [forRole] 的特殊判别方法
 * * 当传入的 role 为 [forRole] 类型时，将传入被标注的方法进行判断
 * * 该方法的声明应该为 fun <anyFunctionNameYouWant>(role:<forRoleType>):Boolean
 * * 请注意该方法**不会**执行任何[forRole]所属权限类的比较逻辑，而只执行方法内部的逻辑。
 * * 除非方法内部存在递归操作，否则该方法不会形成循环验证
 * * [passKey] 为在执行 pass 方法前预先的验证
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION)
annotation class Pass(val forRole: KClass<*>, val passKey: Array<TypelessPassKey> = [])

/**
 * 制定对应键相同则执行判断函数
 * 定义在元注解上时仍可以正常工作
 * [key] Provided 的属性
 * [requiredKey] Requried 属性
 * [requiredType] Required 的类型
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS, AnnotationTarget.ANNOTATION_CLASS)
annotation class PassKey(val key: String, val requiredKey: String, val requiredType: KClass<*>)

/**
 * 验证某一属性是否相同
 * * 必须与 Pass 联合使用，可以在 Pass 前验证某些属性联合相同
 * * 定义在元注解上也可以被正确处理
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Repeatable
annotation class TypelessPassKey(val key: String, val requiredKey: String)

/**
 * [Pass] 的快速方法，指对于 [forRole] 权限，无视其内部状态，被标注的权限总能 Pass
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class UnconditionalPass(val forRole: KClass<*>)

/**
 * 指示被标注的数据类是一个权限类
 *
 * * 如果被指示的权限不包含任何状态，请将第一个参数命名为 placeHolder:Unit 那么 RoleConstruct 将会会其填充 Unit 默认值
 *
 * * 身份验证遵循一下规则进行
 * 1. 如果两者类型相同，则使用相等比较法确定权限，权限判断结束
 * 2. 如果存在 Pass 方法, 则尝试是有 is 尝试将 provide 类型转换为 targetType 依次执行
 * 3. 如果存在 To 方法，将使用 To 方法转换为 targetType 重新开始执行判断
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class AsRole(val log: Boolean = false)

/**
 * 指示被标注的函数可以转换自己的权限为 [targetType]
 *
 * 函数声明应为
 * fun <yourFunctionName>():<TargetType>[?]
 *
 * * 请注意错误的使用 To 可能会导致程序抛出 [CycleToException]
 * * 函数声明中 <TargetType> 允许可空，如果返回值为空表示转换失败，将继续执行转换
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION)
annotation class To(val targetType: KClass<*>)

/**
 * 权限判断程序将在每次到达 To 分支时作出判断，如果形成链式调用，则每一个Role 必须不同，否则将抛出该异常.
 *
 * * 请注意权限判断程序无法静态的检测程序运行时该异常是否会抛出，即使两个Role存在To的循环链。
 */
class CycleToException : Exception()
