FROM azul/zulu-openjdk-alpine:26-latest AS build

WORKDIR /remake_bot

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY tdlib/build.gradle.kts ./tdlib/

RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY . .

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon fatJar


FROM azul/zulu-openjdk-alpine:26-jre-latest AS runner

WORKDIR /app

COPY --from=build /remake_bot/build/libs/remake_bot.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
