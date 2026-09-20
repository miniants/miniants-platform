# 发布

1. `./gradlew testAll generateSbom`
2. 更新 `CHANGELOG.md` 与版本号（`build.gradle` / `gradle.properties` 保持一致）
3. 配置 `CENTRAL_USERNAME` / `CENTRAL_PASSWORD` / `SIGNING_KEY` / `SIGNING_PASSWORD`
4. `./gradlew publish`
5. 在 Central Portal 完成发布
6. 打 tag：`vX.Y.Z`

采用方只改 `platformVersion`。不要发 SNAPSHOT 进生产。
