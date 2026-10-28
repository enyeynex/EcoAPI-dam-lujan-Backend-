package pe.upeu.ecorecicla.dominio

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import pe.upeu.ecorecicla.seguridad.Usuario
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Pesaje de un punto en una ruta. El peso neto lo calcula el servicio
 * (RN-03) y la anulacion no borra la fila: la marca.
 */
@Entity
@Table(name = "pesaje")
class Pesaje(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruta_id")
    var ruta: Ruta = Ruta(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "punto_id")
    var punto: PuntoAcopio = PuntoAcopio(),

    @Column(nullable = false)
    var fechaHora: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false, length = 40)
    var material: String = "",

    @Column(nullable = false, precision = 10, scale = 2)
    var pesoBruto: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 10, scale = 2)
    var tara: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 10, scale = 2)
    var pesoNeto: BigDecimal = BigDecimal.ZERO,

    @Column(length = 250)
    var observacion: String? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operario_id")
    var operario: Usuario = Usuario(),

    @Column(nullable = false)
    var anulado: Boolean = false,

    @Column(length = 200)
    var motivoAnulacion: String? = null,

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
)
