package pe.upeu.ecorecicla.config

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.stereotype.Component
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import pe.upeu.ecorecicla.dominio.CredencialesInvalidasException
import pe.upeu.ecorecicla.dominio.NoEncontradoException
import pe.upeu.ecorecicla.dominio.ReglaNegocioException
import pe.upeu.ecorecicla.dominio.SinPermisoSobreRecursoException
import pe.upeu.ecorecicla.dominio.ValidacionException
import pe.upeu.ecorecicla.web.dto.ErrorRespuesta
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Arma el formato de error comun del servicio (seccion 4.2). */
@Component
class EscritorDeErrores(private val mapper: ObjectMapper) {

    fun cuerpo(
        request: HttpServletRequest,
        estado: HttpStatus,
        mensaje: String,
        campos: Map<String, String>? = null
    ) = ErrorRespuesta(
        timestamp = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(),
        status = estado.value(),
        error = estado.reasonPhrase,
        message = mensaje,
        path = request.requestURI,
        validationErrors = campos?.takeIf { it.isNotEmpty() }
    )

    /** Para el filtro de seguridad, que responde antes de llegar a los controladores. */
    fun escribir(request: HttpServletRequest, response: HttpServletResponse, estado: HttpStatus, mensaje: String) {
        response.status = estado.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        mapper.writeValue(response.writer, cuerpo(request, estado, mensaje))
    }
}

/**
 * Traduce cada excepcion a su codigo: 400 validacion, 401 credenciales, 403
 * regla de propiedad (RN-02), 404 no encontrado, 409 regla de negocio y 500.
 */
@RestControllerAdvice
class ManejadorDeErrores(private val errores: EscritorDeErrores) {

    private val log = LoggerFactory.getLogger(javaClass)

    private fun respuesta(
        request: HttpServletRequest,
        estado: HttpStatus,
        mensaje: String,
        campos: Map<String, String>? = null
    ): ResponseEntity<ErrorRespuesta> =
        ResponseEntity.status(estado).body(errores.cuerpo(request, estado, mensaje, campos))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validacion(e: MethodArgumentNotValidException, request: HttpServletRequest) =
        respuesta(
            request, HttpStatus.BAD_REQUEST, "Datos invalidos",
            e.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "Valor no valido") }
        )

    @ExceptionHandler(ValidacionException::class)
    fun validacionDeNegocio(e: ValidacionException, request: HttpServletRequest) =
        respuesta(request, HttpStatus.BAD_REQUEST, "Datos invalidos", mapOf(e.campo to e.mensaje))

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        MissingServletRequestParameterException::class
    )
    fun peticionMalFormada(e: Exception, request: HttpServletRequest) =
        respuesta(request, HttpStatus.BAD_REQUEST, "La petición no tiene el formato esperado")

    @ExceptionHandler(CredencialesInvalidasException::class)
    fun credenciales(e: CredencialesInvalidasException, request: HttpServletRequest) =
        respuesta(request, HttpStatus.UNAUTHORIZED, e.message!!)

    @ExceptionHandler(SinPermisoSobreRecursoException::class)
    fun sinPermiso(e: SinPermisoSobreRecursoException, request: HttpServletRequest) =
        respuesta(request, HttpStatus.FORBIDDEN, e.message!!)

    @ExceptionHandler(NoEncontradoException::class)
    fun noEncontrado(e: NoEncontradoException, request: HttpServletRequest) =
        respuesta(request, HttpStatus.NOT_FOUND, e.message!!)

    @ExceptionHandler(NoResourceFoundException::class)
    fun rutaInexistente(e: NoResourceFoundException, request: HttpServletRequest) =
        respuesta(request, HttpStatus.NOT_FOUND, "Recurso no encontrado")

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun metodo(e: HttpRequestMethodNotSupportedException, request: HttpServletRequest) =
        respuesta(request, HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido")

    @ExceptionHandler(ReglaNegocioException::class)
    fun conflicto(e: ReglaNegocioException, request: HttpServletRequest) =
        respuesta(request, HttpStatus.CONFLICT, e.message!!)

    @ExceptionHandler(Exception::class)
    fun inesperado(e: Exception, request: HttpServletRequest): ResponseEntity<ErrorRespuesta> {
        log.error("Error no controlado en {}", request.requestURI, e)
        return respuesta(request, HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor")
    }
}
