package pe.upeu.ecorecicla.config

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import pe.upeu.ecorecicla.dominio.EstadoRuta
import pe.upeu.ecorecicla.dominio.Pesaje
import pe.upeu.ecorecicla.dominio.PuntoAcopio
import pe.upeu.ecorecicla.dominio.Ruta
import pe.upeu.ecorecicla.dominio.RutaPunto
import pe.upeu.ecorecicla.repositorio.PesajeRepository
import pe.upeu.ecorecicla.repositorio.PuntoAcopioRepository
import pe.upeu.ecorecicla.repositorio.RutaRepository
import pe.upeu.ecorecicla.seguridad.Privilegio
import pe.upeu.ecorecicla.seguridad.PrivilegioRepository
import pe.upeu.ecorecicla.seguridad.Privilegios
import pe.upeu.ecorecicla.seguridad.Rol
import pe.upeu.ecorecicla.seguridad.RolRepository
import pe.upeu.ecorecicla.seguridad.Usuario
import pe.upeu.ecorecicla.seguridad.UsuarioRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Datos semilla del anexo 10. La matriz de privilegios vive aqui, en la base:
 * para un rol nuevo basta agregarlo con sus privilegios.
 */
@Component
class DatosSemilla(
    private val privilegios: PrivilegioRepository,
    private val roles: RolRepository,
    private val usuarios: UsuarioRepository,
    private val puntos: PuntoAcopioRepository,
    private val rutas: RutaRepository,
    private val pesajes: PesajeRepository,
    private val encoder: PasswordEncoder
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(vararg args: String) {
        if (usuarios.count() == 0L) {
            sembrarTodo()
            log.info("Datos semilla cargados")
        }
        asegurarRutaDeHoy()
    }

    private fun sembrarTodo() {
        val p = listOf(
            Privilegios.PUNTO_LEER to "Consultar la ruta y los puntos de acopio",
            Privilegios.PESAJE_LEER to "Consultar los pesajes de un punto",
            Privilegios.PESAJE_CREAR to "Registrar un pesaje nuevo",
            Privilegios.PESAJE_EDITAR to "Corregir sus propios pesajes del día",
            Privilegios.PESAJE_EDITAR_TODOS to "Corregir pesajes de cualquier operario y fecha",
            Privilegios.PESAJE_ANULAR to "Anular un pesaje",
            Privilegios.RUTA_CERRAR to "Cerrar la ruta del día",
            Privilegios.PUNTO_GESTIONAR to "Crear puntos de acopio y asignar rutas"
        ).associate { (nombre, descripcion) -> nombre to privilegios.save(Privilegio(nombre, descripcion)) }

        fun rol(nombre: String, vararg nombres: String) =
            roles.save(Rol(nombre, nombres.map { p.getValue(it) }.toMutableSet()))

        val operario = rol(
            "OPERARIO",
            Privilegios.PUNTO_LEER, Privilegios.PESAJE_LEER, Privilegios.PESAJE_CREAR, Privilegios.PESAJE_EDITAR
        )
        val supervisor = rol(
            "SUPERVISOR",
            Privilegios.PUNTO_LEER, Privilegios.PESAJE_LEER, Privilegios.PESAJE_CREAR, Privilegios.PESAJE_EDITAR,
            Privilegios.PESAJE_EDITAR_TODOS, Privilegios.PESAJE_ANULAR, Privilegios.RUTA_CERRAR
        )
        val administrador = rol(
            "ADMINISTRADOR",
            Privilegios.PUNTO_LEER, Privilegios.PESAJE_LEER, Privilegios.PUNTO_GESTIONAR
        )

        val clave = encoder.encode("Eco2026*")
        val operario1 = usuarios.save(Usuario("operario1", clave, "Luis Quispe Mamani", operario))
        val operario2 = usuarios.save(Usuario("operario2", clave, "Rosa Huamán Torres", operario))
        val supervisor1 = usuarios.save(Usuario("supervisor1", clave, "Carmen Vargas Ríos", supervisor))
        usuarios.save(Usuario("admin", clave, "Jorge Salazar Paredes", administrador))

        val padron = listOf(
            PuntoAcopio("Distribuidora Los Olivos S.A.C.", "Av. Carlos Izaguirre 1250, Los Olivos", "Norte", "Cartón"),
            PuntoAcopio("Industrias Plásticas Comas S.A.", "Av. Túpac Amaru 4120, Comas", "Norte", "Plástico PET"),
            PuntoAcopio("Panificadora San Martín E.I.R.L.", "Jr. Las Begonias 340, San Martín de Porres", "Norte", "Cartón"),
            PuntoAcopio("Textiles Independencia S.A.C.", "Av. Industrial 780, Independencia", "Norte", "Papel"),
            PuntoAcopio("Imprenta Grafinorte S.A.C.", "Av. Naranjal 1505, Los Olivos", "Norte", "Papel"),
            PuntoAcopio("Bodegas Unidas del Cercado S.A.", "Jr. Ica 455, Cercado de Lima", "Centro", "Vidrio"),
            PuntoAcopio("Hotel Plaza Lima S.A.C.", "Jr. de la Unión 920, Cercado de Lima", "Centro", "Plástico PET"),
            PuntoAcopio("Oficinas Corporativas Breña S.A.", "Av. Arica 1120, Breña", "Centro", "Papel"),
            PuntoAcopio("Restaurante El Rímac E.I.R.L.", "Jr. Trujillo 210, Rímac", "Centro", "Vidrio"),
            PuntoAcopio("Clínica Santa Beatriz S.A.C.", "Av. Arequipa 1440, Lince", "Sur", "Plástico PET"),
            PuntoAcopio("Supermercado Surquillo S.A.", "Av. Angamos Este 2205, Surquillo", "Sur", "Cartón"),
            PuntoAcopio("Metalmecánica Chorrillos S.A.C.", "Av. Defensores del Morro 1830, Chorrillos", "Sur", "Metal")
        ).map { puntos.save(it) }

        // Ruta de ayer: cerrada y con todos sus puntos pesados (demuestra RN-01).
        val ayer = LocalDate.now().minusDays(1)
        val rutaAyer = rutas.save(nuevaRuta(ayer, EstadoRuta.CERRADA, operario1, supervisor1, padron.subList(6, 12)))
        padron.subList(6, 12).forEachIndexed { i, punto ->
            pesaje(rutaAyer, punto, ayer.atTime(8 + i, 15), punto.materialHabitual, 60.0 + i * 12, 4.5, operario1)
        }
        pesaje(
            rutaAyer, padron[6], ayer.atTime(8, 5), "Plástico PET", 50.0, 4.5, operario1,
            anulado = "Se pesó con la tara equivocada"
        )

        // Ruta de hoy: abierta, 3 puntos pesados y 3 sin pesar (demuestra RN-04).
        val hoy = LocalDate.now()
        val rutaHoy = rutas.save(nuevaRuta(hoy, EstadoRuta.ABIERTA, operario1, supervisor1, padron.subList(0, 6)))
        pesaje(rutaHoy, padron[0], hoy.atTime(7, 40), "Cartón", 85.5, 6.0, operario1)
        pesaje(rutaHoy, padron[0], hoy.atTime(7, 45), "Papel", 22.0, 2.0, operario1)
        pesaje(rutaHoy, padron[0], hoy.atTime(7, 50), "Cartón", 30.0, 3.0, operario1)
        // Pesajes de otro operario (demuestra RN-02).
        pesaje(rutaHoy, padron[1], hoy.atTime(8, 20), "Plástico PET", 48.0, 3.5, operario2)
        pesaje(rutaHoy, padron[1], hoy.atTime(8, 25), "Plástico PET", 26.4, 3.5, operario2)
        pesaje(rutaHoy, padron[2], hoy.atTime(8, 55), "Cartón", 64.0, 5.0, operario1)
        pesaje(rutaHoy, padron[2], hoy.atTime(9, 0), "Papel", 18.0, 2.0, supervisor1)
        pesaje(rutaHoy, padron[2], hoy.atTime(9, 5), "Cartón", 12.0, 2.0, operario1)
    }

    /** En cualquier dia de demostracion operario1 tiene una ruta abierta. */
    private fun asegurarRutaDeHoy() {
        val operario1 = usuarios.findByUsername("operario1") ?: return
        val hoy = LocalDate.now()
        if (rutas.existsByFechaAndOperarioId(hoy, operario1.id!!)) return
        val supervisor1 = usuarios.findByUsername("supervisor1")
        val planificados = puntos.findAll().sortedBy { it.id }.take(6)
        rutas.save(nuevaRuta(hoy, EstadoRuta.ABIERTA, operario1, supervisor1, planificados))
        log.info("Se creó la ruta del día {} para operario1", hoy)
    }

    private fun nuevaRuta(
        fecha: LocalDate,
        estado: EstadoRuta,
        operario: Usuario,
        supervisor: Usuario?,
        planificados: List<PuntoAcopio>
    ): Ruta {
        val ruta = Ruta(fecha = fecha, estado = estado, operario = operario, supervisor = supervisor)
        planificados.forEachIndexed { i, punto ->
            ruta.puntos.add(RutaPunto(ruta = ruta, punto = punto, secuencia = i + 1))
        }
        return ruta
    }

    private fun pesaje(
        ruta: Ruta,
        punto: PuntoAcopio,
        cuando: LocalDateTime,
        material: String,
        bruto: Double,
        tara: Double,
        operario: Usuario,
        anulado: String? = null
    ) {
        val b = BigDecimal.valueOf(bruto).setScale(2)
        val t = BigDecimal.valueOf(tara).setScale(2)
        pesajes.save(
            Pesaje(
                ruta = ruta, punto = punto, fechaHora = cuando, material = material,
                pesoBruto = b, tara = t, pesoNeto = b.subtract(t), operario = operario,
                anulado = anulado != null, motivoAnulacion = anulado
            )
        )
    }
}
