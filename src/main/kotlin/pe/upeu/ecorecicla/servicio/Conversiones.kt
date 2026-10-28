package pe.upeu.ecorecicla.servicio

import pe.upeu.ecorecicla.dominio.Pesaje
import pe.upeu.ecorecicla.dominio.PuntoAcopio
import pe.upeu.ecorecicla.web.dto.PesajeResponse
import pe.upeu.ecorecicla.web.dto.PuntoAcopioResponse
import java.time.format.DateTimeFormatter

internal val FORMATO_FECHA_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

internal fun PuntoAcopio.aRespuesta() = PuntoAcopioResponse(id!!, razonSocial, direccion, zona, materialHabitual)

internal fun Pesaje.aRespuesta() = PesajeResponse(
    id = id!!,
    rutaId = ruta.id!!,
    puntoId = punto.id!!,
    fechaHora = fechaHora.format(FORMATO_FECHA_HORA),
    material = material,
    pesoBruto = pesoBruto.toDouble(),
    tara = tara.toDouble(),
    pesoNeto = pesoNeto.toDouble(),
    observacion = observacion,
    operario = operario.username,
    anulado = anulado,
    motivoAnulacion = motivoAnulacion,
    humedadEstimada = humedadEstimada?.toDouble()
)
