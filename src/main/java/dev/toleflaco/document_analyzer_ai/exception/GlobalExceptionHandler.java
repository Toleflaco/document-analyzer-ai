package dev.toleflaco.document_analyzer_ai.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import tools.jackson.core.JacksonException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MultipartException.class)
    public ProblemDetail handleMultipartException(MultipartException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "La petición debe ser multipart/form-data con un fichero 'file' adjunto."
        );
        problem.setTitle("Petición multipart inválida");
        return problem;
    }

    @ExceptionHandler(JacksonException.class)
    public ProblemDetail handleLlmOutputParsing(JacksonException ex, HttpServletRequest request) {
        String path = request.getRequestURI();
        log.error("llm response could not be parsed path={}", path, ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "El modelo devolvió una respuesta con formato inesperado. Se recomienda reintentar la petición."
        );
        problem.setTitle("Respuesta de LLM no procesable");
        return problem;
    }
}
