package cn.llonvne.role.checker

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.writeTo
import kotlin.reflect.KClass
import kotlin.reflect.KTypeProjection
import kotlin.reflect.typeOf

class RoleCheckerSymbolProcessor(
    private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {
    private val logger = environment.logger

    private val roleConstructorExtFileSpec =
        FileSpec.builder(RoleCheckerConfigure.PACKAGE_NAME, RoleCheckerConfigure.ROLE_CONSTRUCTOR_FILE_NAME)

    private val roleSealedInterfaceFileSpec =
        FileSpec.builder(RoleCheckerConfigure.PACKAGE_NAME, RoleCheckerConfigure.ROLE_SEALED_INTERFACES_FILE_NAME)

    private val returnAllAsRoleResolver = ReturnAllAsRoleTypeResolver(
        roleConstructorExtFileSpec
    )

    override fun finish() {
        roleConstructorExtFileSpec.build().writeTo(codeGenerator = environment.codeGenerator, true)
        roleSealedInterfaceFileSpec.build().writeTo(codeGenerator = environment.codeGenerator, true)
    }

    data class AsRole(val cls: KSClassDeclaration, val log: Boolean)

    override fun process(resolver: Resolver): List<KSAnnotated> {

        val asRoleFQName = cn.llonvne.role.checker.AsRole::class.qualifiedName
            ?: throw RuntimeException("AsRole 接口未被发现，请检查是否将 role-checker-annotation 添加到依赖")

        val asRoleAnnotatedElements = resolver.getSymbolsWithAnnotation(asRoleFQName).toList()

        val dataClasses = asRoleAnnotatedElements.map { ksAnnotated ->
            if (ksAnnotated is KSClassDeclaration) {
                logger.warn("发现被 AsRole 注解的类 ${ksAnnotated.simpleName.getShortName()}")

                val first: KSAnnotation = ksAnnotated.annotations.filter {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == asRoleFQName
                }.first()

                val log = first.arguments[0].value as Boolean

                if (Modifier.DATA in ksAnnotated.modifiers) {
                    logger.warn("${ksAnnotated.simpleName.getShortName()} 成功验证为数据类")
                } else {
                    throw RuntimeException("$ksAnnotated 不是数据类")
                }
                return@map AsRole(ksAnnotated, log)
            } else {
                throw RuntimeException("$ksAnnotated 不是一个类")
            }
        }.toList()

        returnAllAsRoleResolver.resolve(dataClasses)

        val asRoleResolver = AsRoleResolver(
            resolver,
            roleConstructorExtFileSpec,
            roleSealedInterfaceFileSpec,
            environment
        )

        dataClasses.forEach { cls -> asRoleResolver.resolve(cls) }
        return emptyList()
    }


}