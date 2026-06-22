# Build: docker build -t msvc-gateway-server .
# Run:   docker run -p 8080:8080 msvc-gateway-server

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=docker
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
