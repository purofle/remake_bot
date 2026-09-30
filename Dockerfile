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


# libtdjni.so is linked against glibc and LLVM libc++, so the runner can't be Alpine (musl).
FROM azul/zulu-openjdk:26-jre-latest AS runner

ARG TARGETARCH

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        libc++1-15 libc++abi1-15 libunwind-15 libssl3 zlib1g \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

RUN --mount=type=bind,source=tdjni,target=/tmp/tdjni \
    case "$TARGETARCH" in \
        amd64) arch=x64 ;; \
        *) arch="$TARGETARCH" ;; \
    esac \
    && if [ ! -f "/tmp/tdjni/linux-$arch/libtdjni.so" ]; then \
        echo "tdjni/linux-$arch/libtdjni.so not found, $TARGETARCH is not supported" >&2; exit 1; \
    fi \
    && mkdir -p /app/lib && cp "/tmp/tdjni/linux-$arch/"* /app/lib/

COPY --from=build /remake_bot/build/libs/remake_bot.jar app.jar

ENTRYPOINT ["java", "-Djava.library.path=/app/lib", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]
