# ==============================================================================
# Etapa 1: Construcción (Builder) con Maven y JDK 21 Alpine
# ==============================================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Descarga y cacheo de dependencias para acelerar builds posteriores
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Compilación y empaquetado del JAR optimizado
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==============================================================================
# Etapa 2: Runtime ligero y de bajo consumo con JRE 21 Alpine
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# Crear usuario sin privilegios root por seguridad
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copiar el artefacto generado desde la etapa de construcción
COPY --from=builder /app/target/urbanfeet-*.jar app.jar
RUN chown -R appuser:appgroup /app

USER appuser

# Render asigna dinámicamente la variable PORT (por defecto 8080)
EXPOSE 8080

# ==============================================================================
# Optimización de Recursos para Render (Free Tier: 512MB RAM / 0.5 CPU)
# - UseContainerSupport: Detecta los límites reales del contenedor cgroups
# - -Xmx320m: Limita el heap a 320MB, reservando el resto para metaspace/OS y evitando OOM 137
# - -Xms128m: Inicia con memoria reducida
# - -Xss512k: Reduce el tamaño de stack por hilo a la mitad (ahorra mucha RAM)
# - -XX:+UseSerialGC: GC de un solo hilo con mínimo consumo de CPU y memoria
# - -XX:CICompilerCount=2: Reduce hilos del compilador JIT evitando saturar la CPU
# ==============================================================================
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70.0 -Xms128m -Xmx320m -Xss512k -XX:+UseSerialGC -XX:CICompilerCount=2 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
