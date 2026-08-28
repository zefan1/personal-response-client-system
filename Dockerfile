FROM dragonwell-registry.cn-hangzhou.cr.aliyuncs.com/dragonwell/dragonwell:17-anolis

WORKDIR /app

COPY target/private-domain-assistant-0.1.0-SNAPSHOT.jar /app/private-domain-assistant.jar
RUN chmod 644 /app/private-domain-assistant.jar

USER 999:988

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/private-domain-assistant.jar"]
