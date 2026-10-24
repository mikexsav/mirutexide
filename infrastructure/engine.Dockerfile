FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY pom.xml mvnw ./
COPY core core
COPY refactoring refactoring
COPY analyzer analyzer
COPY examples examples
RUN ./mvnw -B -ntp package
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=build /app/analyzer/target/analyzer-1.0.0.jar app.jar
ENV SERVER_ADDRESS=0.0.0.0 SERVER_PORT=8081
USER 65534:65534
EXPOSE 8081
ENTRYPOINT ["java", "-Xmx384m", "-jar", "app.jar"]
