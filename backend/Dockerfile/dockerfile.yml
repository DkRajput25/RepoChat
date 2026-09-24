FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY . .

RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

EXPOSE 10000

CMD ["sh", "-c", "java -jar target/*.jar --server.port=${PORT:-10000}"]