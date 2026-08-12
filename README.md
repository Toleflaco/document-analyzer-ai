# Document Analyzer AI · Spring AI + Anthropic Claude

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

**Estado actual**: activo en `/analyze` y `/analyze/pdf`, temporalmente desactivado en `/chat` por una interacción no diagnosticada con `MessageChatMemoryAdvisor` que duplica mensajes en Redis. Ver TODO en `ChatController.java`.

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

## Cómo arrancar en local

1. Clona el repositorio:

   ```bash
   git clone https://github.com/Toleflaco/document-analyzer-ai.git
   cd document-analyzer-ai
   ```

2. Consigue una API key de Anthropic: regístrate en [console.anthropic.com](https://console.anthropic.com), genera una clave y guárdala en un lugar seguro (no en el repo).

3. Exporta la clave como variable de entorno:

   ```bash
   export ANTHROPIC_API_KEY=<tu-clave-real>
   ```

4. Levanta Redis (necesario para `/chat`):

   ```bash
   docker compose up redis -d
   ```

5. Arranca la aplicación:

   ```bash
   ./mvnw spring-boot:run
   ```

El servidor levanta en `http://localhost:8080`. Redis escucha en `localhost:6379`.

El `docker-compose.yml` incluye también un servicio `app` bajo profile `full-stack` para levantar toda la stack en contenedores (`docker compose --profile full-stack up`), útil para verificar el despliegue containerizado sin arrancar la app desde el IDE.

## Estado del roadmap

Proyecto vehículo cerrado de la **Fase 1** del [AI Engineer Roadmap · Java + Spring AI](https://github.com/Toleflaco/ai-engineer-roadmap-java). Las siguientes fases del roadmap se cubren en proyectos independientes.
