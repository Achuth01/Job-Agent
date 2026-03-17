FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -q -DskipTests package
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
RUN ./mvnw -q -DskipTests exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY --from=build /ms-playwright /ms-playwright
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
