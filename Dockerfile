FROM adoptopenjdk:11-jre-hotspot
EXPOSE 2080 2443
RUN mkdir /processm
COPY ./processm.launcher/target/launcher-0.1-SNAPSHOT-jar-with-dependencies.jar /processm/
WORKDIR /processm
ENTRYPOINT ["java", "-jar", "launcher-0.1-SNAPSHOT-jar-with-dependencies.jar"]
