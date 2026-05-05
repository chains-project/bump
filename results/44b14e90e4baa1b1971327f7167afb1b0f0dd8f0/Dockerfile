FROM ghcr.io/chains-project/breaking-updates:44b14e90e4baa1b1971327f7167afb1b0f0dd8f0-pre

# Java version in the base image is 11; Maven 4 requires >= 17 to run, so upgrade it
RUN apk add --no-cache openjdk17-jdk
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk

RUN wget -q https://dlcdn.apache.org/maven/maven-4/4.0.0-rc-5/binaries/apache-maven-4.0.0-rc-5-bin.tar.gz -O /tmp/maven4.tar.gz \
    && tar -xzf /tmp/maven4.tar.gz -C /opt \
    && rm /tmp/maven4.tar.gz \
    && ln -sf /opt/apache-maven-4.0.0-rc-5/bin/mvn /usr/bin/mvn

CMD ["sh", "-c", "mvn package -DskipTests -B"]
