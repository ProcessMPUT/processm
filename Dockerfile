FROM eclipse-temurin:17
EXPOSE 2080 2443
ADD processm.launcher/target/processm-${project.version}-bin.tar.xz /
WORKDIR /processm-${project.version}
ENTRYPOINT ["java", "-Xmx8G", "-jar", "launcher-${project.version}.jar"]
