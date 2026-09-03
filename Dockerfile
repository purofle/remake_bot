FROM azul/zulu-openjdk-alpine:26-jre-latest AS build

WORKDIR /remake_bot

COPY kotlin module.yaml libs.versions.toml ./

# For cache kotlin toolchain in Github Actions
RUN apk add --no-cache curl && \
    KOTLIN_CLI_JAVA_HOME="$JAVA_HOME" ./kotlin --help > /dev/null

COPY . .

RUN --mount=type=cache,target=/root/.cache/JetBrains \
    KOTLIN_CLI_JAVA_HOME="$JAVA_HOME" ./kotlin package -v release


FROM azul/zulu-openjdk-alpine:26-jre-latest AS runner

WORKDIR /app

COPY --from=build /remake_bot/build/tasks/_remake_bot_executableJarJvm/remake_bot-jvm-executable.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
