import { describe, expect, it } from 'vitest'
import {
  validateEmail,
  validateHouseholdName,
  validateInvitationCode,
  validateMerchant,
  validateName,
  validateNewPassword,
  validatePassword,
} from './validation.ts'

describe('validateName', () => {
  it('acepta un nombre con tildes y espacios', () => {
    expect(validateName('María José')).toBeUndefined()
  })

  it('pide un nombre', () => {
    expect(validateName('')).toBe('Ingresá tu nombre')
    expect(validateName('   ')).toBe('Ingresá tu nombre')
  })

  it('rechaza un nombre de más de 200 caracteres', () => {
    expect(validateName('a'.repeat(200))).toBeUndefined()
    expect(validateName('a'.repeat(201))).toMatch(/hasta 200/)
  })

  // Postgres no guarda el carácter nulo en un texto, y el backend los rechaza todos.
  it('rechaza caracteres de control', () => {
    expect(validateName('Ana\u0000')).toMatch(/invisibles/)
    expect(validateName('Ana\tLópez')).toMatch(/invisibles/)
    expect(validateName('Ana\nPerez')).toMatch(/invisibles/)
  })

  // Los mismos casos que ApiContractsValidationTest del backend (DisplayNames): sin esta validación
  // el servidor responde 400 y la pantalla no puede decir que el problema es el nombre.
  it('rechaza los caracteres de control de Unicode, los invisibles y los separadores de línea', () => {
    for (const name of ['Atajo\u0085', '‮onofelet', 'Ana​', '​', 'Ata jo', 'Ata jo']) {
      expect(validateName(name), JSON.stringify(name)).toBeDefined()
    }
  })

  // trim() de JavaScript los quita, así que se piden como un nombre vacío.
  it('rechaza un nombre hecho solo de espacios duros', () => {
    expect(validateName(' ')).toBe('Ingresá tu nombre')
    expect(validateName('  ')).toBe('Ingresá tu nombre')
  })

  it('acepta emoji, también los compuestos con la unión de ancho cero', () => {
    expect(validateName('Ana ✨')).toBeUndefined()
    expect(validateName('Familia 👨‍👩‍👧')).toBeUndefined()
    expect(validateName('María José Núñez')).toBeUndefined()
  })
})

describe('validateEmail', () => {
  it('acepta un correo, también sin dominio de nivel superior, como el backend', () => {
    expect(validateEmail('ana@ejemplo.com')).toBeUndefined()
    expect(validateEmail('ana@ejemplo')).toBeUndefined()
  })

  it('pide un correo válido', () => {
    expect(validateEmail('')).toBe('Ingresá tu correo')
    expect(validateEmail('ana')).toMatch(/correo válido/)
    expect(validateEmail('ana @ejemplo.com')).toMatch(/correo válido/)
    expect(validateEmail('ana@')).toMatch(/correo válido/)
  })
})

describe('validatePassword', () => {
  it('en el login solo pide que esté', () => {
    expect(validatePassword('')).toBe('Ingresá tu contraseña')
    expect(validatePassword('corta')).toBeUndefined()
  })
})

describe('validateNewPassword', () => {
  it('pide al menos 8 caracteres', () => {
    expect(validateNewPassword('1234567')).toMatch(/al menos 8/)
    expect(validateNewPassword('12345678')).toBeUndefined()
  })

  it('mide el máximo en bytes, como bcrypt', () => {
    expect(validateNewPassword('a'.repeat(72))).toBeUndefined()
    expect(validateNewPassword('a'.repeat(73))).toMatch(/más corta/)
    // 37 eñes son 37 caracteres pero 74 bytes.
    expect(validateNewPassword('ñ'.repeat(36))).toBeUndefined()
    expect(validateNewPassword('ñ'.repeat(37))).toMatch(/más corta/)
  })
})

describe('validateHouseholdName', () => {
  it('acepta un nombre con tildes', () => {
    expect(validateHouseholdName('Depto Palermo')).toBeUndefined()
    expect(validateHouseholdName('Casa de Ana ✨')).toBeUndefined()
  })

  // HU-18: un nombre hecho solo de espacios se rechaza y queda el anterior.
  it('pide un nombre que no sea solo espacios', () => {
    expect(validateHouseholdName('')).toBe('Ingresá un nombre para el hogar')
    expect(validateHouseholdName('   ')).toBe('Ingresá un nombre para el hogar')
  })

  it('rechaza un nombre de más de 200 caracteres', () => {
    expect(validateHouseholdName('a'.repeat(200))).toBeUndefined()
    expect(validateHouseholdName('a'.repeat(201))).toMatch(/hasta 200/)
  })

  // Lo ven todos los integrantes: un override de dirección o un espacio de ancho cero lo dejarían
  // al revés o vacío para todos, y el backend responde 400 (DisplayNames).
  it('rechaza los caracteres de control y los invisibles', () => {
    for (const name of ['Casa\u0000', 'Casa\nRivoira', 'Casa\u0085', '\u202Easac', 'Casa\u200B']) {
      expect(validateHouseholdName(name), JSON.stringify(name)).toMatch(/invisibles/)
    }
  })

  it('acepta emoji compuestos con la unión de ancho cero', () => {
    expect(validateHouseholdName('Familia 👨\u200D👩\u200D👧')).toBeUndefined()
  })
})

describe('validateInvitationCode', () => {
  it('acepta el código como se comparte, también en minúsculas o sin guiones', () => {
    expect(validateInvitationCode('GST-ABCD-EFG')).toBeUndefined()
    expect(validateInvitationCode('gst abcd efg')).toBeUndefined()
  })

  it('pide un código', () => {
    expect(validateInvitationCode('')).toBe('Ingresá el código que te pasaron')
    expect(validateInvitationCode('   ')).toBe('Ingresá el código que te pasaron')
  })

  // El backend lo rechaza con 400, que no dice qué corregir.
  it('rechaza un código de más de 32 caracteres', () => {
    expect(validateInvitationCode('A'.repeat(32))).toBeUndefined()
    expect(validateInvitationCode('A'.repeat(33))).toMatch(/GST-ABCD-EFG/)
  })

  // Se envía recortado: los espacios que trae al pegarlo no cuentan para el tope.
  it('no cuenta los espacios de alrededor', () => {
    expect(validateInvitationCode(`  ${'A'.repeat(32)}  `)).toBeUndefined()
  })
})

describe('validateMerchant', () => {
  it('pide el comercio', () => {
    expect(validateMerchant('')).toBe('Ingresá el comercio')
    expect(validateMerchant('   ')).toBe('Ingresá el comercio')
    expect(validateMerchant('Carrefour')).toBeUndefined()
  })

  it('admite hasta 200 caracteres', () => {
    expect(validateMerchant('a'.repeat(200))).toBeUndefined()
    expect(validateMerchant('a'.repeat(201))).toMatch(/hasta 200/)
  })

  it('rechaza los caracteres invisibles, como el backend', () => {
    expect(validateMerchant('Carre\u200bfour')).toMatch(/invisibles/)
    expect(validateMerchant('\u200b')).toMatch(/invisibles/)
  })
})
