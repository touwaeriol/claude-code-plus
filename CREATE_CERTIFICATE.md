# 插件签名证书创建指南

插件签名是可选的，但强烈推荐用于确保插件的安全性和完整性。

## 生成私钥和证书

### 方法1：使用 OpenSSL（推荐）

```bash
# 1. 生成私钥
openssl genrsa -out private-key.pem 4096

# 2. 生成证书签名请求 (CSR)
openssl req -new -key private-key.pem -out request.csr

# 3. 生成自签名证书（有效期10年）
openssl x509 -req -days 3650 -in request.csr -signkey private-key.pem -out certificate.crt

# 4. 创建证书链文件
cat certificate.crt > certificate-chain.crt
```

### 方法2：使用 Java keytool

```bash
# 生成密钥库
keytool -genkeypair -alias claudecodeplus -keyalg RSA -keysize 4096 \
        -validity 3650 -keystore keystore.jks \
        -storepass YOUR_PASSWORD -keypass YOUR_PASSWORD

# 导出证书
keytool -exportcert -alias claudecodeplus -keystore keystore.jks \
        -storepass YOUR_PASSWORD -file certificate.crt

# 导出私钥（需要额外工具）
```

## 配置环境变量

在系统中设置私钥密码：

```bash
export PRIVATE_KEY_PASSWORD="your_private_key_password"
```

## 测试签名

```bash
# 构建并签名插件
./gradlew signPlugin

# 验证签名
./gradlew verifyPluginSignature
```

## 注意事项

1. **保密私钥**：永远不要将私钥提交到版本控制系统
2. **备份证书**：安全地备份证书和私钥
3. **密码管理**：使用安全的方式管理密码（如环境变量或密钥管理系统）

## 跳过签名（开发阶段）

如果暂时不需要签名，可以注释掉 `build.gradle.kts` 中的签名配置：

```kotlin
// signing {
//     certificateChainFile = file("certificate-chain.crt")
//     privateKeyFile = file("private-key.pem") 
//     password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
// }
```

然后使用 `buildPlugin` 任务构建未签名的插件：

```bash
./gradlew buildPlugin
```