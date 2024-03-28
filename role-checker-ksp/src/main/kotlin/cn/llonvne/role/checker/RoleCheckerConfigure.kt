package cn.llonvne.role.checker

import com.squareup.kotlinpoet.*

object RoleCheckerConfigure {

    const val PACKAGE_NAME = "cn.llonvne.role.checker"

    const val ROLE_CONSTRUCTOR_FILE_NAME = "RoleConstructorExt"

    const val ROLE_SEALED_INTERFACES_FILE_NAME = "Roles"

    val SEAL_ROLE_CLASS_NAME = ClassName.bestGuess("cn.llonvne.role.checker.Role")

    const val ROLE_PASS_FUNCTION = "pass"

    val ROLE_PASS_FUNCTION_PARAMETER = ParameterSpec.builder("role", SEAL_ROLE_CLASS_NAME).build()

    private val ROLE_PASS_FUNCTION_BUILDER = FunSpec.builder(ROLE_PASS_FUNCTION)
        .addParameter(ROLE_PASS_FUNCTION_PARAMETER)
        .returns(BOOLEAN)


    val SEALED_ROLE_TYPE = TypeSpec.interfaceBuilder("Role")
        .addModifiers(KModifier.SEALED)
        .addFunction(
            ROLE_PASS_FUNCTION_BUILDER
                .addModifiers(KModifier.ABSTRACT)
                .build()
        )
        .build()

}