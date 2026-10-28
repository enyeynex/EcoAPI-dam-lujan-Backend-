package pe.upeu.ecorecicla.config

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import pe.upeu.ecorecicla.seguridad.Usuario
import java.util.Date
import javax.crypto.SecretKey

/**
 * Firma y verifica los JWT con HMAC-SHA256. El token transporta el usuario
 * (sub), su id (uid), el rol y los privilegios; vence en una hora y no se
 * renueva.
 */
@Service
class JwtService(
    @Value("\${app.jwt.secret}") secreto: String,
    @Value("\${app.jwt.expiracion-minutos}") private val expiracionMinutos: Long
) {

    private val clave: SecretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secreto))

    fun vigenciaEnSegundos(): Long = expiracionMinutos * 60

    fun generar(usuario: Usuario): String {
        val ahora = Date()
        return Jwts.builder()
            .subject(usuario.username)
            .claim("uid", usuario.id)
            .claim("rol", usuario.rol.nombre)
            .claim("privilegios", usuario.privilegios().sorted())
            .issuedAt(ahora)
            .expiration(Date(ahora.time + vigenciaEnSegundos() * 1000))
            .signWith(clave)
            .compact()
    }

    /** Lanza ExpiredJwtException si vencio y JwtException si la firma no coincide. */
    fun leer(token: String): Claims =
        Jwts.parser().verifyWith(clave).build().parseSignedClaims(token).payload
}
