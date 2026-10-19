FROM eclipse-temurin:26-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
COPY src src
COPY seeds seeds
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:26-jre
WORKDIR /app
COPY --from=build /src/build/libs/langgraph4j-poc-0.1.0-SNAPSHOT.jar app.jar
COPY --from=build /src/seeds seeds
COPY --from=build /src/gradlew gradlew
COPY --from=build /src/gradle gradle
RUN chmod +x gradlew
EXPOSE 8093
ENTRYPOINT ["java", "-jar", "app.jar"]
