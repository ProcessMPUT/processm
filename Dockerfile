FROM eclipse-temurin:17
EXPOSE 2080 2443
RUN mkdir /processm
ADD processm.launcher/target/processm-0.7.0-bin.tar.xz /
WORKDIR /processm-0.7.0
ENTRYPOINT ["java", "-Xmx8G", "-jar", "launcher-0.7.0.jar"]
