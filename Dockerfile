# ── Build stage (optional: if you want CI to build inside Docker) ──
# For the CI pipeline we build the JAR with Maven first, then COPY here.
# This keeps Docker build fast and Maven cache effective.

FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S fitsync && adduser -S fitsync -G fitsync

WORKDIR /app

# Copy the runnable auth-service JAR produced by `mvn package`
COPY auth-service/target/*.jar app.jar

RUN chown fitsync:fitsync app.jar

USER fitsync

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
