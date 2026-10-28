package pe.upeu.ecorecicla.config

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.filter.OncePerRequestFilter
import pe.upeu.ecorecicla.seguridad.UsuarioAutenticado
import pe.upeu.ecorecicla.seguridad.UsuarioRepository

/**
 * Lee la cabecera «Authorization: Bearer ...», verifica la firma y la vigencia
 * del token y deja al usuario en el contexto con sus privilegios como
 * authorities. Si el token falla no responde aqui: guarda el motivo y deja que
 * el AuthenticationEntryPoint conteste 401 con el formato comun.
 */
@Component
class JwtAuthenticationFilter(
    private val jwt: JwtService,
    private val usuarios: UsuarioRepository,
    private val transacciones: TransactionTemplate
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val cabecera = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (cabecera != null && cabecera.startsWith("Bearer ")) {
            autenticar(cabecera.removePrefix("Bearer ").trim(), request)
        }
        chain.doFilter(request, response)
    }

    private fun autenticar(token: String, request: HttpServletRequest) {
        try {
            val claims = jwt.leer(token)
            val autenticado = transacciones.execute {
                usuarios.findByUsername(claims.subject)
                    ?.takeIf { it.activo }
                    ?.let { UsuarioAutenticado(it.id!!, it.username, it.rol.nombre, it.privilegios()) }
            }
            if (autenticado == null) {
                request.setAttribute(MOTIVO_401, MOTIVO_INVALIDO)
                return
            }
            val authorities = autenticado.privilegios.map { SimpleGrantedAuthority(it) }
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(autenticado, null, authorities)
        } catch (vencido: ExpiredJwtException) {
            request.setAttribute(MOTIVO_401, MOTIVO_EXPIRADO)
        } catch (alterado: JwtException) {
            request.setAttribute(MOTIVO_401, MOTIVO_INVALIDO)
        } catch (malformado: IllegalArgumentException) {
            request.setAttribute(MOTIVO_401, MOTIVO_INVALIDO)
        }
    }

    companion object {
        const val MOTIVO_401 = "eco.motivo401"
        const val MOTIVO_EXPIRADO = "expirado"
        const val MOTIVO_INVALIDO = "invalido"
    }
}
