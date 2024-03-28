package cn.llonvne.role.checker

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlin.reflect.KClass

class ReturnAllAsRoleTypeResolver(
    private val roleConstructorExtFileSpec: FileSpec.Builder
) {
    fun resolve(dataClasses: List<RoleCheckerSymbolProcessor.AsRole>) {
        roleConstructorExtFileSpec.addFunction(
            FunSpec.builder("allRoleType")
                .addKdoc("返回所有被 AsRole 注解的权限模型")
                .returns(
                    LIST.parameterizedBy(KClass::class.asClassName().parameterizedBy(STAR))
                )
                .addCode("return listOf(%L)",
                    dataClasses.joinToString(",") {
                        it.cls.simpleName.asString() + "::class"
                    }
                )
                .build()
        )
    }
}