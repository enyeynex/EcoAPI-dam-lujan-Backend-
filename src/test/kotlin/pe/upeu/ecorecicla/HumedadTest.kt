package pe.upeu.ecorecicla

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
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
import kotlin.test.assertTrue

/** SC-C: la humedad estimada recorre la peticion, la entidad y la respuesta. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HumedadTest {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var rutas: RutaRepository
    @Autowired lateinit var pesajes: PesajeRepository

    private fun bearer(usuario: String): String {
        val r = mvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"usuario":"$usuario","clave":"Eco2026*"}""")
        ).andReturn().response.contentAsString
        return "Bearer " + mapper.readTree(r)["token"].asText()
    }

    private fun rutaDeHoy() = rutas.findAll().first { it.fecha == LocalDate.now() && it.estado == EstadoRuta.ABIERTA }

    @Test
    fun `registrar guarda la humedad estimada y la devuelve`() {
        val ruta = rutaDeHoy()
        mvc.perform(
            post("/api/v1/pesajes").header("Authorization", bearer("operario1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rutaId":${ruta.id},"puntoId":${ruta.puntos.last().punto.id},"material":"Papel","pesoBruto":30,"tara":2,"humedadEstimada":12.5}""")
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.humedadEstimada").value(12.5))
            .andExpect(jsonPath("$.pesoNeto").value(28.0))
    }

    @Test
    fun `corregir actualiza la humedad estimada`() {
        val propio = pesajes.findAll().first { it.operario.username == "operario1" && !it.anulado && it.ruta.estado == EstadoRuta.ABIERTA }
        mvc.perform(
            put("/api/v1/pesajes/${propio.id}").header("Authorization", bearer("operario1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"material":"Cartón","pesoBruto":40,"tara":3,"humedadEstimada":20}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.humedadEstimada").value(20.0))
    }

    @Test
    fun `una humedad fuera de 0 a 100 responde 400 en su campo`() {
        val ruta = rutaDeHoy()
        mvc.perform(
            post("/api/v1/pesajes").header("Authorization", bearer("operario1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rutaId":${ruta.id},"puntoId":${ruta.puntos.last().punto.id},"material":"Papel","pesoBruto":30,"tara":2,"humedadEstimada":150}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.validationErrors.humedadEstimada").value("La humedad debe estar entre 0 y 100"))
    }

    @Test
    fun `los datos semilla traen humedad estimada`() {
        assertTrue(pesajes.findAll().all { it.humedadEstimada != null })
        val punto = rutaDeHoy().puntos[0].punto.id
        mvc.perform(get("/api/v1/puntos/$punto/pesajes").header("Authorization", bearer("operario1")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.contenido[0].humedadEstimada").isNumber)
    }
}
