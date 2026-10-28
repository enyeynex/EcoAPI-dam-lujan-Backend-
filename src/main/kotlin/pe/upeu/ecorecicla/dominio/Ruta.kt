package pe.upeu.ecorecicla.dominio

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import pe.upeu.ecorecicla.seguridad.Usuario
import java.time.LocalDate

enum class EstadoRuta { ABIERTA, CERRADA }

/** Ruta de un dia: los puntos que el operario visita, en orden. */
@Entity
@Table(name = "ruta")
class Ruta(
    @Column(nullable = false)
    var fecha: LocalDate = LocalDate.now(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var estado: EstadoRuta = EstadoRuta.ABIERTA,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operario_id")
    var operario: Usuario = Usuario(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id")
    var supervisor: Usuario? = null,

    @OneToMany(mappedBy = "ruta", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("secuencia ASC")
    var puntos: MutableList<RutaPunto> = mutableListOf(),

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
) {
    fun cerrada() = estado == EstadoRuta.CERRADA
}

/** Un punto planificado dentro de la ruta, con su orden de visita. */
@Entity
@Table(name = "ruta_punto")
class RutaPunto(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruta_id")
    var ruta: Ruta = Ruta(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "punto_id")
    var punto: PuntoAcopio = PuntoAcopio(),

    @Column(nullable = false)
    var secuencia: Int = 0,

    /** Motivo por el que no se peso (empresa cerrada, sin material...). */
    @Column(length = 200)
    var motivoOmision: String? = null,

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
)
