# ---- Build stage: Java 21 + Maven wrapper ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy wrapper + pom first (cache deps)
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Normalize line endings & ensure executable (in case CRLF snuck in)
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -q -DskipTests dependency:go-offline

# Copy source and build the jar
COPY src src
RUN ./mvnw -DskipTests package

# ---- Run stage: slim JRE 21 ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar
ENV JAVA_OPTS=""
EXPOSE 8080
CMD ["sh","-c","java $JAVA_OPTS -jar app.jar"]
