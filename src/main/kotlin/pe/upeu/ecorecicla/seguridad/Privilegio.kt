package pe.upeu.ecorecicla.seguridad

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Permiso atomico (PESAJE_CREAR, RUTA_CERRAR...). La autorizacion se verifica
 * siempre contra el privilegio, nunca contra el nombre del rol.
 */
@Entity
@Table(name = "privilegio")
class Privilegio(
    @Column(nullable = false, unique = true, length = 40)
    var nombre: String = "",

    @Column(length = 120)
    var descripcion: String? = null,

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
)

/** Nombres de los privilegios del caso, para no escribir cadenas sueltas. */
object Privilegios {
    const val PUNTO_LEER = "PUNTO_LEER"
    const val PESAJE_LEER = "PESAJE_LEER"
    const val PESAJE_CREAR = "PESAJE_CREAR"
    const val PESAJE_EDITAR = "PESAJE_EDITAR"
    /** Celda «Todos» de la matriz: corregir pesajes de cualquier operario y fecha. */
    const val PESAJE_EDITAR_TODOS = "PESAJE_EDITAR_TODOS"
    const val PESAJE_ANULAR = "PESAJE_ANULAR"
    const val RUTA_CERRAR = "RUTA_CERRAR"
    const val PUNTO_GESTIONAR = "PUNTO_GESTIONAR"
}
