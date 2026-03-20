FROM gradle:8.5-jdk17 AS build

WORKDIR /app

COPY . .

RUN chmod +x ./gradlew
RUN ./gradlew :notification-service:bootJar --no-daemon

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=build /app/notification-service/build/libs/*.jar app.jar

ENTRYPOINT ["java","-jar","app.jar"]