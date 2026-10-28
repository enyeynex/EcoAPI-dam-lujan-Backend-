package pe.upeu.ecorecicla.dominio

/** 401 en el inicio de sesion. */
class CredencialesInvalidasException : RuntimeException("Usuario o contraseña incorrectos")

/** 404: el recurso pedido no existe. */
class NoEncontradoException(mensaje: String) : RuntimeException(mensaje)

/** 409: una regla de negocio del servidor impide la operacion (RN-01, RN-04). */
class ReglaNegocioException(mensaje: String) : RuntimeException(mensaje)

/** 400 con el detalle en un campo concreto (RN-03). */
class ValidacionException(val campo: String, val mensaje: String) : RuntimeException(mensaje)

/** 403 por una regla sobre el recurso, no sobre la ruta (RN-02). */
class SinPermisoSobreRecursoException(mensaje: String) : RuntimeException(mensaje)
