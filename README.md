# EcoAPI — servicio de pesajes en ruta (EcoRecicla)

Servicio REST del Examen Parcial U2 (Versión B). Custodia rutas, puntos de acopio
y pesajes, con autenticación JWT y autorización por **privilegio**.

**Stack:** Kotlin 2.4 · Spring Boot 3.5 (Web, Security, Data JPA, Validation) ·
JJWT 0.12 · PostgreSQL 16 en Docker · pruebas con MockMvc y H2.

## Ejecución (un solo comando)

Requisitos: **JDK 21** y **Docker Desktop encendido**.

```powershell
.\gradlew.bat bootRun        # Windows
./gradlew bootRun            # Linux / macOS
```

`spring-boot-docker-compose` lee `compose.yaml`, levanta PostgreSQL
(`ecorecicla`, usuario `eco` / `eco`, puerto 5432) y configura la conexión. Al
arrancar se cargan los datos semilla. El servicio queda en
`http://localhost:8080/api/v1` (desde el emulador Android: `http://10.0.2.2:8080/api/v1`).

Pruebas: `.\gradlew.bat test` (22 pruebas con H2, sin Docker).

## Usuarios de prueba

| Usuario | Contraseña | Rol |
| --- | --- | --- |
| operario1 | Eco2026* | OPERARIO (ruta del día asignada) |
| operario2 | Eco2026* | OPERARIO (dueño de pesajes para demostrar RN-02) |
| supervisor1 | Eco2026* | SUPERVISOR (supervisa la ruta del día) |
| admin | Eco2026* | ADMINISTRADOR |
| auditor1 | Eco2026* | AUDITOR (solo lectura, SC-B) |

Las contraseñas se guardan con BCrypt.

Datos semilla:
- 12 puntos de acopio en 3 zonas (Norte, Centro, Sur).
- Ruta de ayer **CERRADA** con sus 6 puntos pesados (RN-01).
- Ruta de hoy **ABIERTA** con 6 puntos: 3 pesados y 3 sin pesar (RN-04).
- 15 pesajes: uno anulado y varios de `operario2` (RN-02).

En cada arranque, si `operario1` no tiene ruta para la fecha actual, se crea una ruta abierta nueva. Así la demostración funciona cualquier día.

## Estructura de paquetes

```
src/main/kotlin/pe/upeu/ecorecicla/
├── config/      SecurityConfig, JwtAuthenticationFilter, JwtService,
│                ManejadorDeErrores (+ EscritorDeErrores), DatosSemilla
├── seguridad/   Usuario, Rol, Privilegio, UsuarioRepository, UsuarioAutenticado, AuthController
├── dominio/     PuntoAcopio, Ruta, RutaPunto, Pesaje, Excepciones
├── repositorio/ PuntoAcopioRepository, RutaRepository, PesajeRepository
├── servicio/    RutaServicio (RN-04), PesajeServicio (RN-01, RN-02, RN-03), PuntoServicio
├── web/         RutaController, PuntoController, PesajeController
│   └── dto/     peticiones, respuestas, Pagina<T> y ErrorRespuesta
└── Aplicacion.kt
```

## Contrato del API (base `/api/v1`)

| Operación | Verbo y ruta | Privilegio | Éxito | Errores |
| --- | --- | --- | --- | --- |
| Inicio de sesión | `POST /auth/login` | Público | 200 | 400 campos vacíos, 401 credenciales |
| Usuario actual | `GET /auth/me` | Autenticado | 200 | 401 |
| Ruta del día | `GET /rutas/hoy?fecha=yyyy-MM-dd` | PUNTO_LEER | 200 | 401, 403, 404 sin ruta |
| Cerrar ruta | `POST /rutas/{id}/cierre` | RUTA_CERRAR | 200 | 401, 403, 404, 409 RN-04 / ya cerrada |
| Puntos de acopio | `GET /puntos?zona=&pagina=&tamanio=` | PUNTO_LEER | 200 | 401, 403 |
| Crear punto | `POST /puntos` | PUNTO_GESTIONAR | 201 | 400, 401, 403 |
| Pesajes de un punto | `GET /puntos/{id}/pesajes?pagina=&tamanio=` | PESAJE_LEER | 200 | 401, 403, 404 |
| Registrar pesaje | `POST /pesajes` | PESAJE_CREAR | 201 | 400 (RN-03), 401, 403, 404, 409 (RN-01) |
| Corregir pesaje | `PUT /pesajes/{id}` | PESAJE_EDITAR | 200 | 400, 401, 403 (RN-02), 404, 409 (RN-01, anulado) |
| Anular pesaje | `DELETE /pesajes/{id}?motivo=` | PESAJE_ANULAR | 204 | 401, 403, 404, 409 ya anulado |

Cualquier fallo no controlado responde **500** «Error interno del servidor».

**Peticiones principales**

```json
POST /auth/login       {"usuario":"operario1","clave":"Eco2026*"}
→ {"token":"…","tipo":"Bearer","expiraEnSegundos":3600,"usuario":"operario1",
   "nombreCompleto":"Luis Quispe Mamani","rol":"OPERARIO","privilegios":["PESAJE_CREAR",…]}

POST /pesajes          {"rutaId":2,"puntoId":4,"material":"Cartón","pesoBruto":120.5,"tara":5.2,"observacion":"…"}
PUT  /pesajes/{id}     {"material":"Cartón","pesoBruto":120.5,"tara":5.2,"observacion":"…"}
→ PesajeDto {"id","rutaId","puntoId","fechaHora":"2026-10-28T10:15:00","material","pesoBruto",
             "tara","pesoNeto","observacion","operario","anulado","motivoAnulacion"}

GET /rutas/hoy → {"id","fecha","estado":"ABIERTA","puntos":[{"id","razonSocial","direccion","zona",
                  "materialHabitual","secuencia","atendido","motivoOmision"}]}
```

El cliente **nunca envía el peso neto**: lo calcula el servicio.

**Envoltorio de paginación:** `{"contenido":[…],"pagina":0,"tamanio":20,"totalElementos":37,"totalPaginas":2,"ultima":false}`

**Formato de error:** `{"timestamp","status","error","message","path","validationErrors":{"campo":"mensaje"}}`. El campo `validationErrors` solo aparece en los 400.

## Seguridad: roles y privilegios

- **JWT** firmado con HMAC-SHA256 y vigencia de 1 hora, sin renovación.
  - Claims: `sub` (usuario), `uid`, `rol`, `privilegios`, `iat` y `exp`.
- **Filtro** `JwtAuthenticationFilter`: verifica la firma y la vigencia y carga desde la base los privilegios actuales del usuario como *authorities*.
- **401**: sin token («Debe iniciar sesión…»), token alterado («El token no es válido») o vencido («El token ha expirado…»). Lo responde el `AuthenticationEntryPoint` con el formato común.
- **403**: autenticado sin el privilegio de la ruta. Lo responde el `AccessDeniedHandler`.

| Privilegio | OPERARIO | SUPERVISOR | ADMINISTRADOR | AUDITOR |
| --- | --- | --- | --- | --- |
| PUNTO_LEER | Sí | Sí | Sí | Sí |
| PESAJE_LEER | Sí | Sí | Sí | Sí |
| PESAJE_CREAR | Sí | Sí | No | No |
| PESAJE_EDITAR | Sí (solo los propios del día) | Sí | No | No |
| PESAJE_EDITAR_TODOS | No | Sí | No | No |
| PESAJE_ANULAR | No | Sí | No | No |
| RUTA_CERRAR | No | Sí | No | No |
| PUNTO_GESTIONAR | No | No | Sí | No |

**Rol AUDITOR (solicitud de cambio SC-B).** Tiene todos los privilegios de lectura y ninguno de escritura. Para agregarlo solo se tocó `DatosSemilla.kt`, que crea el rol con `PUNTO_LEER` y `PESAJE_LEER` y el usuario `auditor1` en cada arranque si faltan, de modo que también llega a una base ya sembrada. `SecurityConfig`, `JwtAuthenticationFilter` y los controladores no cambian, porque autorizan por privilegio y nunca por nombre de rol. Pruebas: `AuditorTest`.

**`PESAJE_EDITAR_TODOS`** representa la celda «Todos» frente a «Solo los propios» de la matriz. Así RN-02 se decide por privilegio y no comparando el nombre del rol: sin ese privilegio solo se corrigen los pesajes propios del mismo día.

**Dónde vive cada cosa:**
- La relación rol → privilegios está en la base de datos (tabla `rol_privilegio`, cargada en `DatosSemilla`).
- Qué privilegio exige cada ruta se declara en un solo lugar: `SecurityConfig`.
- Ningún controlador ni el filtro contienen nombres de rol.
- Para agregar un rol (por ejemplo AUDITOR) basta insertarlo con sus privilegios.

## Reglas de negocio (capa `servicio`, con `@Transactional`)

| Regla | Dónde | Respuesta |
| --- | --- | --- |
| RN-01 ruta cerrada: no se registra ni corrige | `PesajeServicio.verificarRutaAbierta` | 409 con el motivo |
| RN-02 operario: solo sus pesajes del mismo día | `PesajeServicio.verificarPropiedad` | 403 «Solo puede corregir sus propios pesajes del día» |
| RN-03 neto = bruto − tara > 0, lo calcula el servicio | `PesajeServicio.calcularNeto` | 400 `validationErrors.tara` |
| RN-04 no cerrar con puntos sin pesaje ni motivo de omisión | `RutaServicio.cerrar` | 409 «faltan N puntos por pesar u omitir» |

La anulación marca el pesaje (`anulado`, `motivoAnulacion`) y responde 204; nunca borra la fila.

## Colección de peticiones

- `http/ecoapi.http`: cliente HTTP de IntelliJ, con ejemplos de 401, 403, 400, 404 y 409.
- `http/EcoAPI.postman_collection.json`: la misma colección para Postman. El login guarda el token en una variable.
