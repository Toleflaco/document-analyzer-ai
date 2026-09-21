# Document Analyzer AI · Spring AI + Anthropic Claude

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI 2.0](https://img.shields.io/badge/Spring%20AI-2.0-blue.svg)](https://spring.io/projects/spring-ai)
[![Anthropic Claude](https://img.shields.io/badge/Anthropic-Claude%20Sonnet%204.5-D97757.svg)](https://www.anthropic.com/)

API de análisis de documentos con Spring Boot y Spring AI. Análisis estructurado de CVs a partir de texto plano o PDF, con extracción de datos vía LLM. Memoria de chat persistida en Redis.

## Contexto

Este proyecto es el Proyecto 1 del [AI Engineer Roadmap · Java + Spring AI](https://github.com/Toleflaco/ai-engineer-roadmap-java), un roadmap de formación como AI Engineer en Java. El repo hub contiene el roadmap completo, los cuatro proyectos entregables y la bitácora de progreso.

## Stack técnico

- Java 21
- Spring Boot 4.1.0
- Spring AI 2.0.0
- Anthropic Claude Sonnet 4.5 (vía `spring-ai-starter-model-anthropic`)
- Redis 7.4 (vía `spring-boot-starter-data-redis` con Lettuce 7.5.2) para persistencia de memoria conversacional

## Endpoints

### `GET /chat`

Conversación con memoria persistida en Redis. `MessageChatMemoryAdvisor` con `MessageWindowChatMemory` de ventana 10 mensajes, respaldado por `RedisChatMemoryRepository` custom. La memoria sobrevive a reinicios de la JVM y se segmenta por `conversationId`: cada valor distinto abre un hilo de conversación independiente. TTL de 24h por conversación.

Query params: `message`, `conversationId`.

```bash
curl -G "http://localhost:8080/chat" \
  --data-urlencode "message=¿Qué es Spring AI?" \
  --data-urlencode "conversationId=sesion-1"
```

**Observabilidad:** LlmLoggingAdvisor mide latencia, tokens y coste estimado por llamada.

### `POST /analyze`

Extracción estructurada de datos de un CV en texto plano. Prompt externalizado en `src/main/resources/prompts/analyze-cv.st`. La conversión del output del modelo a `CvSummary` usa `BeanOutputConverter`.

Body JSON:

```bash
curl -X POST "http://localhost:8080/analyze" \
  -H "Content-Type: application/json" \
  -d '{"cv": "texto del CV aquí"}'
```

### `POST /analyze/pdf`

Igual que `/analyze` pero recibe el PDF directamente como `Media` de Spring AI, sin extracción de texto intermedia: el PDF nativo se envía a Claude. Prompt propio en `analyze-cv-pdf.st`.

Body `multipart/form-data`, parte `file`:

```bash
curl -X POST "http://localhost:8080/analyze/pdf" \
  -F "file=@/ruta/al/cv.pdf"
```

### Schema de salida: `CvSummary`

Común a `/analyze` y `/analyze/pdf`.

- Campos raíz: `fullName`, `yearsOfExperience`, `topSkills`, `seniorityLevel`.
- `languages`: lista de `Language(name, level)`.
- `education`: lista de `Education(degree, institution, year)`.
- `workExperience`: lista de `WorkExperience(role, company, startYear, endYear, responsibilities)`.

## Persistencia de memoria de chat (Redis)

`RedisChatMemoryRepository` (package `chat/`) implementa `ChatMemoryRepository` de Spring AI 2.0 sobre `StringRedisTemplate`. La política de retención (ventana de N mensajes) vive en `MessageWindowChatMemory`; el repositorio se limita a persistencia CRUD.

- **Modelo de almacenamiento**: cada conversación es una Redis List con clave `chat:memory:{conversationId}`. Cada elemento es un JSON string.
- **Serialización**: DTO propio `MessageRecord(String messageType, String text)` para desacoplar el formato en disco de la jerarquía interna `Message` de Spring AI. Al leer, un `switch` sobre `messageType` reconstruye `UserMessage`, `AssistantMessage` o `SystemMessage`.
- **Idempotencia de `saveAll`**: `DEL` + `RPUSH` + `EXPIRE`. Reemplaza toda la lista en cada llamada (comportamiento contractual de `ChatMemoryRepository`).
- **TTL**: 24 horas por conversación, refrescado en cada `saveAll`.
- **`findConversationIds`**: implementado con `SCAN` (no `KEYS`) para no bloquear Redis en producción.

## Observabilidad

`LlmLoggingAdvisor` (package `observability/`), advisor custom de Spring AI (`CallAdvisor`). Mide latencia, extrae tokens de la respuesta y calcula coste estimado por llamada. Los precios de input/output por millón de tokens están externalizados en `application.properties` (`llm.pricing.input-per-mtok`, `llm.pricing.output-per-mtok`), no hardcodeados en el advisor.

**Estado actual**: completamente operativo en `/chat`, `/analyze` y `/analyze/pdf`. Configurado con `getOrder() = 100` para ejecutarse después de `MessageChatMemoryAdvisor`, evitando duplicación de mensajes en Redis y garantizando observabilidad en todo el flujo conversacional.

**Salida de logs:**

```
[main] INFO  LlmLoggingAdvisor - llm call completed latency_ms=450 tokens_in=25 tokens_out=187 cost_usd=0.000128
```

## Manejo de errores

`GlobalExceptionHandler` (`@RestControllerAdvice`) centraliza los errores en `ProblemDetail` (RFC 7807):

- `MultipartException` → 400 Bad Request.
- `JacksonException` (fallo al parsear el output del LLM a `CvSummary`) → 502 Bad Gateway. Se loguea la excepción original y el path de la petición.

## Configuración relevante

| Propiedad | Valor |
|---|---|
| `spring.ai.anthropic.chat.model` | `claude-sonnet-4-5` |
| `spring.ai.anthropic.chat.temperature` | `0.3` |
| `spring.ai.anthropic.chat.max-tokens` | `1024` |
| `spring.servlet.multipart.max-file-size` | `10MB` |
| `spring.ai.anthropic.api-key` | `${ANTHROPIC_API_KEY}` (variable de entorno, nunca en el repo) |
| `spring.data.redis.host` | `localhost` (en despliegue containerizado se sobrescribe a `redis`) |
| `spring.data.redis.port` | `6379` |
| `llm.pricing.input-per-mtok` | `0.003` (USD por millón de tokens de entrada) |
| `llm.pricing.output-per-mtok` | `0.015` (USD por millón de tokens de salida) |

## Cómo arrancar en local

### Opción 1: Todo en Docker (recomendado)

1. Clona el repositorio:

   ```bash
   git clone https://github.com/Toleflaco/document-analyzer-ai.git
   cd document-analyzer-ai
   ```

2. Consigue una API key de Anthropic: regístrate en [console.anthropic.com](https://console.anthropic.com), genera una clave y guárdala en un lugar seguro (no en el repo).

3. Levanta la stack (app + Redis):

   ```bash
   export ANTHROPIC_API_KEY=<tu-clave-real>
   docker compose up --build
   ```

El servidor levanta en `http://localhost:8080`. Redis escucha en `localhost:6379`.

### Opción 2: App local, Redis en Docker

1. Clona el repositorio:

   ```bash
   git clone https://github.com/Toleflaco/document-analyzer-ai.git
   cd document-analyzer-ai
   ```

2. Consigue una API key de Anthropic (igual que arriba).

3. Levanta SOLO Redis:

   ```bash
   docker run -d -p 6379:6379 redis:7.4-alpine
   ```

4. Arranca la aplicación desde el IDE o terminal:

   ```bash
   export ANTHROPIC_API_KEY=<tu-clave-real>
   ./mvnw spring-boot:run
   ```

El servidor levanta en `http://localhost:8080`.

### Pruebas

Una vez arrancado:

```bash
# Chat conversacional (memoria persistida en Redis)
curl -G "http://localhost:8080/chat" \
  --data-urlencode "message=¿Qué es Spring AI?" \
  --data-urlencode "conversationId=sesion-1"

# Analizar CV en texto plano
curl -X POST "http://localhost:8080/analyze" \
  -H "Content-Type: application/json" \
  -d '{"cv":"Manuel Toledano\nDesarrollador Java\n18 años experiencia..."}'

# Analizar CV en PDF
curl -X POST "http://localhost:8080/analyze/pdf" \
  -F "file=@cv.pdf"
```

## Estado del roadmap

Proyecto vehículo cerrado de la **Fase 1** del [AI Engineer Roadmap · Java + Spring AI](https://github.com/Toleflaco/ai-engineer-roadmap-java). Las siguientes fases del roadmap se cubren en proyectos independientes:

- **Fase 2:** [erp-mcp-server](https://github.com/Toleflaco/erp-mcp-server) — MCP server con Spring AI
- **Fase 3:** Knowledge base empresarial con RAG (planificado)
- **Fase 4–5:** Observabilidad y despliegue en AWS (planificado)

---

*Última actualización: 2026-09-20*
