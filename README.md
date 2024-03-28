# RoleCheck

一个类型安全的简单权限模型，使用Kotlin DataClass 来表示数据模型。使用 KSP 生成验证方法

## 使用方法

1. 创建权限模型

```kotlin
@AsRole(log = true)
data class UserRole(val id: Int)
```

使用 AsRole 注解标明一个数据类，该数据类即成为一个权限模型，内部可以包含权限具体属性等，
如果该权限没有内部属性，可以使用 placeHolder:Unit 作为占位符号。

```kotlin   
@AsRole
data class ARole(val placeHolder: Unit)
```

PS: 如果你需要权限验证的日子，如图一所示将 log 设置为 true 即可

2. 获得权限

使用 RoleConstructor.create<YourRole> 即可获得一个权限表示,参数列表与数据类主构造器保持一致。
对于使用了placeHolder:Unit的参数将会自动填充默认值。

```kotlin
val root = RoleConstructor.createUserRole(1)
val aRole = RoleConstructor.createARole()
```

3. 判别权限

使用权限内部的 pass 方法进行判断,格式为

```
Provided.pass(Required)
```

```kotlin
root.pass(aRole)
```

在默认情况下只有两者内部类型一致，且属性一致(使用 DataClass Equals 进行比较)才返回 true,否则返回 false

4. 权限链

你可以使用以下注解来构建权限链

* Pass

Pass 注解指示当 Required 是 Pass 注解给定类型时，尝试执行该方法执行权限判断

```kotlin
@AsRole
data class Root(val placeHolder: Unit) {
    @Pass(ARole::class)
    fun to(aRole: ARole): Boolean {
        return true
    }
}
```

如下所示,会执行到 root.to 方法来判断

```kotlin
val aRole = RoleConstructor.createARole()
val root = RoleConstructor.createRoot()
root.pass(aRole)
```

* To

To 注解指示将 Provided 通过 To 方法转换为 To 的参数中给定的类型，尝试调用该类型的权限标识的 Pass 方法

```kotlin
@AsRole
data class Root(val placeHolder: Unit) {
    @To(ARole::class)
    fun to(): ARole {
        return ARole(placeHolder)
    }
}
```


