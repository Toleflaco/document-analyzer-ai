# CLAUDE.md — Document Analyzer AI

Context file for Claude Code. Read this before making any changes to the codebase.

---

## 1. Stack tecnológico

| Componente | Versión |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.0 |
| Spring AI | 2.0.0 |
| Proveedor LLM | Anthropic Claude (starter `spring-ai-starter-model-anthropic`) |
| Spring Web MVC | incluido en Spring Boot 4.1.0 |
| Spring Boot Actuator | incluido en Spring Boot 4.1.0 |
| Spring Boot DevTools | incluido en Spring Boot 4.1.0 (runtime + optional) |
| Build | Maven Wrapper (`./mvnw`) |

**Sin Lombok.** El proyecto no usa Lombok. Getters, setters y constructores son siempre manuales.

**Sin persistencia relacional en esta fase.** No hay JPA, ni Hibernate, ni Flyway, ni base de datos. La configuración es puramente en memoria y stateless. Se añadirá persistencia en fases posteriores del roadmap (memoria conversacional en Redis, RAG con pgvector, etc.), y este fichero se actualizará entonces.

**Sin seguridad JWT en esta fase.** El endpoint `/chat` es público. En fases posteriores se añadirá autenticación cuando el proyecto lo justifique.

---

## 2. Convenciones de código

### DTOs: Java records
Todos los DTOs son `record`, sin excepción. No usar clases con Lombok ni builders.

```java
// Correcto
public record ChatRequest(@NotBlank String message) {}

// Incorrecto
@Data public class ChatRequest { ... }
```

### Inyección de dependencias
Constructor injection en todas las clases. Sin `@Autowired` en campos.

### equals / hashCode
Cuando aplique (records lo generan automáticamente; POJOs solo si son necesarios), se sigue el patrón habitual: basado en identidad de negocio, no en referencia.

---

## 3. Arquitectura

### Package-by-feature (vertical slice)

```
dev.toleflaco.document_analyzer_ai/
├── DocumentAnalyzerAiApplication.java   → punto de entrada
├── chat/                                → conversación libre con el LLM
│   └── ChatController.java
└── (futuros paquetes conforme el proyecto crezca)
    ├── documents/                       → extracción de contenido de PDF, Word, etc.
    ├── analysis/                        → análisis estructurado de documentos
    └── common/                          → GlobalExceptionHandler, DTOs comunes
```

### Capas por feature
`Controller → Service → (colaboradores externos: Spring AI, filesystem, etc.)`

- **Controller**: validación de entrada (`@Valid`), delegación al Service, mapeo a DTO de respuesta.
- **Service**: lógica de negocio, construcción de prompts, invocación de `ChatClient`.
- **DTOs**: separados de cualquier entidad de dominio interna.

En este momento el proyecto solo tiene un `ChatController` sin service, porque la lógica se limita a delegar en `ChatClient`. Cuando la lógica crezca (memoria, prompts parametrizados, extracción estructurada), se extraerá a un `ChatService`.

---

## 4. Gestión de la API key y secretos

### Ningún secreto en Git, nunca

`src/main/resources/application.properties` **es público** y va a Git. **No puede contener claves reales, ni siquiera parciales.** La `ANTHROPIC_API_KEY` se referencia con la sintaxis de Spring Boot para variables de entorno:

```properties
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}
```

Spring Boot resuelve `${ANTHROPIC_API_KEY}` en tiempo de arranque leyendo la variable de entorno del proceso. El fichero de propiedades no ve nunca el valor real.

### Dónde vive la clave en desarrollo local

En `~/.secrets` (fichero con permisos `600`, propiedad del usuario, fuera del árbol de proyecto). Cargado automáticamente por `~/.bashrc` con el patrón:

```bash
if [ -f "$HOME/.secrets" ]; then
    set -a
    source "$HOME/.secrets"
    set +a
fi
```

Este patrón es coherente con `task-manager-api` y con cualquier proyecto futuro del roadmap. **Nunca se hardcodea una clave en el código, ni en `application.properties`, ni en ficheros commiteados.**

### Regla para Claude Code
Si en algún momento se propone escribir una clave real en un fichero versionado (aunque sea en un ejemplo, aunque sea "temporalmente"), la propuesta debe rechazarse. Las claves solo pueden aparecer como referencias `${VAR}` a variables de entorno, o como valores placeholder claramente falsos (`sk-ant-EXAMPLE-NOT-REAL`) en documentación.

---

## 5. Convenciones específicas de Spring AI

### `ChatClient.Builder` en el constructor

El patrón canónico para inyectar Spring AI en un controller o service es recibir el `ChatClient.Builder` en el constructor y construir el `ChatClient` allí:

```java
public ChatController(ChatClient.Builder chatClientBuilder) {
    this.chatClient = chatClientBuilder.build();
}
```

Aunque hoy no personalicemos nada en la construcción, el patrón permite que cada clase defina sus propias `defaultSystem(...)`, `defaultOptions(...)` cuando crezca la funcionalidad.

### Modelo, temperatura y max tokens por defecto

Configurados en `application.properties`:

- **Modelo**: `claude-sonnet-4-5` — equilibrio calidad/coste para Fase 1. Se sobrescribirá por llamada cuando otras funcionalidades requieran Opus (razonamiento complejo) o Haiku (tareas mecánicas).
- **Temperatura**: `0.3` — default sensato para respuestas variadas pero controladas. Se sobrescribirá por llamada según la tarea (0 para extracción estructurada, 0.7 para creatividad, etc.).
- **Max tokens**: `1024` — límite conservador para evitar respuestas kilométricas y gasto imprevisto.

### Sobrescritura por llamada

Cuando una funcionalidad concreta requiera opciones distintas al default (por ejemplo, extracción de datos con temperatura 0), se aplica en la propia llamada mediante `.options(...)`, no cambiando el default global. El default de `application.properties` es un fallback razonable, no un contrato inflexible.

### Ausencia de logs de Spring AI en INFO

La autoconfiguración de Spring AI 2.0 es silenciosa por diseño: en nivel INFO no emite logs de arranque ni de cada llamada. Para diagnóstico se sube temporalmente a DEBUG en `logging.level.org.springframework.ai`. Confirmación de que los beans están cargados: `curl http://localhost:8080/actuator/beans | grep anthropic`.

### Estado y memoria

En esta fase, cada llamada al `ChatClient` es **stateless**. No hay memoria de conversación. Cada request es independiente. La memoria conversacional se añadirá en una fase posterior con `ChatMemory` (Redis o JPA según decisión arquitectónica).

---

## 6. Manejo de errores

### RFC 7807 ProblemDetail

Todas las respuestas de error usan `ProblemDetail` (nativo en Spring 6+, mantenido en 7.x/Spring Boot 4.x). Un `GlobalExceptionHandler` (`@RestControllerAdvice`) centralizará los casos cuando aparezcan.

En el estado actual del proyecto no hay `GlobalExceptionHandler` porque tampoco hay excepciones de dominio propias. Se creará cuando aparezca la primera excepción custom.

### Errores esperables de Spring AI

Cuando Spring AI falla al comunicarse con Anthropic (network, rate limit, invalid API key), lanza `NonTransientAiException` o subclases. Estos errores deben mapearse a `ProblemDetail` con códigos HTTP apropiados (503 para network, 429 para rate limit, 500 para configuración) en el `GlobalExceptionHandler` cuando exista.

---

## 7. Convenciones de commits

El proyecto sigue **Conventional Commits**:

```
<type>(<scope>): <descripción en imperativo, minúsculas>
```

**Types usados**: `feat`, `chore`, `docs`, `refactor`, `test`
**Scopes previstos**: `chat`, `documents`, `analysis`, `config`, `build`, `docs`

Ejemplos:
```
feat(chat): add /chat endpoint with ChatClient
chore(config): configure Spring AI with Anthropic Claude Sonnet 4.5
docs: initial CLAUDE.md for the project
```

Cuando el commit no encaja limpiamente en un scope (por ejemplo, un cambio transversal), se usa sin scope: `chore: bump dependencies`.

---

## 8. Comandos habituales

### Variables de entorno requeridas

```bash
export ANTHROPIC_API_KEY=<clave-real-de-console.anthropic.com>
```

En desarrollo local se carga automáticamente desde `~/.secrets` vía `~/.bashrc` (ver sección 4).

### Arrancar en dev local

```bash
./mvnw spring-boot:run
```

Sin perfiles activos, el servidor arranca en `http://localhost:8080`.

### Compilar y empaquetar

```bash
./mvnw clean package
```

### Ejecutar tests

```bash
./mvnw test
```

Aún no hay tests en el proyecto. Se añadirán conforme aparezcan servicios con lógica testeable.

### Probar el endpoint `/chat`

```bash
curl -G "http://localhost:8080/chat" --data-urlencode "message=<pregunta>"
```

### Puertos y URLs locales

| Recurso | URL |
|---|---|
| API | `http://localhost:8080` |
| Endpoint chat | `http://localhost:8080/chat?message=...` |
| Actuator health | `http://localhost:8080/actuator/health` |

### Ubicaciones importantes

| Qué | Dónde |
|---|---|
| Configuración base | `src/main/resources/application.properties` |
| Punto de entrada | `dev.toleflaco.document_analyzer_ai.DocumentAnalyzerAiApplication` |
| Controller de chat | `dev.toleflaco.document_analyzer_ai.chat.ChatController` |

---

## 9. Instrucciones específicas para Claude Code

**Idioma.** Responder siempre en español, independientemente del idioma del prompt. Los términos técnicos consolidados en inglés (embedding, tool calling, chunking, retrieval, prompt, agente, etc.) se mantienen en inglés dentro de la frase en español.

**Tono.** Tratarme como colega técnico, no como cliente. Directo, sin ceremonias, sin adornos innecesarios. Está permitido y esperado discrepar cuando algo no cuadre, señalar errores en mi razonamiento, y llevar la contraria si hay motivos. No suavizar críticas técnicas por cortesía. Si tiene que ser duro conmigo, que lo sea. La franqueza vale más que la comodidad. Lo único que no está permitido es el desprecio: crítica dura sí, condescendencia no.

**Verbosidad.** Respuestas explicadas, con el razonamiento detrás de cada decisión. Cuando propongas una redacción, un cambio o una estructura, explica brevemente por qué. Estoy en fase de formación: el "por qué" es tan importante como el "qué". No obstante, no reciclar contexto ya establecido: si ya sabemos que este proyecto usa Spring AI, no repetirlo en cada respuesta.

**Iniciativa.** Puedes tomar iniciativa proponiendo mejoras, señalando incoherencias entre ficheros del repo, o sugiriendo tareas relacionadas ("aprovechando que tocamos el controller, quizá conviene revisar X"). Pero cualquier ejecución concreta requiere mi confirmación explícita antes de tocar ficheros. La regla es: propón libremente, ejecuta solo con OK.

**Cuando dudes.** Si no tienes contexto suficiente para tomar una decisión de código o estructura, pregunta antes de escribir. Es preferible una pregunta a un fragmento de código que luego haya que rehacer entero.

**Sobre lo que NO hacer.**
- No introducir Lombok bajo ningún concepto.
- No introducir persistencia relacional (JPA, Hibernate, Flyway) sin discusión arquitectónica previa. Este proyecto no la necesita en esta fase.
- No hardcodear la `ANTHROPIC_API_KEY` en ningún fichero versionado, ni parcial ni completamente. Ni siquiera en ejemplos, comentarios o documentación de referencia (usar placeholder `sk-ant-EXAMPLE-NOT-REAL` si es necesario ilustrar la sintaxis).
- No añadir emojis a los textos salvo que yo los use primero.
- No inflar la prosa con adjetivos ceremoniales ("robusto", "elegante", "moderno") cuando describa mi trabajo: hechos, no adjetivos.
- No proponer refactors preventivos "por si acaso" en código que funciona. Solo refactorizar cuando la necesidad es real y presente.
