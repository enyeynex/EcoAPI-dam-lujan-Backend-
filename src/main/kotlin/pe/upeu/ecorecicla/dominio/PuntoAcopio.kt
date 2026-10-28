package pe.upeu.ecorecicla.dominio

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "punto_acopio")
class PuntoAcopio(
    @Column(nullable = false, length = 150)
    var razonSocial: String = "",

    @Column(nullable = false, length = 200)
    var direccion: String = "",

    @Column(nullable = false, length = 40)
    var zona: String = "",

    @Column(nullable = false, length = 40)
    var materialHabitual: String = "",

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
)
