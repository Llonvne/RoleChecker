package cn.llonvne.role.checker

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
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

    data class PassFunction(val functionName: String, val type: KSType)

    private fun findPassAnnotatedFunction(cls: KSClassDeclaration): List<PassFunction> {
        val targetType: List<PassFunction> = cls.getAllFunctions().toList().flatMap { func ->
            func.annotations.mapNotNull {
                val name = it.annotationType.resolve().declaration.qualifiedName
                    ?: throw RuntimeException("${cls.simpleName.getShortName()}的${func.simpleName.getShortName()}包含名称为空的注解")
                if (name.asString() == Pass::class.qualifiedName!!) {
                    logger.warn("找到 Pass 注解在函数 ${func.simpleName.getShortName()} 为 ${it.arguments[0]}")
                    PassFunction(func.simpleName.getShortName(), it.arguments[0].value as KSType)
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

    fun resolve(asRole: RoleCheckerSymbolProcessor.AsRole) {
        val cls = asRole.cls
        val clsShortname = cls.simpleName.getShortName()
        logger.warn("AsRoleResolver 正在处理 $clsShortname")

        findPassAnnotatedFunction(cls)

        val implClsName = "${clsShortname}RoleImpl"
        val createFunctionName = "create${cls.simpleName.getShortName()}"

        logger.warn("AsRoleResolver 为 $clsShortname 生成权限构造函数 $createFunctionName")
        resolveFunction(cls, implClsName, createFunctionName)
        logger.warn("AsRoleResolver 为 $clsShortname 生成权限包装类 $implClsName")
        resolveClass(cls, implClsName, asRole)
    }

    private fun CodeBlock.Builder.resolvePassTypes(
        cls: KSClassDeclaration,
        instanceName: String,
        asRole: RoleCheckerSymbolProcessor.AsRole
    ): CodeBlock.Builder {
        findPassAnnotatedFunction(cls)
            .forEach {
                nextControlFlow(
                    "else if (role is %N && %N.%N(%N.%N))",
                    it.type.toClassName().simpleName + "RoleImpl",
                    instanceName,
                    it.functionName,
                    "role",
                    instanceName
                )
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
                addStatement("return true")
            }
        return this
    }

    private fun CodeBlock.Builder.resolveToInternal(
        instanceName: String,
        func: KSFunctionDeclaration,
        annotation: KSAnnotation
    ): CodeBlock.Builder {

        val toType = annotation.arguments.first().value as KSType

        nextControlFlow(
            "else if(check(id,%N(%N.%N(),id)){pass(role)})",
            toType.declaration.simpleName.asString() + "RoleImpl",
            instanceName,
            func.simpleName.asString()
        )
        addStatement("return true")
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

    private fun resolveClass(cls: KSClassDeclaration, implClsName: String, asRole: RoleCheckerSymbolProcessor.AsRole) {
        val instanceName = "instance"
        val clsClassName = cls.toClassName()
        val idName = "id"

        val typeSpec = TypeSpec.classBuilder(implClsName)
            .addModifiers(KModifier.DATA)
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
                            .resolvePassTypes(cls, instanceName, asRole)
                            .resolveTo(instanceName, cls)
                            .nextControlFlow("else")
                            .addStatement("return false")
                            .endControlFlow()
                            .build()

                    )
                    .build()
            )

        roleSealedInterfaceFileSpec.addType(typeSpec.build())
    }


    private fun resolveFunction(
        cls: KSClassDeclaration, implClsName: String,
        functionName: String
    ) {
        val clsShortname = cls.simpleName.getShortName()
        val primaryConstructor =
            cls.primaryConstructor
                ?: throw RuntimeException("数据类 ${cls.simpleName.getShortName()} 未发现主构造器")
        val parameters = primaryConstructor.parameters
        val typeParameterResolver = primaryConstructor.typeParameters.toTypeParameterResolver()

        val instanceName = "instance"

        functionFileSpec.addImport(cls.packageName.asString(), clsShortname)

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
    }


}