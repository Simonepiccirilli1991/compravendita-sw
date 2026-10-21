# Immagine dell'applicazione: build con il wrapper Maven, esecuzione su JRE 21.
# Uso tipico: docker compose up -d --build   (vedi README)

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/gestionale-*.jar extract --layers --launcher --destination /estratto

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home-dir /app app \
    && mkdir -p /app /data/uploads && chown -R app:app /app /data
WORKDIR /app
# un livello per tipo: le dipendenze cambiano di rado e restano in cache
COPY --from=build --chown=app:app /estratto/dependencies/ ./
COPY --from=build --chown=app:app /estratto/spring-boot-loader/ ./
COPY --from=build --chown=app:app /estratto/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /estratto/application/ ./
USER app

ENV SPRING_PROFILES_ACTIVE=prod \
    APP_UPLOAD_DIR=/data/uploads \
    TZ=Europe/Rome \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
VOLUME /data/uploads
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
