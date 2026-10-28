package pe.upeu.ecorecicla.seguridad

import jakarta.validation.Valid
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import pe.upeu.ecorecicla.config.JwtService
import pe.upeu.ecorecicla.dominio.CredencialesInvalidasException
import pe.upeu.ecorecicla.web.dto.LoginRequest
import pe.upeu.ecorecicla.web.dto.LoginResponse
import pe.upeu.ecorecicla.web.dto.UsuarioActualResponse

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val usuarios: UsuarioRepository,
    private val encoder: PasswordEncoder,
    private val jwt: JwtService
) {

    /** RF-B01: unica ruta publica. Valida contra la base con la contrasena cifrada. */
    @PostMapping("/login")
    @Transactional(readOnly = true)
    fun login(@Valid @RequestBody peticion: LoginRequest): LoginResponse {
        val usuario = usuarios.findByUsername(peticion.usuario!!.trim())
        if (usuario == null || !usuario.activo || !encoder.matches(peticion.clave, usuario.password)) {
            throw CredencialesInvalidasException()
        }
        val privilegios = usuario.privilegios().sorted()
        return LoginResponse(
            token = jwt.generar(usuario),
            tipo = "Bearer",
            expiraEnSegundos = jwt.vigenciaEnSegundos(),
            usuario = usuario.username,
            nombreCompleto = usuario.nombreCompleto,
            rol = usuario.rol.nombre,
            privilegios = privilegios
        )
    }

    /** RF-B07: datos y privilegios del usuario del token. */
    @GetMapping("/me")
    @Transactional(readOnly = true)
    fun yo(): UsuarioActualResponse {
        val actual = UsuarioAutenticado.actual()
        val usuario = usuarios.findById(actual.id).orElseThrow()
        return UsuarioActualResponse(
            usuario = usuario.username,
            nombreCompleto = usuario.nombreCompleto,
            rol = usuario.rol.nombre,
            privilegios = usuario.privilegios().sorted()
        )
    }
}
