package pe.upeu.ecorecicla

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import pe.upeu.ecorecicla.dominio.EstadoRuta
import pe.upeu.ecorecicla.repositorio.PesajeRepository
import pe.upeu.ecorecicla.repositorio.RutaRepository
import java.time.LocalDate

/**
 * Pruebas del contrato con la base H2 y los datos semilla. Cada prueba corre
 * en una transaccion que se revierte al terminar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EcoApiTest {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var rutas: RutaRepository
    @Autowired lateinit var pesajes: PesajeRepository
    @Autowired lateinit var usuarios: pe.upeu.ecorecicla.seguridad.UsuarioRepository
    @org.springframework.beans.factory.annotation.Value("\${app.jwt.secret}") lateinit var secreto: String

    private fun token(usuario: String, clave: String = "Eco2026*"): String {
        val respuesta = mvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"usuario":"$usuario","clave":"$clave"}""")
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return mapper.readTree(respuesta)["token"].asText()
    }

    private fun bearer(usuario: String) = "Bearer ${token(usuario)}"

    private fun rutaDeHoy() = rutas.findAll().first { it.fecha == LocalDate.now() && it.estado == EstadoRuta.ABIERTA }
    private fun rutaCerrada() = rutas.findAll().first { it.estado == EstadoRuta.CERRADA }

    private fun cuerpoPesaje(rutaId: Long, puntoId: Long, bruto: Double, tara: Double) =
        """{"rutaId":$rutaId,"puntoId":$puntoId,"material":"Cartón","pesoBruto":$bruto,"tara":$tara,"observacion":"prueba"}"""

    @Test
    fun `login correcto devuelve token, rol y privilegios`() {
        mvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"usuario":"supervisor1","clave":"Eco2026*"}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.tipo").value("Bearer"))
            .andExpect(jsonPath("$.expiraEnSegundos").value(3600))
            .andExpect(jsonPath("$.rol").value("SUPERVISOR"))
            .andExpect(jsonPath("$.privilegios[?(@ == 'RUTA_CERRAR')]").exists())
    }

    @Test
    fun `credenciales incorrectas responden 401 con el formato comun`() {
        mvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"usuario":"operario1","clave":"mala"}""")
        ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.message").value("Usuario o contraseña incorrectos"))
            .andExpect(jsonPath("$.path").value("/api/v1/auth/login"))
    }

    @Test
    fun `login con campos vacios responde 400 por campo`() {
        mvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""{"usuario":"","clave":""}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.validationErrors.usuario").exists())
            .andExpect(jsonPath("$.validationErrors.clave").exists())
    }

    @Test
    fun `sin token responde 401`() {
        mvc.perform(get("/api/v1/rutas/hoy"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("Debe iniciar sesión para acceder a este recurso"))
    }

    @Test
    fun `token alterado responde 401`() {
        val alterado = token("operario1").dropLast(2) + "xx"
        mvc.perform(get("/api/v1/rutas/hoy").header("Authorization", "Bearer $alterado"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("El token no es válido"))
    }

    @Test
    fun `token vencido responde 401 con el motivo`() {
        // Mismo secreto, vigencia negativa: el token nace vencido.
        val vencido = pe.upeu.ecorecicla.config.JwtService(secreto, -1)
            .generar(usuarios.findByUsername("operario1")!!)
        mvc.perform(get("/api/v1/rutas/hoy").header("Authorization", "Bearer $vencido"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("El token ha expirado. Inicie sesión nuevamente"))
    }

    @Test
    fun `usuario actual devuelve sus privilegios`() {
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer("admin")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"))
            .andExpect(jsonPath("$.privilegios.length()").value(3))
    }

    @Test
    fun `ruta del dia ordenada por secuencia con puntos atendidos y pendientes`() {
        mvc.perform(get("/api/v1/rutas/hoy").header("Authorization", bearer("operario1")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.estado").value("ABIERTA"))
            .andExpect(jsonPath("$.puntos.length()").value(6))
            .andExpect(jsonPath("$.puntos[0].secuencia").value(1))
            .andExpect(jsonPath("$.puntos[0].atendido").value(true))
            .andExpect(jsonPath("$.puntos[5].atendido").value(false))
    }

    @Test
    fun `listados usan el envoltorio de paginacion y filtran por zona`() {
        mvc.perform(get("/api/v1/puntos?zona=Norte&pagina=0&tamanio=2").header("Authorization", bearer("operario1")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.contenido.length()").value(2))
            .andExpect(jsonPath("$.pagina").value(0))
            .andExpect(jsonPath("$.tamanio").value(2))
            .andExpect(jsonPath("$.totalElementos").value(5))
            .andExpect(jsonPath("$.totalPaginas").value(3))
            .andExpect(jsonPath("$.ultima").value(false))
    }

    @Test
    fun `operario registra un pesaje y el servicio calcula el neto`() {
        val ruta = rutaDeHoy()
        val punto = ruta.puntos.last().punto.id!!
        mvc.perform(
            post("/api/v1/pesajes").header("Authorization", bearer("operario1"))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpoPesaje(ruta.id!!, punto, 120.5, 5.25))
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNumber)
            .andExpect(jsonPath("$.pesoNeto").value(115.25))
            .andExpect(jsonPath("$.operario").value("operario1"))
    }

    @Test
    fun `admin no puede registrar pesajes 403`() {
        val ruta = rutaDeHoy()
        mvc.perform(
            post("/api/v1/pesajes").header("Authorization", bearer("admin"))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpoPesaje(ruta.id!!, ruta.puntos[0].punto.id!!, 10.0, 1.0))
        ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("No tiene el privilegio necesario para esta operación"))
    }

    @Test
    fun `operario no puede anular 403`() {
        val id = pesajes.findAll().first { !it.anulado }.id
        mvc.perform(delete("/api/v1/pesajes/$id").header("Authorization", bearer("operario1")))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `supervisor anula con 204 y el pesaje sigue en la base`() {
        val id = pesajes.findAll().first { !it.anulado }.id!!
        mvc.perform(delete("/api/v1/pesajes/$id?motivo=Duplicado").header("Authorization", bearer("supervisor1")))
            .andExpect(status().isNoContent)
        val anulado = pesajes.findById(id).orElseThrow()
        kotlin.test.assertTrue(anulado.anulado)
        kotlin.test.assertEquals("Duplicado", anulado.motivoAnulacion)
    }

    @Test
    fun `RN-03 neto no positivo responde 400 en el campo tara`() {
        val ruta = rutaDeHoy()
        mvc.perform(
            post("/api/v1/pesajes").header("Authorization", bearer("operario1"))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpoPesaje(ruta.id!!, ruta.puntos[0].punto.id!!, 10.0, 10.0))
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.validationErrors.tara").value("El peso neto (bruto - tara) debe ser mayor que cero"))
    }

    @Test
    fun `validacion de formato responde 400 por campo`() {
        val ruta = rutaDeHoy()
        mvc.perform(
            post("/api/v1/pesajes").header("Authorization", bearer("operario1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rutaId":${ruta.id},"puntoId":${ruta.puntos[0].punto.id},"material":"","pesoBruto":-1,"tara":-2}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Datos invalidos"))
            .andExpect(jsonPath("$.validationErrors.material").exists())
            .andExpect(jsonPath("$.validationErrors.pesoBruto").exists())
            .andExpect(jsonPath("$.validationErrors.tara").exists())
    }

    @Test
    fun `RN-01 en una ruta cerrada responde 409`() {
        val ruta = rutaCerrada()
        mvc.perform(
            post("/api/v1/pesajes").header("Authorization", bearer("supervisor1"))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpoPesaje(ruta.id!!, ruta.puntos[0].punto.id!!, 20.0, 1.0))
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("La ruta del ${ruta.fecha} está cerrada: no se pueden registrar ni corregir pesajes"))
    }

    @Test
    fun `RN-02 operario no corrige el pesaje de otro operario 403`() {
        val ajeno = pesajes.findAll().first { it.operario.username == "operario2" }
        mvc.perform(
            put("/api/v1/pesajes/${ajeno.id}").header("Authorization", bearer("operario1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"material":"Cartón","pesoBruto":40.0,"tara":2.0,"observacion":null}""")
        ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("Solo puede corregir sus propios pesajes del día"))
    }

    @Test
    fun `supervisor corrige el pesaje de cualquier operario 200`() {
        val ajeno = pesajes.findAll().first { it.operario.username == "operario2" && !it.anulado }
        mvc.perform(
            put("/api/v1/pesajes/${ajeno.id}").header("Authorization", bearer("supervisor1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"material":"Cartón","pesoBruto":40.0,"tara":2.0}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.pesoNeto").value(38.0))
    }

    @Test
    fun `RN-04 no se cierra la ruta con puntos sin pesar 409`() {
        val ruta = rutaDeHoy()
        mvc.perform(post("/api/v1/rutas/${ruta.id}/cierre").header("Authorization", bearer("supervisor1")))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("No se puede cerrar la ruta: faltan 3 puntos por pesar u omitir"))
    }

    @Test
    fun `operario no puede cerrar la ruta 403`() {
        val ruta = rutaDeHoy()
        mvc.perform(post("/api/v1/rutas/${ruta.id}/cierre").header("Authorization", bearer("operario1")))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `pesajes de un punto inexistente responden 404`() {
        mvc.perform(get("/api/v1/puntos/9999/pesajes").header("Authorization", bearer("operario1")))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    fun `pesajes de un punto del mas reciente al mas antiguo`() {
        val punto = rutaDeHoy().puntos[0].punto.id
        mvc.perform(get("/api/v1/puntos/$punto/pesajes").header("Authorization", bearer("operario1")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.contenido[0].fechaHora").value(LocalDate.now().toString() + "T07:50:00"))
            .andExpect(jsonPath("$.totalElementos").value(3))
    }
}
