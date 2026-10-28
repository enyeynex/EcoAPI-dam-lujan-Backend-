package pe.upeu.ecorecicla.servicio

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pe.upeu.ecorecicla.dominio.NoEncontradoException
import pe.upeu.ecorecicla.dominio.PuntoAcopio
import pe.upeu.ecorecicla.repositorio.PesajeRepository
import pe.upeu.ecorecicla.repositorio.PuntoAcopioRepository
import pe.upeu.ecorecicla.web.dto.Pagina
import pe.upeu.ecorecicla.web.dto.PesajeResponse
import pe.upeu.ecorecicla.web.dto.PuntoAcopioRequest
import pe.upeu.ecorecicla.web.dto.PuntoAcopioResponse

@Service
class PuntoServicio(
    private val puntos: PuntoAcopioRepository,
    private val pesajes: PesajeRepository
) {

    /** RF-B03: listado paginado con filtro opcional por zona. */
    @Transactional(readOnly = true)
    fun listar(zona: String?, pagina: Int, tamanio: Int): Pagina<PuntoAcopioResponse> {
        val pedido = PageRequest.of(pagina.coerceAtLeast(0), tamanio.coerceIn(1, 100), Sort.by("id"))
        val resultado = if (zona.isNullOrBlank()) puntos.findAll(pedido)
        else puntos.findByZonaIgnoreCase(zona.trim(), pedido)
        return Pagina.de(resultado) { it.aRespuesta() }
    }

    /** Pesajes de un punto, del mas reciente al mas antiguo. */
    @Transactional(readOnly = true)
    fun pesajesDe(puntoId: Long, pagina: Int, tamanio: Int): Pagina<PesajeResponse> {
        if (!puntos.existsById(puntoId)) throw NoEncontradoException("No existe el punto de acopio $puntoId")
        val pedido = PageRequest.of(pagina.coerceAtLeast(0), tamanio.coerceIn(1, 100))
        return Pagina.de(pesajes.findByPuntoIdOrderByFechaHoraDescIdDesc(puntoId, pedido)) { it.aRespuesta() }
    }

    @Transactional
    fun crear(peticion: PuntoAcopioRequest): PuntoAcopioResponse =
        puntos.save(
            PuntoAcopio(
                razonSocial = peticion.razonSocial!!.trim(),
                direccion = peticion.direccion!!.trim(),
                zona = peticion.zona!!.trim(),
                materialHabitual = peticion.materialHabitual!!.trim()
            )
        ).aRespuesta()
}
