package pe.upeu.ecorecicla.web

import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import pe.upeu.ecorecicla.seguridad.UsuarioAutenticado
import pe.upeu.ecorecicla.servicio.PesajeServicio
import pe.upeu.ecorecicla.servicio.PuntoServicio
import pe.upeu.ecorecicla.servicio.RutaServicio
import pe.upeu.ecorecicla.web.dto.CorreccionPesajeRequest
import pe.upeu.ecorecicla.web.dto.Pagina
import pe.upeu.ecorecicla.web.dto.PesajeRequest
import pe.upeu.ecorecicla.web.dto.PesajeResponse
import pe.upeu.ecorecicla.web.dto.PuntoAcopioRequest
import pe.upeu.ecorecicla.web.dto.PuntoAcopioResponse
import pe.upeu.ecorecicla.web.dto.RutaResponse
import java.time.LocalDate

/*
 * Controladores delgados: reciben, validan el formato y delegan. Ni roles ni
 * privilegios aparecen aqui; los exige SecurityConfig y las reglas viven en
 * la capa servicio.
 */

@RestController
@RequestMapping("/api/v1/rutas")
class RutaController(private val servicio: RutaServicio) {

    @GetMapping("/hoy")
    fun hoy(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fecha: LocalDate?
    ): RutaResponse = servicio.rutaDelDia(UsuarioAutenticado.actual(), fecha ?: LocalDate.now())

    @PostMapping("/{id}/cierre")
    fun cerrar(@PathVariable id: Long): RutaResponse = servicio.cerrar(id)
}

@RestController
@RequestMapping("/api/v1/puntos")
class PuntoController(private val servicio: PuntoServicio) {

    @GetMapping
    fun listar(
        @RequestParam(required = false) zona: String?,
        @RequestParam(defaultValue = "0") pagina: Int,
        @RequestParam(defaultValue = "20") tamanio: Int
    ): Pagina<PuntoAcopioResponse> = servicio.listar(zona, pagina, tamanio)

    @GetMapping("/{id}/pesajes")
    fun pesajes(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "0") pagina: Int,
        @RequestParam(defaultValue = "20") tamanio: Int
    ): Pagina<PesajeResponse> = servicio.pesajesDe(id, pagina, tamanio)

    @PostMapping
    fun crear(@Valid @RequestBody peticion: PuntoAcopioRequest): ResponseEntity<PuntoAcopioResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(peticion))
}

@RestController
@RequestMapping("/api/v1/pesajes")
class PesajeController(private val servicio: PesajeServicio) {

    @PostMapping
    fun registrar(@Valid @RequestBody peticion: PesajeRequest): ResponseEntity<PesajeResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(servicio.registrar(peticion, UsuarioAutenticado.actual()))

    @PutMapping("/{id}")
    fun corregir(@PathVariable id: Long, @Valid @RequestBody peticion: CorreccionPesajeRequest): PesajeResponse =
        servicio.corregir(id, peticion, UsuarioAutenticado.actual())

    /** Anulacion: 204 sin cuerpo. El pesaje queda marcado, no se borra. */
    @DeleteMapping("/{id}")
    fun anular(@PathVariable id: Long, @RequestParam(required = false) motivo: String?): ResponseEntity<Void> {
        servicio.anular(id, motivo)
        return ResponseEntity.noContent().build()
    }
}
