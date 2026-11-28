# 多阶段构建：阶段 1 编译（Maven 依赖走 BuildKit 缓存），阶段 2 只保留 JRE 与可执行 jar，非 root 运行
#
# 关于 jlink 精简：曾按分工表试过 jdeps + jlink 三阶段方案（jdeps 输出 java.base,java.sql，裁剪后 JRE 仅 43MB），
# 但 jlink 产物是 glibc 链接，放到 alpine（musl）里执行报 bin/java: not found，无法启动；
# 要保留 jlink 就得改用 debian 系基础镜像（体积回到 ~200MB 以上）或拉 musl 版 JDK（160MB 基础镜像），
# 收益被抵消，所以最终仍用官方 JRE alpine 镜像。结论记录在 PR 描述里。

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# 拷源码后编译。依赖复用靠下面 RUN 的 BuildKit 缓存挂载（~/.m2），不是靠 pom 层缓存：
# 本仓库是多模块，只拷父 pom 跑不了子模块，所以没有 dependency:go-offline 那一层。
# 注意 --mount 需要 BuildKit：compose v2 默认开启；老环境直接 docker build 会报 Unknown flag。
COPY backend/pom.xml ./pom.xml
COPY backend/ ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Dmaven.test.skip=true -Dmaven.javadoc.skip=true --no-transfer-progress clean package \
 && mkdir -p /out \
 && cp co-bootstrap/target/campus-overflow.jar /out/app.jar

FROM eclipse-temurin:21-jre-alpine
# 非 root 运行
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /out/app.jar /app/app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC"
EXPOSE 8080
USER app
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
