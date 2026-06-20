# ─────────────────────────────────────────────────────────────────────────────
# Universal runtime image for ANY runnable FitSync Spring Boot service.
#
# JARs are built by Maven BEFORE this image is built (CI / local `mvn package`),
# then packaged here. Build context = backend root.
#
#   mvn -DskipTests package                         # produces */target/*.jar
#   docker build -f Dockerfile \
#     --build-arg SERVICE=auth-service \
#     --build-arg SERVER_PORT=8080 \
#     -t fitsync-auth-service:latest .
#
# Use scripts/build-images.sh (Linux/CI) or scripts/build-images.ps1 (Windows)
# to build all services at once with the correct ports.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

# Module to package, e.g. auth-service / api-gateway / workout-service.
ARG SERVICE
# Documented container port. The real listen port is driven by the SERVER_PORT
# env var at runtime; this only affects EXPOSE metadata.
ARG SERVER_PORT=8080

RUN addgroup -S fitsync && adduser -S fitsync -G fitsync

WORKDIR /app

# Copy ONLY the runnable Spring Boot jar. The "${SERVICE}-" prefix avoids picking
# up tool jars that also land in target/ (e.g. findsecbugs-plugin-*.jar), and
# the repackaged ".jar.original" does not match "*.jar".
COPY ${SERVICE}/target/${SERVICE}-*.jar app.jar

RUN chown fitsync:fitsync app.jar
USER fitsync

EXPOSE ${SERVER_PORT}

# busybox wget is present in the alpine base — used by the compose / ALB
# healthcheck against /actuator/health.
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
