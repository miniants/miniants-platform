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
3. 配置 `CENTRAL_USERNAME` / `CENTRAL_PASSWORD` / `SIGNING_KEY` / `SIGNING_PASSWORD`
4. `./gradlew publish`
5. 在 Central Portal 完成发布
6. 打 tag：`vX.Y.Z`

RC 先发 `2.0.0-rc.1`。一次采用方发布/回退演练后再发 `2.0.0`。
采用方只改 `platformVersion`。不要发 SNAPSHOT 进生产。
