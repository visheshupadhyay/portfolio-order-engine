# Stage 1: Docker temporarily uses Maven and Java to build the JAR.
FROM maven:3.9.16-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

# Dependencies are cached unless pom.xml changes.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests


# Stage 2: the final image contains only the Java runtime and application.
FROM eclipse-temurin:24-jre-alpine-3.22

# Create a restricted Linux user for the running application.
RUN addgroup -S orderengine && adduser -S orderengine -G orderengine

WORKDIR /app

COPY --from=build --chown=orderengine:orderengine /workspace/target/portfolio-order-engine-1.0-SNAPSHOT.jar app.jar
COPY --from=build --chown=orderengine:orderengine /workspace/src/main/resources/orders.txt orders.txt

USER orderengine

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]