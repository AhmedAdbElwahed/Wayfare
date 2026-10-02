# The jar is built on the host (`./mvnw package -DskipTests`, or `make package`)
# rather than in a builder stage — one Maven run for the whole reactor instead of
# one per service, so a rebuild is just a jar copy. Build context is the repo root.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY wayfare-driver-service/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
