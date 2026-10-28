package pe.upeu.ecorecicla.seguridad

import org.springframework.security.core.context.SecurityContextHolder

/**
 * Lo que el filtro JWT deja en el contexto de seguridad. Los privilegios se
 * leen de la base en cada peticion: si a un rol se le quita un privilegio,
 * el cambio rige sin esperar a que el token venza.
 */
data class UsuarioAutenticado(
    val id: Long,
    val username: String,
    val rol: String,
    val privilegios: Set<String>
) {
    fun tiene(privilegio: String): Boolean = privilegio in privilegios

    companion object {
        fun actual(): UsuarioAutenticado =
            SecurityContextHolder.getContext().authentication?.principal as? UsuarioAutenticado
                ?: throw IllegalStateException("No hay un usuario autenticado")
    }
}
