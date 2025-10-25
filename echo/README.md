# 🪞 Spring Boot Echo Server

A simple Spring Boot application that **echoes back incoming HTTP requests**.

## Run Application locally
java -jar target/echo-0.0.1-SNAPSHOT.jar --server.port=<port>

## Build and run on docker
docker build -t echo-server .
docker run -d -p 8085:8080 --name echo-service-5 echo-server