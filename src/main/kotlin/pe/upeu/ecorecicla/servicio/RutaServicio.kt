package pe.upeu.ecorecicla.servicio

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pe.upeu.ecorecicla.dominio.EstadoRuta
import pe.upeu.ecorecicla.dominio.NoEncontradoException
import pe.upeu.ecorecicla.dominio.ReglaNegocioException
import pe.upeu.ecorecicla.dominio.Ruta
import pe.upeu.ecorecicla.repositorio.PesajeRepository
import pe.upeu.ecorecicla.repositorio.RutaRepository
import pe.upeu.ecorecicla.seguridad.UsuarioAutenticado
import pe.upeu.ecorecicla.web.dto.PuntoDeRutaResponse
import pe.upeu.ecorecicla.web.dto.RutaResponse
import java.time.LocalDate

@Service
class RutaServicio(
    private val rutas: RutaRepository,
    private val pesajes: PesajeRepository
) {

    /** RF-B02: ruta asignada al usuario autenticado para la fecha. */
    @Transactional(readOnly = true)
    fun rutaDelDia(usuario: UsuarioAutenticado, fecha: LocalDate): RutaResponse {
        val ruta = rutas.asignadas(fecha, usuario.id).firstOrNull()
            ?: throw NoEncontradoException("No tiene una ruta asignada para $fecha")
        return aRespuesta(ruta)
    }

    /**
     * RF-B06 + RN-04: no se cierra mientras algun punto planificado no tenga
     * un pesaje vigente ni un motivo de omision.
     */
    @Transactional
    fun cerrar(rutaId: Long): RutaResponse {
        val ruta = rutas.findById(rutaId).orElseThrow { NoEncontradoException("No existe la ruta $rutaId") }
        if (ruta.cerrada()) {
            throw ReglaNegocioException("La ruta del ${ruta.fecha} ya está cerrada")
        }
        val faltan = ruta.puntos.count { !atendido(ruta, it.punto.id!!, it.motivoOmision) }
        if (faltan > 0) {
            val texto = if (faltan == 1) "falta 1 punto" else "faltan $faltan puntos"
            throw ReglaNegocioException("No se puede cerrar la ruta: $texto por pesar u omitir")
        }
        ruta.estado = EstadoRuta.CERRADA
        return aRespuesta(ruta)
    }

    private fun atendido(ruta: Ruta, puntoId: Long, motivoOmision: String?): Boolean =
        motivoOmision != null || pesajes.existsByRutaIdAndPuntoIdAndAnuladoFalse(ruta.id!!, puntoId)

    private fun aRespuesta(ruta: Ruta) = RutaResponse(
        id = ruta.id!!,
        fecha = ruta.fecha.toString(),
        estado = ruta.estado.name,
        puntos = ruta.puntos.sortedBy { it.secuencia }.map {
            val punto = it.punto
            PuntoDeRutaResponse(
                id = punto.id!!,
                razonSocial = punto.razonSocial,
                direccion = punto.direccion,
                zona = punto.zona,
                materialHabitual = punto.materialHabitual,
                secuencia = it.secuencia,
                atendido = atendido(ruta, punto.id!!, it.motivoOmision),
                motivoOmision = it.motivoOmision
            )
        }
    )
}
