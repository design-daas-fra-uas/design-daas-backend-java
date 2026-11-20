FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B -q
COPY src ./src
RUN mvn package -DskipTests -B -q

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /app/keys && chown -R app:app /app
USER app
VOLUME /app/keys
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
