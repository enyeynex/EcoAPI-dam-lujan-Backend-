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
 * SC-B: el rol AUDITOR solo tiene privilegios de lectura. El servicio lo
 * resuelve con los mismos hasAuthority de SecurityConfig, sin ningun if por
 * nombre de rol.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuditorTest {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var rutas: RutaRepository
    @Autowired lateinit var pesajes: PesajeRepository

    private fun bearerAuditor(): String {
        val respuesta = mvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"usuario":"auditor1","clave":"Eco2026*"}""")
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return "Bearer " + mapper.readTree(respuesta)["token"].asText()
    }

    private fun rutaDeHoy() = rutas.findAll().first { it.fecha == LocalDate.now() && it.estado == EstadoRuta.ABIERTA }

    @Test
    fun `auditor inicia sesion con solo privilegios de lectura`() {
        mvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"usuario":"auditor1","clave":"Eco2026*"}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.rol").value("AUDITOR"))
            .andExpect(jsonPath("$.privilegios.length()").value(2))
            .andExpect(jsonPath("$.privilegios[?(@ == 'PUNTO_LEER')]").exists())
            .andExpect(jsonPath("$.privilegios[?(@ == 'PESAJE_LEER')]").exists())
    }

    @Test
    fun `auditor lee los puntos y los pesajes`() {
        val auth = bearerAuditor()
        mvc.perform(get("/api/v1/puntos").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.contenido").isArray)
        val punto = rutaDeHoy().puntos[0].punto.id
        mvc.perform(get("/api/v1/puntos/$punto/pesajes").header("Authorization", auth))
            .andExpect(status().isOk)
    }

    @Test
    fun `auditor no registra corrige anula ni cierra 403`() {
        val auth = bearerAuditor()
        val ruta = rutaDeHoy()
        val pesaje = pesajes.findAll().first { !it.anulado }.id
        mvc.perform(
            post("/api/v1/pesajes").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("""{"rutaId":${ruta.id},"puntoId":${ruta.puntos[0].punto.id},"material":"Cartón","pesoBruto":10,"tara":1}""")
        ).andExpect(status().isForbidden)
        mvc.perform(
            put("/api/v1/pesajes/$pesaje").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("""{"material":"Cartón","pesoBruto":10,"tara":1}""")
        ).andExpect(status().isForbidden)
        mvc.perform(delete("/api/v1/pesajes/$pesaje").header("Authorization", auth))
            .andExpect(status().isForbidden)
        mvc.perform(post("/api/v1/rutas/${ruta.id}/cierre").header("Authorization", auth))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("No tiene el privilegio necesario para esta operación"))
    }
}
