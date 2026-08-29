# The jar is built on the host (`./mvnw package -DskipTests`, or `make package`)
# rather than in a builder stage — one Maven run for the whole reactor instead of
# five, so a rebuild is just a jar copy. Build context is the repo root.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY wayfare-config-server/target/*.jar app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]
