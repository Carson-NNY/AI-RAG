# ---------- build ----------
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /workspace

# copy entire repo so all modules are present
COPY . .

# build only dev-tech-app but also-make its deps
RUN mvn -pl dev-tech-app -am clean package -DskipTests

# ---------- runtime ----------
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=builder /workspace/dev-tech-app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
