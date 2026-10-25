/*
 * Validaciones de los formularios de cuenta. Repiten las de los contratos del backend
 * (AuthContracts) para avisar antes de enviar, con un mensaje que dice qué corregir (RNF-17). El
 * servidor valida igual: esto es para el usuario, no una barrera.
 *
 * Cada función devuelve el mensaje de error, o undefined si el valor sirve.
 */

/** Lo que bcrypt alcanza a leer: el backend rechaza una contraseña de más bytes. */
const PASSWORD_MAX_BYTES = 72
const PASSWORD_MIN_LENGTH = 8
const NAME_MAX_LENGTH = 200
const EMAIL_MAX_LENGTH = 320

// Tan permisiva como @Email del backend, que acepta "ana@ejemplo" sin dominio de nivel superior:
// una más estricta rechazaría correos que el servidor admite.
const EMAIL = /^[^\s@]+@[^\s@]+$/
// Los mismos que \p{Cntrl} de Java, que es lo que rechaza el backend.
// eslint-disable-next-line no-control-regex -- detectar caracteres de control es justamente la idea
const CONTROL_CHARACTERS = /[\u0000-\u001f\u007f]/

const utf8 = new TextEncoder()

export function validateName(name: string): string | undefined {
  if (name.trim() === '') {
    return 'Ingresá tu nombre'
  }
  if (name.length > NAME_MAX_LENGTH) {
    return `Usá un nombre de hasta ${NAME_MAX_LENGTH} caracteres`
  }
  if (CONTROL_CHARACTERS.test(name)) {
    return 'Usá solo letras, números y signos comunes'
  }
  return undefined
}

export function validateEmail(email: string): string | undefined {
  if (email.trim() === '') {
    return 'Ingresá tu correo'
  }
  if (!EMAIL.test(email) || email.length > EMAIL_MAX_LENGTH) {
    return 'Ingresá un correo válido, como ana@ejemplo.com'
  }
  return undefined
}

/** La del inicio de sesión: solo tiene que estar. El mínimo lo exige el alta, no el login. */
export function validatePassword(password: string): string | undefined {
  return password === '' ? 'Ingresá tu contraseña' : undefined
}

/**
 * La de una cuenta nueva. El máximo va en bytes, como en el backend: una tilde o una eñe ocupan dos,
 * así que una contraseña de 72 caracteres con tildes no entra.
 */
export function validateNewPassword(password: string): string | undefined {
  if (password.length < PASSWORD_MIN_LENGTH) {
    return `Usá al menos ${PASSWORD_MIN_LENGTH} caracteres`
  }
  if (utf8.encode(password).length > PASSWORD_MAX_BYTES) {
    return `Usá una contraseña más corta: admite hasta ${PASSWORD_MAX_BYTES} caracteres, menos si lleva tildes o eñes`
  }
  return undefined
}
