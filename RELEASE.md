# 发布

## 公开仓

本目录是独立 Git 仓。首次公开：

1. 创建 `miniants/miniants-platform`（public）
2. 保护 `main`：必需 CI、禁止 force-push
3. `git remote add origin git@github.com:miniants/miniants-platform.git`
4. `git push -u origin main`

## Maven Central

1. `./gradlew testAll generateSbom`
2. 更新 `CHANGELOG.md` 与版本号（`build.gradle` / `gradle.properties` 保持一致）
3. 配置发布身份。新名 `centralUsername` / `centralPassword` / `signingKey` /
   `signingPassword`，或沿用 storm-boot 的 `mavenCentralUsername` /
   `mavenCentralPassword` / `signingInMemoryKey` / `signingInMemoryKeyPassword`。
   密码必须是 Central Portal **user token**，不是登录密码。
4. `./gradlew publishToMavenCentral`（Central Portal Publisher API，部署名 `miniants-platform:<version>`）
5. 在 https://central.sonatype.com/publishing/deployments 点 Publish
6. 打 tag：`vX.Y.Z`

RC 先发 `2.0.0-rc.1`。一次采用方发布/回退演练后再发 `2.0.0`。
采用方只改 `platformVersion`。不要发 SNAPSHOT 进生产。
