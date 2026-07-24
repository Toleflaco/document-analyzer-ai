# Document Analyzer AI · Spring AI + Anthropic Claude

API de análisis de documentos con Spring Boot y Spring AI. Proyecto de estudio en fase inicial: hoy es un endpoint de chat funcional, no un analizador de documentos todavía.

## Contexto

Este proyecto es el Proyecto 1 del [AI Engineer Roadmap · Java + Spring AI](https://github.com/Toleflaco/ai-engineer-roadmap-java), un roadmap de formación como AI Engineer en Java. El repo hub contiene el roadmap completo, los cuatro proyectos entregables y la bitácora de progreso.

## Stack técnico

- Java 21
- Spring Boot 4.1.0
- Spring AI 2.0.0
- Anthropic Claude (vía `spring-ai-starter-model-anthropic`)

## Estado actual

Fase 1 del roadmap, en curso.

**Hecho:**
- Endpoint `/chat` funcional, con `ChatClient` invocando a Claude de forma stateless.

**Pendiente:**
- Memoria de conversación (`ChatMemory`).
- Outputs estructurados (extracción de datos de documentos).
- Soporte multimodal (PDF, Word).
- RAG con embeddings.

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

4. Arranca la aplicación:

   ```bash
   ./mvnw spring-boot:run
   ```

   El servidor levanta en `http://localhost:8080`.

5. Prueba el endpoint:

   ```bash
   curl -G "http://localhost:8080/chat" --data-urlencode "message=¿Qué es Spring AI?"
   ```
