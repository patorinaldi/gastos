# Guía de despliegue

Proyecto Gastos. Trabajo Final Integrador, TUP UTN.

Cómo se despliega el sistema, qué variables de entorno necesita cada parte y cómo comprobar que un
despliegue quedó bien. La ejecución local está en el [README](../README.md#instalación-y-ejecución-local),
y el diagrama de nodos, en [diagramas.md](diagramas.md#6-diagrama-de-despliegue).

## 1. Arquitectura

| Parte | Dónde corre | Cómo se despliega |
|---|---|---|
| API (`backend/`) | **Azure App Service**, Linux, Java 21, plan gratuito | El trabajo *Despliegue del backend* de [`ci.yml`](../.github/workflows/ci.yml), en cada incorporación a `main`, después de que pasan `Backend` y `Frontend` |
| Base de datos | **Azure Database for PostgreSQL – Flexible Server**, PostgreSQL 17 | Las migraciones de Flyway corren solas al arrancar la API |
| Cliente web (`frontend/`) | **Cloudflare Pages** | Cloudflare compila y publica en cada push, conectado al repositorio |
| Correo | Cualquier proveedor con **SMTP** | La API se conecta con las variables `MAIL_*` |

Las tres partes se hablan por HTTPS. El cliente web llama a la API desde el navegador, así que su
origen tiene que estar permitido en CORS; la API arma los enlaces de los correos con la dirección
del cliente web. Esas tres direcciones tienen que coincidir (ver la [sección 3](#3-coherencia-entre-las-direcciones)).

## 2. Variables de entorno

### API, en App Service

Se cargan en *Configuración → Variables de entorno* de la aplicación. Cada una está mapeada de forma
explícita en `application.yaml`: una variable que no esté ahí no la lee nadie, aunque esté definida
en App Service, y `ApplicationYamlTest` comprueba que el mapeo exista.

| Variable | Obligatoria | Contenido |
|---|---|---|
| `DB_URL` | Sí | URL JDBC de la base, con `sslmode=require` |
| `DB_ADMIN_USERNAME`, `DB_ADMIN_PASSWORD` | Sí | Rol con el que corren las migraciones (`gastos_admin`). Es dueño de la base y del esquema `public`, y tiene `CREATEROLE`, sin ser superusuario. Ver la [sección 4.1](#41-base-de-datos) |
| `DB_API_USERNAME`, `DB_API_PASSWORD` | Sí | Rol con el que se conecta la aplicación (`gastos_api`), sin `BYPASSRLS`. Lo crea la V2 en el primer arranque, con esta contraseña |
| `JWT_SECRET` | **Sí, no arranca sin ella** | Clave de firma del token de sesión, de 32 caracteres o más (RNF-07). Cambiarla cierra todas las sesiones abiertas |
| `WEB_URL` | **Sí, no arranca sin ella** | Dirección del cliente web, por ejemplo `https://gastos.pages.dev`: `http` o `https`, absoluta, sin query ni fragmento. Con ella se arman los enlaces de verificación y de restablecimiento |
| `EMAIL_SENDER` | **Sí, no arranca sin ella** | `smtp`, el envío real, o `console`, que solo escribe los correos en el log. Con `console`, el alta y los pedidos de enlaces responden `503`, para que los enlaces de un solo uso no queden en el log |
| `EMAIL_FROM` | Con `smtp` | Remitente, por ejemplo `Gastos <no-responder@gastos.ejemplo>`. Tiene que ser una dirección que el proveedor permita usar |
| `MAIL_HOST`, `MAIL_PORT` | Con `smtp` | Servidor SMTP del proveedor y su puerto, `587` si no se indica. La conexión exige STARTTLS |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Con `smtp` | Credenciales SMTP del proveedor. La conexión exige autenticación |
| `CORS_ALLOWED_ORIGINS` | En la práctica, sí | Orígenes del cliente web, separados por coma y exactos (`https://gastos.pages.dev`, sin barra final). Vacía, ningún navegador puede llamar a la API desde otro origen |
| `WEBSITES_PORT` | Sí | `8080`, el puerto en el que escucha la aplicación |

**Las que faltan hacen fallar el arranque, a propósito.** Sin `EMAIL_SENDER`, `JWT_SECRET` o
`WEB_URL`, la aplicación no arranca, y con `EMAIL_SENDER=smtp` tampoco si falta `MAIL_HOST`,
`EMAIL_FROM`, `MAIL_USERNAME` o `MAIL_PASSWORD`. Las alternativas son peores: un valor por defecto
de desarrollo dejaría escribiendo tokens en el log o mandando enlaces a `localhost`, y arrancar sin
configurar el correo dejaría el alta respondiendo `201` sin que ningún correo salga. El mensaje de
error del arranque nombra la variable que falta (ver la [sección 8](#8-problemas-frecuentes)).

No hace falta ninguna variable para la zona horaria: la API fija la de Argentina en el código, para
la fecha de los gastos y el mes en curso del análisis, sin depender de la zona del servidor.

### Cliente web, en Cloudflare Pages

| Variable | Obligatoria | Contenido |
|---|---|---|
| `VITE_API_URL` | **Sí, el build sin ella falla al cargar** | Dirección de la API, por ejemplo `https://gastos-api-rrr-d2eyhtcmcabudrgk.canadacentral-01.azurewebsites.net`, sin barra final |
| `NODE_VERSION` | Recomendada | `22`, la misma versión con la que compila la integración continua |

`VITE_API_URL` se lee **en el momento del build**, no al cargar la página: queda escrita en el
JavaScript publicado. Cambiarla exige volver a compilar, por ejemplo con *Retry deployment* en el
panel de Pages. Un build de producción sin ella no apunta a `localhost` en silencio: la aplicación
falla al cargar con un error que la nombra (`src/config.ts`).

## 3. Coherencia entre las direcciones

Tres valores, cargados en dos plataformas distintas, tienen que referirse a las mismas direcciones:

| En | Variable | Tiene que ser |
|---|---|---|
| App Service | `WEB_URL` | la dirección de producción del cliente web |
| App Service | `CORS_ALLOWED_ORIGINS` | incluir esa misma dirección, como origen exacto |
| Cloudflare Pages | `VITE_API_URL` | la dirección de la API en App Service |

Si `WEB_URL` y CORS no coinciden, los correos llevan enlaces a un sitio que no puede llamar a la
API. Si `VITE_API_URL` apunta a otro lado, el cliente web carga pero ninguna pantalla trae datos.

**Las vistas previas de Pages no llegan a la API de producción.** Cloudflare publica cada rama en
una dirección propia (`https://<rama>.<proyecto>.pages.dev`), y CORS solo admite orígenes exactos.
Para probar una rama contra la API de producción hay que agregar su dirección a
`CORS_ALLOWED_ORIGINS`, y sacarla después.

## 4. Primer despliegue

Se hace una sola vez. Los despliegues siguientes son automáticos ([sección 5](#5-despliegues-siguientes)).

### 4.1 Base de datos

Antes del primer despliegue, conectado al servidor con su administrador (el rol que se eligió al
crear el Flexible Server). Desde el portal se puede usar Cloud Shell, que ya trae `psql` y llega al
servidor si está activado *Allow public access from any Azure service*.

1. Crear el rol de las migraciones. Con `\password`, la contraseña no queda escrita en el SQL ni
   en el historial:

   ```bash
   psql "host=<servidor>.postgres.database.azure.com port=5432 dbname=postgres user=<administrador> sslmode=require"
   ```
   ```sql
   create role gastos_admin login createrole createdb;
   \password gastos_admin
   ```

2. Crear la base como `gastos_admin`, para que quede como su dueño:

   ```bash
   psql "host=<servidor>.postgres.database.azure.com port=5432 dbname=postgres user=gastos_admin sslmode=require"
   ```
   ```sql
   create database gastos;
   ```

3. Como el administrador, conectado a la base `gastos`, transferirle el esquema `public` a
   `gastos_admin` y quitarle `CREATEDB`, que ya no necesita:

   ```bash
   psql "host=<servidor>.postgres.database.azure.com port=5432 dbname=gastos user=<administrador> sslmode=require"
   ```
   ```sql
   grant gastos_admin to <administrador>;
   alter schema public owner to gastos_admin;
   revoke gastos_admin from <administrador>;
   alter role gastos_admin nocreatedb;
   ```

**Por qué el paso 3.** En Azure, el esquema `public` de una base nueva pertenece a `azure_pg_admin`,
no al dueño de la base: `gastos_admin` podría usarlo pero no crear tablas, y Flyway fallaría al
crear su tabla de historial (`permission denied for schema public`). El `grant` temporal hace falta
porque, desde PostgreSQL 16, transferirle un objeto a un rol exige poder actuar como ese rol; el
administrador puede concedérselo a sí mismo porque fue quien creó `gastos_admin`. Las pruebas
reproducen este mismo escenario con
[`init-admin-gestionado.sql`](../backend/src/test/resources/db/init-admin-gestionado.sql).

`gastos_api` no se crea a mano: lo crea la V2 en el primer arranque, con `DB_API_PASSWORD`.

### 4.2 API en App Service

1. Crear la aplicación: Linux, pila Java 21, plan gratuito.
2. Cargar las variables de la [sección 2](#api-en-app-service). Para el primer despliegue alcanza
   con `EMAIL_SENDER=console`, si todavía no hay proveedor de correo: la API arranca y el alta
   responde `503` hasta configurar SMTP ([sección 4.4](#44-correo)).
3. En GitHub, el environment `production` del repositorio guarda los identificadores de Azure
   (`AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`). GitHub entra a Azure por OIDC, y
   Azure solo acepta tokens emitidos para este repositorio y ese environment: no hay ninguna
   contraseña de Azure guardada en GitHub.

La dirección de la proxy de App Service ya está configurada en `application.yaml`
(`server.tomcat.remoteip.internal-proxies`). La API toma la dirección real del cliente de
`X-Forwarded-For` solo si la conexión viene de esa proxy; de eso dependen los límites de intentos
por dirección (RNF-06, RNF-30 a RNF-33).

### 4.3 Cliente web en Cloudflare Pages

1. Crear el proyecto conectado al repositorio de GitHub, con rama de producción `main`.
2. Configuración del build:

   | Campo | Valor |
   |---|---|
   | Root directory | `frontend` |
   | Build command | `npm run build` |
   | Build output directory | `dist` |

3. Cargar `VITE_API_URL` y `NODE_VERSION` ([sección 2](#cliente-web-en-cloudflare-pages)) para
   *Production* y para *Preview*.
4. Con la dirección de producción del proyecto, completar en App Service `WEB_URL` y
   `CORS_ALLOWED_ORIGINS` ([sección 3](#3-coherencia-entre-las-direcciones)).

El cliente web es una aplicación de una sola página: todas sus rutas (`/expenses`, `/verify-email`…)
las resuelve React en el navegador. Pages, si el sitio no publica un `404.html` propio, responde
cualquier ruta con `index.html`, que es lo que hace falta para que un enlace del correo o una
recarga en `/expenses` no terminen en un 404.

### 4.4 Correo

Cualquier proveedor que ofrezca SMTP sirve; cambiar de proveedor es cambiar variables, no código.
Para el volumen del proyecto alcanza un plan gratuito: por ejemplo, Brevo permite 300 correos por
día.

1. En el panel del proveedor, verificar el remitente: la dirección o el dominio de `EMAIL_FROM`.
   Sin eso, el proveedor rechaza los envíos.
2. Generar las credenciales SMTP. En la mayoría de los proveedores son una clave específica para
   SMTP, distinta de la contraseña de la cuenta.
3. Cargar en App Service `EMAIL_FROM`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME` y `MAIL_PASSWORD`,
   y recién después `EMAIL_SENDER=smtp` (ver el orden de la [sección 6](#6-cambios-que-suman-variables)).

Un correo que no sale no tira la API: queda en el log con el tipo de error, sin la dirección del
destinatario. El health de la API no consulta al servidor SMTP, justamente para que una caída del
proveedor no haga fallar el despliegue.

## 5. Despliegues siguientes

- **API.** Cada incorporación a `main` corre la integración continua y, si pasa, el trabajo
  *Despliegue del backend*: publica en App Service el mismo jar que se acaba de probar y espera a
  que `/actuator/health` responda `200`, con unos siete minutos de margen. Si no responde, el
  despliegue queda marcado como fallido en GitHub. Dos despliegues nunca corren a la vez.
- **Migraciones.** Corren solas al arrancar, antes de que la API acepte pedidos. Una migración nueva
  entra con el mismo PR que el código que la necesita.
- **Cliente web.** Cloudflare compila y publica `main` en producción, y cada rama en su vista
  previa, sin pasar por GitHub Actions.

## 6. Cambios que suman variables

**Las variables nuevas se cargan antes de desplegar el código que las lee.** Una variable que la
versión en producción no conoce se ignora, así que cargarla primero no la afecta, y el despliegue
siguiente arranca con todo lo que necesita. Al revés, el despliegue falla el health y queda la
versión anterior publicada a medias. Si el PR suma una variable, conviene que lo diga en su
descripción.

La excepción es un **valor nuevo** de una variable que ya existe: la versión anterior no lo
reconoce y no arrancaría. Es el caso de `EMAIL_SENDER=smtp`. Para pasar de `console` a `smtp`:

1. Cargar `EMAIL_FROM`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME` y `MAIL_PASSWORD`.
2. Desplegar la versión que conoce `smtp`, si todavía no está en producción. Mientras
   `EMAIL_SENDER` siga en `console`, el alta y los pedidos de enlaces responden `503`.
3. Cambiar `EMAIL_SENDER` a `smtp`. App Service reinicia la aplicación con el valor nuevo.

## 7. Verificación después del despliegue

Con `API` la dirección de App Service y `WEB` la de Pages. Los pasos van de lo más barato a lo más
completo: si uno falla, la [sección 8](#8-problemas-frecuentes) dice por dónde buscar.

1. **La API está arriba.** El primer pedido después de un rato sin uso puede tardar hasta 30
   segundos ([arranque en frío](#arranque-en-frío)).

   ```bash
   curl -s "$API/actuator/health"
   # {"groups":["liveness","readiness"],"status":"UP"}
   ```

2. **CORS acepta al cliente web.** La respuesta tiene que traer el origen en
   `Access-Control-Allow-Origin`:

   ```bash
   curl -si -X OPTIONS "$API/api/auth/login" \
     -H "Origin: $WEB" -H "Access-Control-Request-Method: POST" | grep -i access-control-allow-origin
   ```

3. **El cliente web carga y llega a la API.** Abrir `WEB/login` e iniciar sesión con una cuenta
   existente. Un error de CORS o de red en la consola del navegador es el paso 2 o `VITE_API_URL`.

4. **El correo sale y los enlaces son correctos.** Crear una cuenta con una casilla propia. El
   correo tiene que llegar con un enlace a `WEB/verify-email#token=...`; abrirlo verifica la cuenta
   y permite iniciar sesión. Con un `503` al crear la cuenta, `EMAIL_SENDER` sigue en `console`.

5. **La captura rápida funciona.** Desde la sesión, emitir un token de cliente máquina y capturar
   un gasto con él:

   ```bash
   curl -s -X POST "$API/api/capture" -H "Authorization: Token $TOKEN" \
     -H "Content-Type: application/json" -d '{"amount": "1.234,56", "merchant": "Prueba"}'
   # {"id":"...","amount":"1234.56",...}
   ```

   Después, dar de baja ese gasto desde el cliente web y revocar el token.

## 8. Problemas frecuentes

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| El despliegue falla en *Verificar que la aplicación responda* | La API no arrancó | Ver el log de App Service (*Supervisión → Secuencia de registro*). El primer error del arranque nombra la causa, en general una variable que falta |
| El log dice `Falta gastos.email.sender (variable EMAIL_SENDER)` | Falta `EMAIL_SENDER` | Cargarla ([sección 2](#api-en-app-service)) |
| El log nombra `gastos.web.url (WEB_URL)` | Falta `WEB_URL`, o no es una dirección `http` o `https` absoluta sin query ni fragmento | Corregirla. `gastos.pages.dev`, sin `https://`, no sirve |
| El log nombra `MAIL_HOST`, `EMAIL_FROM` o `MAIL_USERNAME` y `MAIL_PASSWORD` | `EMAIL_SENDER=smtp` sin la configuración del proveedor | Cargar las variables que nombra ([sección 4.4](#44-correo)) |
| El log dice `Falta gastos.jwt.secret (variable JWT_SECRET)` o que tiene que tener al menos 32 caracteres | `JWT_SECRET` vacía o corta | Cargar una clave larga y aleatoria, por ejemplo con `openssl rand -base64 48` |
| `permission denied for schema public` al migrar | El esquema `public` sigue siendo de `azure_pg_admin` | Paso 3 de la [sección 4.1](#41-base-de-datos) |
| El alta y los pedidos de enlaces responden `503` | `EMAIL_SENDER=console` | Configurar SMTP ([sección 6](#6-cambios-que-suman-variables)) |
| El alta responde `201` pero el correo no llega | El proveedor rechaza el envío: remitente sin verificar o credenciales vencidas | Buscar en el log de App Service el error del envío; revisar el remitente en el panel del proveedor |
| El enlace del correo lleva a otro sitio o a `localhost` | `WEB_URL` desactualizada | Corregirla: los enlaces se arman al enviar, así que los correos ya enviados conservan la dirección vieja |
| La consola del navegador muestra un error de CORS | El origen del cliente web no está en `CORS_ALLOWED_ORIGINS`, o difiere en algo (barra final, `http` en vez de `https`) | Cargar el origen exacto ([sección 3](#3-coherencia-entre-las-direcciones)) |
| El cliente web carga en blanco con `Falta VITE_API_URL` en la consola | El build se hizo sin la variable | Cargarla en Pages y volver a compilar |
| Muchos usuarios reciben `429` al iniciar sesión a la vez | La API cuenta a todos desde la dirección de la proxy | Revisar que la proxy de App Service siga en el rango de `internal-proxies` (`application.yaml`) |

### Arranque en frío

El plan gratuito de App Service no permite *Always On*: después de un rato sin pedidos la
aplicación se detiene, y el primer pedido siguiente la vuelve a levantar. Ese pedido tarda más:
la plataforma inicia el contenedor y Spring Boot tarda entre 15 y 30 segundos en arrancar. Los
pedidos siguientes responden normalmente.

## 9. Volver atrás

El jar que publica el despliegue se guarda un solo día, así que volver a una versión anterior es
**revertir el PR en GitHub e incorporar la reversión**: la integración continua la prueba y la
despliega como cualquier otro cambio.

Las migraciones no se revierten solas. Si la versión que se revierte trajo una migración, la base
la conserva, y la versión anterior arranca igual mientras la migración solo haya agregado tablas o
columnas que esa versión no usa. Si borró o renombró algo, hace falta una migración nueva que lo
corrija, nunca editar ni borrar una ya aplicada (RN-15).
