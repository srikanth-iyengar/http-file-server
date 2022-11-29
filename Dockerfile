FROM openjdk:17.0.1-jdk-slim
COPY ./app/build/libs/app.jar app.jar
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]
