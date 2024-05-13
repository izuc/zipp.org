# Stage 1: Build the application using Maven
FROM maven:3.8.4-openjdk-17 as builder
COPY src /usr/src/app/src
COPY pom.xml /usr/src/app
WORKDIR /usr/src/app
RUN mvn clean package

# Stage 2: Setup the final image with OpenJDK and IPFS
FROM openjdk:17-jdk-bullseye

# Copy the JAR from the build stage
COPY --from=builder /usr/src/app/target/*.jar /usr/app/app.jar

# Copy application.properties and the IPFS swarm key
COPY src/main/resources/application.properties /usr/app/application.properties
COPY swarm.key /usr/app/swarm.key

# Copy the shared_files.dat and user_keys.dat files
COPY shared_files.dat /usr/app/shared_files.dat
COPY user_keys.dat /usr/app/user_keys.dat

# Install dependencies including wget for IPFS download
RUN apt-get update \
    && apt-get install -y wget jq \
    && wget https://dist.ipfs.io/kubo/v0.28.0/kubo_v0.28.0_linux-amd64.tar.gz \
    && tar -xvzf kubo_v0.28.0_linux-amd64.tar.gz \
    && mv kubo/ipfs /usr/local/bin/ipfs \
    && rm -rf kubo_v0.28.0_linux-amd64.tar.gz kubo \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Copy the WebUI CAR file
COPY webui.car /usr/app/webui.car

# Expose the necessary ports for your Java application and IPFS
EXPOSE 4001 5001 8081 8082

# Copy the startup script
COPY start.sh /usr/app/start.sh
RUN chmod +x /usr/app/start.sh

# Use the startup script to run IPFS daemon and your Java application
CMD ["/usr/app/start.sh"]