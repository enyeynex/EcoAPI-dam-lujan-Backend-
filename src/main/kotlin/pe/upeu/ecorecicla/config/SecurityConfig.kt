package pe.upeu.ecorecicla.config

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import pe.upeu.ecorecicla.seguridad.Privilegios.PESAJE_ANULAR
import pe.upeu.ecorecicla.seguridad.Privilegios.PESAJE_CREAR
import pe.upeu.ecorecicla.seguridad.Privilegios.PESAJE_EDITAR
import pe.upeu.ecorecicla.seguridad.Privilegios.PESAJE_LEER
import pe.upeu.ecorecicla.seguridad.Privilegios.PUNTO_GESTIONAR
import pe.upeu.ecorecicla.seguridad.Privilegios.PUNTO_LEER
import pe.upeu.ecorecicla.seguridad.Privilegios.RUTA_CERRAR

/**
 * Unico lugar donde se decide que privilegio exige cada ruta. Los roles no
 * aparecen aqui: un rol nuevo (por ejemplo AUDITOR) solo se agrega en la base
 * con sus privilegios, sin tocar este archivo ni los controladores.
 */
@Configuration
class SecurityConfig(
    private val filtroJwt: JwtAuthenticationFilter,
    private val errores: EscritorDeErrores
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    /** El filtro solo corre dentro de la cadena de seguridad, no dos veces. */
    @Bean
    fun noRegistrarFiltroJwtGlobal(): FilterRegistrationBean<JwtAuthenticationFilter> =
        FilterRegistrationBean(filtroJwt).apply { isEnabled = false }

    @Bean
    fun cadenaDeSeguridad(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                    .requestMatchers("/error").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/rutas/**").hasAuthority(PUNTO_LEER)
                    .requestMatchers(HttpMethod.POST, "/api/v1/rutas/*/cierre").hasAuthority(RUTA_CERRAR)
                    .requestMatchers(HttpMethod.GET, "/api/v1/puntos/*/pesajes").hasAuthority(PESAJE_LEER)
                    .requestMatchers(HttpMethod.GET, "/api/v1/puntos", "/api/v1/puntos/**").hasAuthority(PUNTO_LEER)
                    .requestMatchers(HttpMethod.POST, "/api/v1/puntos").hasAuthority(PUNTO_GESTIONAR)
                    .requestMatchers(HttpMethod.POST, "/api/v1/pesajes").hasAuthority(PESAJE_CREAR)
                    .requestMatchers(HttpMethod.PUT, "/api/v1/pesajes/*").hasAuthority(PESAJE_EDITAR)
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/pesajes/*").hasAuthority(PESAJE_ANULAR)
                    .anyRequest().authenticated()
            }
            .exceptionHandling {
                // 401: sin token, token alterado o vencido.
                it.authenticationEntryPoint { request, response, _ ->
                    val mensaje = when (request.getAttribute(JwtAuthenticationFilter.MOTIVO_401)) {
                        JwtAuthenticationFilter.MOTIVO_EXPIRADO -> "El token ha expirado. Inicie sesión nuevamente"
                        JwtAuthenticationFilter.MOTIVO_INVALIDO -> "El token no es válido"
                        else -> "Debe iniciar sesión para acceder a este recurso"
                    }
                    errores.escribir(request, response, HttpStatus.UNAUTHORIZED, mensaje)
                }
                // 403: autenticado pero sin el privilegio que exige la ruta.
                it.accessDeniedHandler { request, response, _ ->
                    errores.escribir(
                        request, response, HttpStatus.FORBIDDEN,
                        "No tiene el privilegio necesario para esta operación"
                    )
                }
            }
            .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
