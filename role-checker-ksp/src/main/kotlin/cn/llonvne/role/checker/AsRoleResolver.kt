@file:OptIn(KspExperimental::class)

package cn.llonvne.role.checker

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import java.lang.annotation.Inherited
import java.util.UUID

class AsRoleResolver(
    private val resolver: Resolver,
    private val functionFileSpec: FileSpec.Builder,
    private val roleSealedInterfaceFileSpec: FileSpec.Builder,
    private val environment: SymbolProcessorEnvironment
) {
    private fun <T : Any> T.logClient(asRole: RoleCheckerSymbolProcessor.AsRole, action: T.() -> Any): T {
        return also {
            if (asRole.log) {
                action()
            }
        }
    }

    private val logger = environment.logger
    private val clientLogger = MemberName("cn.llonvne.role.checker", "log")

    init {
        logger.warn("注册 Role 密封接口 ...")
        roleSealedInterfaceFileSpec.addType(RoleCheckerConfigure.SEALED_ROLE_TYPE)
    }

    data class PassFunction(val functionName: String, val type: KSType, val passKeyData: List<TypelessPassKeyData>)


    private fun findAllAnnotations(cls: KSDeclaration): List<KSAnnotation> {
        val allAnnotations = mutableListOf<KSAnnotation>()

        // 遍历 KSClassDeclaration 的 annotations 属性，获取直接存在于该类上的注解
        val directAnnotations = cls.annotations.toList()
        allAnnotations.addAll(directAnnotations)

        for (anno in directAnnotations) {
            if (anno.annotationType.resolve().declaration.qualifiedName?.asString() !in
                setOf(
                    Target::class,
                    Retention::class,
                    Inherited::class,
                    MustBeDocumented::class
                ).map { it.qualifiedName }
            ) {
                allAnnotations.addAll(
                    findAllAnnotations(anno.annotationType.resolve().declaration)
                )
            }
        }

        return allAnnotations
    }


    @OptIn(KspExperimental::class)
    private fun findPassAnnotatedFunction(cls: KSClassDeclaration): List<PassFunction> {

        val targetType: List<PassFunction> = cls.getAllFunctions().toList().flatMap { func ->

            val allAnnotation = findAllAnnotations(func)

            val typelessPasskeys = allAnnotation.filter {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == TypelessPassKey::class.qualifiedName
            }.toList()
                .map {
                    TypelessPassKeyData(
                        it.arguments[0].value as String,
                        it.arguments[1].value as String
                    )
                }

            func.annotations.mapNotNull {
                val name = it.annotationType.resolve().declaration.qualifiedName
                    ?: throw RuntimeException("${cls.simpleName.getShortName()}的${func.simpleName.getShortName()}包含名称为空的注解")
                if (name.asString() == Pass::class.qualifiedName!!) {
                    logger.warn("找到 Pass 注解在函数 ${func.simpleName.getShortName()} 为 ${it.arguments[0]}")

                    val annotationsByType: Pass =
                        func.getAnnotationsByType(Pass::class).toList().firstOrNull() ?: return@mapNotNull null

                    PassFunction(
                        func.simpleName.getShortName(),
                        it.arguments[0].value as KSType,
                        annotationsByType.passKey.toList().map {
                            TypelessPassKeyData(
                                it.key, it.requiredKey
                            )
                        } + typelessPasskeys
                    )
                } else {
                    null
                }
            }.toList()
        }.toList()

        targetType.forEach {
            if (!it.type.declaration.annotations.any {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == AsRole::class.qualifiedName
                }) {
                throw RuntimeException("${it.type.declaration.qualifiedName?.getShortName()} 作为 Pass 参数必须被 AsRole 注解")
            }
        }

        return targetType
    }

    private fun findUnconditionalPass(cls: KSClassDeclaration): List<KSType> {
        return cls.annotations.filter {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == UnconditionalPass::class.qualifiedName
        }
            .map {
                it.arguments.first().value as KSType
            }.toList()
    }

    data class PassKeyData(
        val provide: String,
        val required: String,
        val targetType: KSType
    )

    data class TypelessPassKeyData(
        val provide: String,
        val required: String,
    )

    private fun findPassKey(cls: KSClassDeclaration): List<PassKeyData> {
        return findAllAnnotations(cls)
            .mapNotNull {
                val fq = it.annotationType.resolve().declaration.qualifiedName?.asString()

                if (fq == PassKey::class.qualifiedName) {
                    return@mapNotNull PassKeyData(
                        it.arguments.first().value as String,
                        it.arguments[1].value as String,
                        it.arguments[2].value as KSType
                    )
                }
                null
            }
            .toList()
    }

    fun resolve(asRole: RoleCheckerSymbolProcessor.AsRole) {
        val cls = asRole.cls
        val clsShortname = cls.simpleName.getShortName()
        logger.warn("AsRoleResolver 正在处理 $clsShortname")

        findPassAnnotatedFunction(cls)

        val implClsName = "${clsShortname}RoleImpl"
        val createFunctionName = "create${cls.simpleName.getShortName()}"

        logger.warn("AsRoleResolver 为 $clsShortname 生成权限包装类 $implClsName")
        resolveClass(cls, implClsName, asRole)
        logger.warn("AsRoleResolver 为 $clsShortname 生成权限构造函数 $createFunctionName")
        resolveFunction(cls, implClsName, createFunctionName)
    }

    private fun CodeBlock.Builder.resolvePassTypes(
        cls: KSClassDeclaration,
        instanceName: String,
        asRole: RoleCheckerSymbolProcessor.AsRole
    ): CodeBlock.Builder {
        findPassAnnotatedFunction(cls)
            .forEach {
                beginControlFlow(
                    "if (role is %N && %N.%N(%N.%N))",
                    it.type.toClassName().simpleName + "RoleImpl",
                    instanceName,
                    it.functionName,
                    "role",
                    instanceName
                )

                it.passKeyData.forEach { (provide, required) ->
                    beginControlFlow("if (role.%N.%N == %N.%N)", instanceName, required, instanceName, provide)
                    logClient(asRole = asRole) {
                        addStatement(
                            "%M(%S + %L + %S + %S)",
                            clientLogger,
                            "检查 ${cls.simpleName.getShortName()} 权限,required:",
                            "role.instance",
                            "provide: [通过 Pass 函数自定义检查]",
                            it.functionName
                        )
                    }
                }
                addStatement("return true")
                it.passKeyData.forEach {
                    endControlFlow()
                }

                endControlFlow()
            }
        return this
    }

    private fun CodeBlock.Builder.resolveUnconditionalPass(
        cls: KSClassDeclaration,
        instanceName: String
    ): CodeBlock.Builder {
        findUnconditionalPass(cls).forEach {
            beginControlFlow("if (role is %N)", it.toClassName().simpleName + "RoleImpl")
            addStatement("return true")
            endControlFlow()
        }
        return this
    }

    private fun CodeBlock.Builder.resolvePassKey(
        cls: KSClassDeclaration,
        instanceName: String
    ): CodeBlock.Builder {
        findPassKey(cls).forEach { (provided, required, type) ->
            beginControlFlow("if (role is %N)", type.toClassName().simpleName + "RoleImpl")
            addStatement("return role.%N.%N == %N.%N", instanceName, required, instanceName, provided)
            endControlFlow()
        }
        return this
    }

    private fun CodeBlock.Builder.resolveToInternal(
        instanceName: String,
        func: KSFunctionDeclaration,
        annotation: KSAnnotation
    ): CodeBlock.Builder {

        val toType = annotation.arguments.first().value as KSType

        if (toType.declaration.qualifiedName?.asString() != func.returnType?.resolve()?.declaration?.qualifiedName?.asString()) {
            logger.error(
                "To 参数类型与函数返回值不匹配，预期的函数返回类型为:${toType.declaration.simpleName.asString()},实际的返回类型为:${func.returnType?.resolve()?.declaration?.simpleName?.asString()}",
                func
            )
            throw RuntimeException("To 参数类型与函数返回值不匹配，预期的函数返回类型为:${toType.declaration.simpleName.asString()},实际的返回类型为:${func.returnType?.resolve()?.declaration?.simpleName?.asString()}")
        }

        addStatement(
            "val to = %N.%N()", instanceName,
            func.simpleName.asString()
        )

        beginControlFlow("if (to != null)")

        beginControlFlow(
            "if(check(id,%N(to,id)){pass(role)})",
            toType.declaration.simpleName.asString() + "RoleImpl",
        ).addStatement("return true")
            .endControlFlow()


        endControlFlow()
        return this
    }

    private fun CodeBlock.Builder.resolveTo(
        instanceName: String,
        cls: KSClassDeclaration,
    ): CodeBlock.Builder {
        cls.getAllFunctions()
            .forEach { func ->
                func.annotations.toList().forEach { anno ->
                    if (anno.annotationType.resolve().declaration.qualifiedName?.asString() == To::class.qualifiedName) {
                        resolveToInternal(instanceName, func, anno)
                    }
                }
            }
        return this
    }



    private fun resolveClass(
        cls: KSClassDeclaration,
        implClsName: String,
        asRole: RoleCheckerSymbolProcessor.AsRole,
    ) {
        val instanceName = "instance"
        val clsClassName = cls.toClassName()
        val idName = "id"


        val typeSpec = TypeSpec.classBuilder(implClsName)
            .addModifiers(KModifier.DATA, KModifier.INTERNAL)
            .addProperty(
                PropertySpec.builder(instanceName, clsClassName)
                    .initializer(instanceName)
                    .build()
            )
            .addProperty(
                PropertySpec.builder(idName, UUID::class)
                    .initializer(idName)
                    .build()
            )
            .addSuperinterface(RoleCheckerConfigure.SEAL_ROLE_CLASS_NAME)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameters(
                        listOf(
                            ParameterSpec.builder(
                                instanceName, clsClassName
                            ).build(),
                            ParameterSpec.builder(
                                idName, UUID::class
                            ).defaultValue("UUID.randomUUID()")
                                .build()
                        )
                    )

                    .build()
            )
            .addFunction(
                FunSpec.builder(RoleCheckerConfigure.ROLE_PASS_FUNCTION)
                    .returns(BOOLEAN)
                    .addParameter(RoleCheckerConfigure.ROLE_PASS_FUNCTION_PARAMETER)
                    .addModifiers(KModifier.OVERRIDE)
                    .addCode(
                        CodeBlock.builder()
                            .beginControlFlow("if (role is %N){", implClsName)
                            .logClient(asRole) {
                                addStatement(
                                    "%M(%S + %L + %S + %L)",
                                    clientLogger,
                                    "检查 $clsClassName 权限,required:",
                                    "role.instance",
                                    ",provide=",
                                    "instance"
                                )
                            }
                            .addStatement("return %N == %N.%N", instanceName, "role", instanceName)
                            .endControlFlow()
                            .resolveUnconditionalPass(cls, instanceName)
                            .resolvePassKey(cls, instanceName)
                            .resolvePassTypes(cls, instanceName, asRole)
                            .resolveTo(instanceName, cls)
                            .addStatement("return false")
                            .build()

                    )
                    .build()
            )

        roleSealedInterfaceFileSpec.addType(typeSpec.build())
    }


    private fun resolveFunction(
        cls: KSClassDeclaration, implClsName: String,
        functionName: String,
    ) {
        val clsShortname = cls.simpleName.getShortName()
        val primaryConstructor =
            cls.primaryConstructor
                ?: throw RuntimeException("数据类 ${cls.simpleName.getShortName()} 未发现主构造器")
        val parameters = primaryConstructor.parameters
        val typeParameterResolver = primaryConstructor.typeParameters.toTypeParameterResolver()

        val instanceName = "instance"

        functionFileSpec.addImport(cls.packageName.asString(), clsShortname)

        val fromFunSpec = FunSpec.builder("from")
            .receiver(RoleConstructor::class)
            .addParameter(ParameterSpec(instanceName, cls.toClassName()))
            .returns(RoleCheckerConfigure.SEAL_ROLE_CLASS_NAME)
            .addCode("return %N(%N)", implClsName, instanceName)
            .build()

        val funSpec = FunSpec.builder(functionName)
            .receiver(RoleConstructor::class)
            .addComment("""Data Class ${cls.qualifiedName?.getShortName()} to Role Constructor""")
            .addComment("Generated By RoleChecker")
            .addParameters(
                parameters.map { kParameter ->

                    val parameterName = kParameter.name?.getShortName()
                        ?: throw RuntimeException("类 ${cls.simpleName.getShortName()}包含未命名参数")

                    val builder = ParameterSpec.builder(
                        parameterName,
                        kParameter.type.toTypeName(typeParameterResolver),
                    )

                    if (parameterName == "placeHolder" && kParameter.type.resolve().declaration.qualifiedName?.asString() == Unit::class.qualifiedName) {
                        builder.defaultValue("Unit")
                    }

                    builder.build()
                }
            )
            .returns(RoleCheckerConfigure.SEAL_ROLE_CLASS_NAME)
            .addCode(
                "val $instanceName = $clsShortname(${
                    parameters.joinToString(",") { it.name?.getShortName() ?: throw RuntimeException("类 ${clsShortname}包含未命名参数") }
                })\n"
            )
            .addCode("return %N(%N)", implClsName, instanceName)
            .build()
        functionFileSpec.addFunction(funSpec)
        functionFileSpec.addFunction(fromFunSpec)
    }


}