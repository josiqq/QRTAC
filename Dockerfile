# Etapa de construcción (opcional, si quieres compilar dentro del contenedor)
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Etapa de ejecución
FROM eclipse-temurin:17-jdk-alpine

# Directorio de trabajo dentro del contenedor
WORKDIR /app

# Copia el archivo JAR compilado (ajusta el nombre del archivo)
COPY target/qrtac-0.0.1-SNAPSHOT.jar app.jar

# Expone el puerto que tu aplicación usa
EXPOSE 8080

# Comando para ejecutar la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]
