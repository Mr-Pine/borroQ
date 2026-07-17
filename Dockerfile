FROM gradle:9.6.1-jdk26 AS dependencies

WORKDIR /project

COPY build.gradle.kts settings.gradle.kts gradle.properties* ./
COPY gradle ./gradle
COPY gradlew ./

RUN gradle --no-daemon dependencies || true

FROM dependencies
WORKDIR /project

COPY src src

ENTRYPOINT ["gradle", "--no-daemon"]