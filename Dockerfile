FROM eclipse-temurin:17
EXPOSE 2080 2443
ARG revision
ENV revision=$revision
ADD processm.launcher/target/processm-${revision}-bin.tar.xz /
WORKDIR /processm-${revision}
ENTRYPOINT ["sh", "-c", "java -Xmx8G -jar launcher-$revision.jar"]
