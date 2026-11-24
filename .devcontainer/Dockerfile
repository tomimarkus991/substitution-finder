FROM eclipse-temurin:25-jdk-noble

RUN apt-get update \
 && apt-get install -y --no-install-recommends git curl ca-certificates procps less \
 && rm -rf /var/lib/apt/lists/*

# Non-root user with no sudo. Replaces the base image's ubuntu user, which has the same uid.
RUN userdel -r ubuntu \
 && useradd -m -u 1000 -s /bin/bash dev \
 && mkdir -p /home/dev/.claude /home/dev/.m2 \
 && chown -R dev:dev /home/dev
