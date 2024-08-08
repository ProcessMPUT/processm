FROM eclipse-temurin:17
EXPOSE 2080 2443
RUN mkdir /processm
COPY ./processm.launcher/target/launcher-0.7.0-jar-with-dependencies.jar /processm/
WORKDIR /processm
ENTRYPOINT ["java", "-jar", "launcher-0.7.0-jar-with-dependencies.jar"]
