// URL base de la API. En desarrollo, sin .env, apunta al backend local; un build de
// producción sin VITE_API_URL falla al cargar en vez de apuntar a localhost en silencio.
const apiUrl = import.meta.env.VITE_API_URL

if (!apiUrl && import.meta.env.PROD) {
  throw new Error('Falta VITE_API_URL: el build de producción necesita la URL de la API')
}

export const API_URL = apiUrl || 'http://localhost:8080'
