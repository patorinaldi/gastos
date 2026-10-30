/*
 * Validaciones de los formularios de cuenta y del gasto. Repiten las de los contratos del backend
 * (AuthContracts, ExpenseContracts) para avisar antes de enviar, con un mensaje que dice qué corregir (RNF-17). El
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
// La misma expresión que DisplayNames.PATTERN del backend, que en JavaScript necesita la bandera u
// para las clases de Unicode. Rechaza los caracteres de control, también los C1, los de formato
// (el override de dirección, el espacio de ancho cero) y los separadores de línea, y exige al menos
// una letra, número, símbolo o signo. La unión de ancho cero (U+200D) se acepta: arma los emoji
// compuestos. Si cambia allá, cambia acá.
const DISPLAY_NAME = /^(?=.*[\p{L}\p{N}\p{S}\p{P}])(?:[^\p{Cc}\p{Cf}\p{Zl}\p{Zp}]|‍)*$/u

const utf8 = new TextEncoder()

export function validateName(name: string): string | undefined {
  if (name.trim() === '') {
    return 'Ingresá tu nombre'
  }
  if (name.length > NAME_MAX_LENGTH) {
    return `Usá un nombre de hasta ${NAME_MAX_LENGTH} caracteres`
  }
  // El caso típico es un nombre pegado de otro lado que trae un carácter que no se ve.
  if (!DISPLAY_NAME.test(name)) {
    return 'Escribí el nombre sin caracteres invisibles: a veces se cuelan al pegarlo'
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

/** El comercio de un gasto: un nombre que se muestra, como el de una persona (RN-07). */
export function validateMerchant(merchant: string): string | undefined {
  if (merchant.trim() === '') {
    return 'Ingresá el comercio'
  }
  if (merchant.length > NAME_MAX_LENGTH) {
    return `Usá un comercio de hasta ${NAME_MAX_LENGTH} caracteres`
  }
  if (!DISPLAY_NAME.test(merchant)) {
    return 'Escribí el comercio sin caracteres invisibles: a veces se cuelan al pegarlo'
  }
  return undefined
}
