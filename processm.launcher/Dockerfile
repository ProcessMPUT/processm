FROM timescale/timescaledb:latest-pg16-oss

###############################
# Copied from https://github.com/adoptium/containers/blob/d13a1cc170046e3b3ea4b35c82e1ff4b5b866ca1/17/jre/alpine/Dockerfile

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=$JAVA_HOME/bin:$PATH

# Default to UTF-8 file.encoding
ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

RUN set -eux; \
    apk add --no-cache \
        # java.lang.UnsatisfiedLinkError: libfontmanager.so: libfreetype.so.6: cannot open shared object file: No such file or directory
        # java.lang.NoClassDefFoundError: Could not initialize class sun.awt.X11FontManager
        # https://github.com/docker-library/openjdk/pull/235#issuecomment-424466077
        fontconfig ttf-dejavu \
        # utilities for keeping Alpine and OpenJDK CA certificates in sync
        # https://github.com/adoptium/containers/issues/293
        ca-certificates p11-kit-trust \
        # locales ensures proper character encoding and locale-specific behaviors using en_US.UTF-8
        musl-locales musl-locales-lang \
        tzdata \
        # Contains `csplit` used for splitting multiple certificates in one file to multiple files, since keytool can
        # only import one at a time.
        coreutils \
        # Needed to extract CN and generate aliases for certificates
        openssl \
    ; \
    rm -rf /var/cache/apk/*

ENV JAVA_VERSION=jdk-17.0.12+7

RUN set -eux; \
    ARCH="$(apk --print-arch)"; \
    case "${ARCH}" in \
       x86_64) \
         ESUM='63bae276cc322532b451ae7473127c92a75db16cc95473577f133cd09349822a'; \
         BINARY_URL='https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.12%2B7/OpenJDK17U-jre_x64_alpine-linux_hotspot_17.0.12_7.tar.gz'; \
         ;; \
       *) \
         echo "Unsupported arch: ${ARCH}"; \
         exit 1; \
         ;; \
    esac; \
    wget -O /tmp/openjdk.tar.gz ${BINARY_URL}; \
    echo "${ESUM} */tmp/openjdk.tar.gz" | sha256sum -c -; \
    mkdir -p "$JAVA_HOME"; \
    tar --extract \
        --file /tmp/openjdk.tar.gz \
        --directory "$JAVA_HOME" \
        --strip-components 1 \
        --no-same-owner \
    ; \
    rm -f /tmp/openjdk.tar.gz ${JAVA_HOME}/lib/src.zip;

RUN set -eux; \
    echo "Verifying install ..."; \
    echo "java --version"; java --version; \
    echo "Complete."

################################################

LABEL maintainer="ProcessM https://processm.cs.put.poznan.pl/"

EXPOSE 2080 2443
ARG PROCESSM_VERSION=0.7.0
# The variable is called PROCESSM_VERSION so it looks like PG_VERSION and JAVA_VERSION that are already in the image
ENV PROCESSM_VERSION=$PROCESSM_VERSION

# I've tried to fix uid and gid, but busybox seemed to have some problems with it. Still, until something changes in alpine they seem to be 1000:1000
RUN adduser -s /bin/false -D -H -h /processm processm processm

ADD target/processm-${PROCESSM_VERSION}-bin.tar.xz /
RUN mv /processm-${PROCESSM_VERSION} /processm

RUN set -eux; apk add --no-cache msmtp; rm -rf /var/cache/apk/*
# replace sendmail with msmtp in the default configuration, as our sendmail is provided by busybox and rather clumsy
# however, we should not replace sendmail with msmtp, as msmtp does not exactly behave like a proper sendmail (not that the busybox variant does)
RUN sed 's/\/usr\/sbin\/sendmail/\/usr\/bin\/msmtp/g' /processm/conf/config.properties >/processm/conf/config.properties.82D9ABXukU
RUN mv /processm/conf/config.properties.82D9ABXukU /processm/conf/config.properties
RUN ln -s /processm/conf/msmtprc /etc/msmtprc

COPY docker-entrypoint.sh /processm
COPY docker-start-processm.sh /processm

RUN mkdir /processm/data
RUN chown processm:processm /processm/data
RUN chgrp -R processm /processm/conf
RUN chmod -R 640 /processm/conf/*

VOLUME /processm/conf /processm/data
WORKDIR /processm
ENTRYPOINT ["sh", "docker-entrypoint.sh"]
