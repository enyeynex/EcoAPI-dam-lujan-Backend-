package pe.upeu.ecorecicla.seguridad

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "usuario")
class Usuario(
    @Column(nullable = false, unique = true, length = 40)
    var username: String = "",

    /** Hash BCrypt: la contrasena nunca se guarda en claro. */
    @Column(nullable = false, length = 100)
    var password: String = "",

    @Column(nullable = false, length = 120)
    var nombreCompleto: String = "",

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "rol_id")
    var rol: Rol = Rol(),

    @Column(nullable = false)
    var activo: Boolean = true,

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
) {
    fun privilegios(): Set<String> = rol.privilegios.map { it.nombre }.toSet()
}
