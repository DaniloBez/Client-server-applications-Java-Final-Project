FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY shared/build.gradle shared/
COPY server/build.gradle server/
COPY client/build.gradle client/

RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

COPY shared/src shared/src
COPY server/src server/src

RUN ./gradlew :server:shadowJar -x test --no-daemon

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S gamegroup && adduser -S gameuser -G gamegroup
USER gameuser

COPY --from=builder /app/server/build/libs/server-all.jar ./server.jar

EXPOSE 10000

ENTRYPOINT ["java", "-jar", "server.jar"]