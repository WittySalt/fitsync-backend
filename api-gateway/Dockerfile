FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S fitsync && adduser -S fitsync -G fitsync

WORKDIR /app

COPY target/*.jar app.jar

RUN chown fitsync:fitsync app.jar

USER fitsync

EXPOSE 8088

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
