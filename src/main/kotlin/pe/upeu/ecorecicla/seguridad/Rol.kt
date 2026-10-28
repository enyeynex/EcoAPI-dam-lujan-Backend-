package pe.upeu.ecorecicla.seguridad

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table

/** Un rol es un conjunto de privilegios guardado en la base de datos. */
@Entity
@Table(name = "rol")
class Rol(
    @Column(nullable = false, unique = true, length = 40)
    var nombre: String = "",

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "rol_privilegio",
        joinColumns = [JoinColumn(name = "rol_id")],
        inverseJoinColumns = [JoinColumn(name = "privilegio_id")]
    )
    var privilegios: MutableSet<Privilegio> = mutableSetOf(),

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
)
