# Built on the host like the other services (`make package`); build context is the repo root.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY wayfare-trip-service/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
