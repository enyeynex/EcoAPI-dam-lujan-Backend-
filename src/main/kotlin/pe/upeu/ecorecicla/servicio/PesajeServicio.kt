package pe.upeu.ecorecicla.servicio

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pe.upeu.ecorecicla.dominio.NoEncontradoException
import pe.upeu.ecorecicla.dominio.Pesaje
import pe.upeu.ecorecicla.dominio.ReglaNegocioException
import pe.upeu.ecorecicla.dominio.Ruta
import pe.upeu.ecorecicla.dominio.SinPermisoSobreRecursoException
import pe.upeu.ecorecicla.dominio.ValidacionException
import pe.upeu.ecorecicla.repositorio.PesajeRepository
import pe.upeu.ecorecicla.repositorio.PuntoAcopioRepository
import pe.upeu.ecorecicla.repositorio.RutaRepository
import pe.upeu.ecorecicla.seguridad.Privilegios
import pe.upeu.ecorecicla.seguridad.UsuarioAutenticado
import pe.upeu.ecorecicla.seguridad.UsuarioRepository
import pe.upeu.ecorecicla.web.dto.CorreccionPesajeRequest
import pe.upeu.ecorecicla.web.dto.PesajeRequest
import pe.upeu.ecorecicla.web.dto.PesajeResponse
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Reglas de negocio de los pesajes. Viven aqui, en la capa que coordina la
 * operacion, y no en el controlador ni en la aplicacion movil.
 */
@Service
class PesajeServicio(
    private val pesajes: PesajeRepository,
    private val rutas: RutaRepository,
    private val puntos: PuntoAcopioRepository,
    private val usuarios: UsuarioRepository
) {

    /** RF-B04: aplica RN-01 y RN-03. */
    @Transactional
    fun registrar(peticion: PesajeRequest, usuario: UsuarioAutenticado): PesajeResponse {
        val ruta = rutas.findById(peticion.rutaId!!)
            .orElseThrow { NoEncontradoException("No existe la ruta ${peticion.rutaId}") }
        val punto = puntos.findById(peticion.puntoId!!)
            .orElseThrow { NoEncontradoException("No existe el punto de acopio ${peticion.puntoId}") }

        verificarRutaAbierta(ruta)                                   // RN-01
        if (ruta.puntos.none { it.punto.id == punto.id }) {
            throw ValidacionException("puntoId", "El punto no pertenece a la ruta")
        }
        val bruto = decimal(peticion.pesoBruto!!)
        val tara = decimal(peticion.tara!!)

        val pesaje = Pesaje(
            ruta = ruta,
            punto = punto,
            fechaHora = LocalDateTime.now().withNano(0),
            material = peticion.material!!.trim(),
            pesoBruto = bruto,
            tara = tara,
            pesoNeto = calcularNeto(bruto, tara),                    // RN-03
            observacion = peticion.observacion?.trim()?.ifBlank { null },
            humedadEstimada = peticion.humedadEstimada?.let(::decimal),
            operario = usuarios.getReferenceById(usuario.id)
        )
        return pesajes.save(pesaje).aRespuesta()
    }

    /** RF-B05 (correccion): aplica RN-01, RN-02 y RN-03. */
    @Transactional
    fun corregir(id: Long, peticion: CorreccionPesajeRequest, usuario: UsuarioAutenticado): PesajeResponse {
        val pesaje = buscar(id)
        if (pesaje.anulado) throw ReglaNegocioException("El pesaje está anulado")
        verificarRutaAbierta(pesaje.ruta)                            // RN-01
        verificarPropiedad(pesaje, usuario)                          // RN-02

        val bruto = decimal(peticion.pesoBruto!!)
        val tara = decimal(peticion.tara!!)
        pesaje.pesoNeto = calcularNeto(bruto, tara)                  // RN-03
        pesaje.pesoBruto = bruto
        pesaje.tara = tara
        pesaje.material = peticion.material!!.trim()
        pesaje.observacion = peticion.observacion?.trim()?.ifBlank { null }
        pesaje.humedadEstimada = peticion.humedadEstimada?.let(::decimal)
        return pesaje.aRespuesta()
    }

    /** RF-B05 (anulacion): marca el pesaje, nunca lo elimina de la base. */
    @Transactional
    fun anular(id: Long, motivo: String?) {
        val pesaje = buscar(id)
        if (pesaje.anulado) throw ReglaNegocioException("El pesaje ya está anulado")
        pesaje.anulado = true
        pesaje.motivoAnulacion = motivo?.trim()?.ifBlank { null } ?: "Anulado por supervisor"
    }

    private fun buscar(id: Long): Pesaje =
        pesajes.findById(id).orElseThrow { NoEncontradoException("No existe el pesaje $id") }

    /** RN-01: en una ruta cerrada no se registra ni se corrige. */
    private fun verificarRutaAbierta(ruta: Ruta) {
        if (ruta.cerrada()) {
            throw ReglaNegocioException(
                "La ruta del ${ruta.fecha} está cerrada: no se pueden registrar ni corregir pesajes"
            )
        }
    }

    /**
     * RN-02: sin el privilegio PESAJE_EDITAR_TODOS solo se corrigen los
     * pesajes propios y del mismo dia. Se decide por privilegio, no por rol.
     */
    private fun verificarPropiedad(pesaje: Pesaje, usuario: UsuarioAutenticado) {
        if (usuario.tiene(Privilegios.PESAJE_EDITAR_TODOS)) return
        val propio = pesaje.operario.id == usuario.id
        val deHoy = pesaje.fechaHora.toLocalDate() == LocalDate.now()
        if (!propio || !deHoy) {
            throw SinPermisoSobreRecursoException("Solo puede corregir sus propios pesajes del día")
        }
    }

    /** RN-03: el neto lo calcula el servicio y debe ser mayor que cero. */
    private fun calcularNeto(bruto: BigDecimal, tara: BigDecimal): BigDecimal {
        val neto = bruto.subtract(tara)
        if (neto.signum() <= 0) {
            throw ValidacionException("tara", "El peso neto (bruto - tara) debe ser mayor que cero")
        }
        return neto
    }

    private fun decimal(valor: Double): BigDecimal = BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP)
}
