FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

# Resolve dependencies before copying the application source so unchanged dependencies stay cached.
COPY pom.xml ./
RUN mvn -B -Dmaven.test.skip=true dependency:go-offline

COPY src ./src
RUN mvn -B -Dmaven.test.skip=true package

FROM dragonwell-registry.cn-hangzhou.cr.aliyuncs.com/dragonwell/dragonwell:17-anolis

WORKDIR /app

COPY --from=build /workspace/target/private-domain-assistant-0.1.0-SNAPSHOT.jar /app/private-domain-assistant.jar
RUN chmod 644 /app/private-domain-assistant.jar

USER 999:988

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/private-domain-assistant.jar"]
