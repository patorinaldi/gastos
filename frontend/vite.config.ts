/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    // Las pruebas corren con la zona horaria de los usuarios. En UTC, o en Europa, un error que
    // corre las fechas un día en Argentina pasaría desapercibido.
    env: { TZ: 'America/Argentina/Buenos_Aires' },
  },
})
