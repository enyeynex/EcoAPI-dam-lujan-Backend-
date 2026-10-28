package pe.upeu.ecorecicla.web.dto

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.springframework.data.domain.Page

// ---------- Formato comun (seccion 4.2) ----------

/** Formato de error de todo el servicio. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorRespuesta(
    val timestamp: String,
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val validationErrors: Map<String, String>? = null
)

/** Envoltorio de paginacion de todos los listados. */
data class Pagina<T>(
    val contenido: List<T>,
    val pagina: Int,
    val tamanio: Int,
    val totalElementos: Long,
    val totalPaginas: Int,
    val ultima: Boolean
) {
    companion object {
        fun <E, T> de(pagina: Page<E>, convertir: (E) -> T) = Pagina(
            contenido = pagina.content.map(convertir),
            pagina = pagina.number,
            tamanio = pagina.size,
            totalElementos = pagina.totalElements,
            totalPaginas = pagina.totalPages,
            ultima = pagina.isLast
        )
    }
}

// ---------- Autenticacion ----------

data class LoginRequest(
    @field:NotBlank(message = "El usuario es obligatorio")
    val usuario: String? = null,
    @field:NotBlank(message = "La contraseña es obligatoria")
    val clave: String? = null
)

data class LoginResponse(
    val token: String,
    val tipo: String,
    val expiraEnSegundos: Long,
    val usuario: String,
    val nombreCompleto: String,
    val rol: String,
    val privilegios: List<String>
)

data class UsuarioActualResponse(
    val usuario: String,
    val nombreCompleto: String,
    val rol: String,
    val privilegios: List<String>
)

// ---------- Puntos y rutas ----------

data class PuntoAcopioResponse(
    val id: Long,
    val razonSocial: String,
    val direccion: String,
    val zona: String,
    val materialHabitual: String
)

data class PuntoAcopioRequest(
    @field:NotBlank(message = "La razón social es obligatoria")
    @field:Size(max = 150, message = "Máximo 150 caracteres")
    val razonSocial: String? = null,
    @field:NotBlank(message = "La dirección es obligatoria")
    val direccion: String? = null,
    @field:NotBlank(message = "La zona es obligatoria")
    val zona: String? = null,
    @field:NotBlank(message = "El material habitual es obligatorio")
    val materialHabitual: String? = null
)

data class PuntoDeRutaResponse(
    val id: Long,
    val razonSocial: String,
    val direccion: String,
    val zona: String,
    val materialHabitual: String,
    val secuencia: Int,
    val atendido: Boolean,
    val motivoOmision: String?
)

data class RutaResponse(
    val id: Long,
    val fecha: String,
    val estado: String,
    val puntos: List<PuntoDeRutaResponse>
)

// ---------- Pesajes ----------

/** El cliente nunca envia el peso neto: lo calcula el servicio (RN-03). */
data class PesajeRequest(
    @field:NotNull(message = "La ruta es obligatoria")
    val rutaId: Long? = null,
    @field:NotNull(message = "El punto es obligatorio")
    val puntoId: Long? = null,
    @field:NotBlank(message = "El material es obligatorio")
    @field:Size(max = 40, message = "Máximo 40 caracteres")
    val material: String? = null,
    @field:NotNull(message = "El peso bruto es obligatorio")
    @field:Positive(message = "El peso bruto debe ser mayor que cero")
    val pesoBruto: Double? = null,
    @field:NotNull(message = "La tara es obligatoria")
    @field:PositiveOrZero(message = "La tara no puede ser negativa")
    val tara: Double? = null,
    @field:Size(max = 250, message = "Máximo 250 caracteres")
    val observacion: String? = null
)

data class CorreccionPesajeRequest(
    @field:NotBlank(message = "El material es obligatorio")
    @field:Size(max = 40, message = "Máximo 40 caracteres")
    val material: String? = null,
    @field:NotNull(message = "El peso bruto es obligatorio")
    @field:Positive(message = "El peso bruto debe ser mayor que cero")
    val pesoBruto: Double? = null,
    @field:NotNull(message = "La tara es obligatoria")
    @field:DecimalMin(value = "0.0", message = "La tara no puede ser negativa")
    val tara: Double? = null,
    @field:Size(max = 250, message = "Máximo 250 caracteres")
    val observacion: String? = null
)

data class PesajeResponse(
    val id: Long,
    val rutaId: Long,
    val puntoId: Long,
    val fechaHora: String,
    val material: String,
    val pesoBruto: Double,
    val tara: Double,
    val pesoNeto: Double,
    val observacion: String?,
    val operario: String,
    val anulado: Boolean,
    val motivoAnulacion: String?
)
