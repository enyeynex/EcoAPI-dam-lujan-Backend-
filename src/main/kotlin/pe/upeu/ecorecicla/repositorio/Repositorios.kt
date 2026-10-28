package pe.upeu.ecorecicla.repositorio

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import pe.upeu.ecorecicla.dominio.Pesaje
import pe.upeu.ecorecicla.dominio.PuntoAcopio
import pe.upeu.ecorecicla.dominio.Ruta
import java.time.LocalDate

interface PuntoAcopioRepository : JpaRepository<PuntoAcopio, Long> {
    fun findByZonaIgnoreCase(zona: String, pagina: Pageable): Page<PuntoAcopio>
}

interface RutaRepository : JpaRepository<Ruta, Long> {

    /** Ruta del dia asignada al usuario, sea como operario o como supervisor. */
    @Query(
        """select r from Ruta r
           where r.fecha = :fecha and (r.operario.id = :usuarioId or r.supervisor.id = :usuarioId)
           order by r.id desc"""
    )
    fun asignadas(@Param("fecha") fecha: LocalDate, @Param("usuarioId") usuarioId: Long): List<Ruta>

    fun existsByFechaAndOperarioId(fecha: LocalDate, operarioId: Long): Boolean
}

interface PesajeRepository : JpaRepository<Pesaje, Long> {

    fun findByPuntoIdOrderByFechaHoraDescIdDesc(puntoId: Long, pagina: Pageable): Page<Pesaje>

    fun existsByRutaIdAndPuntoIdAndAnuladoFalse(rutaId: Long, puntoId: Long): Boolean
}
