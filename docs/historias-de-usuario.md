# Historias de usuario

Proyecto Gastos. Trabajo Final Integrador, TUP UTN.

Las historias describen la funcionalidad desde la perspectiva de quien la usa; los requisitos de
[requerimientos.md](requerimientos.md) la describen desde la del sistema. Ambos documentos cubren
el mismo alcance y se referencian mutuamente.

Cada historia se redactó siguiendo **INVEST** (Independiente, Negociable, Valiosa, Estimable,
Pequeña, Testeable) y sus criterios de aceptación están en formato **BDD (Given-When-Then)**, de
modo que cada criterio se traduce directamente en una prueba automatizada.

## Actores

| Actor | Descripción |
|---|---|
| **Visitante** | Persona sin cuenta. Solo puede registrarse o recuperar su contraseña. |
| **Integrante** | Usuario registrado y verificado, perteneciente a un hogar. Es el actor principal. |
| **Cliente automatizado** | Programa que registra gastos mediante token de máquina, sin sesión. |

> Todos los integrantes tienen las mismas atribuciones sobre los datos de su hogar. No hay roles
> diferenciados dentro del hogar: la economía es compartida y quien la comparte puede corregirla.

---

## M1. Identidad y acceso

### HU-01 · Registro con verificación de correo

> **Como** visitante,
> **quiero** crear una cuenta con mi correo y una contraseña
> **para** empezar a registrar los gastos de mi casa.

*Requisitos: RF-01, RF-02, RF-03 · Reglas: RN-01, RN-11*

```gherkin
Escenario: Alta exitosa
  Dado que no existe una cuenta con el correo "ana@ejemplo.com"
  Cuando me registro con nombre "Ana", ese correo y una contraseña válida
  Entonces el sistema crea mi cuenta y un hogar nuevo asociado a ella
  Y siembra en ese hogar el catálogo inicial de categorías y reglas
  Y me envía un correo con un enlace de verificación
  Y mi cuenta queda como no verificada

Escenario: Correo ya registrado
  Dado que existe una cuenta con el correo "ana@ejemplo.com"
  Cuando intento registrarme con el correo "ANA@EJEMPLO.COM"
  Entonces el sistema rechaza el alta indicando que el correo ya está en uso
  Y no crea ningún hogar

Escenario: El alta es atómica
  Dado que la siembra del catálogo inicial falla
  Cuando intento registrarme
  Entonces no queda creada ni la cuenta ni el hogar
```

### HU-02 · Confirmación del correo

> **Como** visitante que se registró,
> **quiero** confirmar mi correo desde el enlace que recibí
> **para** poder entrar al sistema.

*Requisitos: RF-03, RF-04 · Reglas: RN-02, RN-03*

```gherkin
Escenario: Confirmación válida
  Dado que recibí un enlace de verificación hace menos de 24 horas
  Cuando lo abro
  Entonces mi cuenta queda verificada
  Y puedo iniciar sesión

Escenario: Token ya usado
  Dado que ya confirmé mi correo con ese enlace
  Cuando vuelvo a abrirlo
  Entonces el sistema informa que el enlace ya no es válido

Escenario: Token vencido
  Dado que el enlace se emitió hace más de 24 horas
  Cuando lo abro
  Entonces el sistema informa que venció y me ofrece reenviarlo
```

### HU-03 · Inicio de sesión

> **Como** integrante,
> **quiero** entrar con mi correo y contraseña
> **para** ver y cargar los gastos de mi hogar.

*Requisitos: RF-05 · Reglas: RN-02 · No funcionales: RNF-06*

```gherkin
Escenario: Acceso correcto
  Dado que mi cuenta está verificada
  Cuando ingreso mi correo y mi contraseña correctos
  Entonces el sistema me devuelve un token de sesión que incluye mi hogar

Escenario: Cuenta sin verificar
  Dado que no confirmé mi correo
  Cuando ingreso credenciales correctas
  Entonces el sistema rechaza el acceso e indica que debo confirmar mi correo

Escenario: Límite de intentos
  Dado que fallé 10 veces seguidas la contraseña
  Cuando intento nuevamente
  Entonces el sistema rechaza el intento durante 15 minutos
  Y no revela si el correo existe
```

### HU-04 · Recuperación de contraseña

> **Como** integrante que olvidó su contraseña,
> **quiero** restablecerla desde un enlace enviado a mi correo
> **para** recuperar el acceso a mi cuenta de forma segura.

*Requisitos: RF-06 · Reglas: RN-03*

```gherkin
Escenario: Restablecimiento exitoso
  Dado que solicité recuperar mi contraseña
  Y recibí un enlace válido por 24 horas
  Cuando lo abro e ingreso una contraseña nueva
  Entonces el sistema actualiza mi contraseña
  Y el enlace deja de ser válido

Escenario: No se revela si el correo existe
  Dado que ingreso un correo no registrado
  Cuando solicito recuperar la contraseña
  Entonces el sistema responde igual que si existiera
```

---

## M2. Hogares

### HU-05 · Invitar a un integrante

> **Como** integrante,
> **quiero** generar un código para invitar a quien convive conmigo
> **para** que sus gastos aparezcan en la misma vista que los míos.

*Requisitos: RF-08, RF-12 · Reglas: RN-05*

```gherkin
Escenario: Generar el código
  Dado que pertenezco a un hogar
  Cuando genero una invitación
  Entonces el sistema me devuelve un código con su fecha de vencimiento

Escenario: El código se canjea una sola vez
  Dado que alguien ya canjeó el código que generé
  Cuando otra persona intenta canjearlo
  Entonces el sistema lo rechaza por estar ya utilizado
```

### HU-06 · Unirse a un hogar

> **Como** visitante registrado,
> **quiero** ingresar el código que me pasaron
> **para** sumarme al hogar de mi conviviente en lugar de quedarme en uno propio.

*Requisitos: RF-09 · Reglas: RN-04, RN-05*

```gherkin
Escenario: Canje exitoso
  Dado que tengo un código de invitación vigente
  Cuando lo canjeo
  Entonces paso a integrar ese hogar
  Y veo los gastos ya cargados por sus integrantes

Escenario: Código vencido
  Dado que el código venció
  Cuando lo canjeo
  Entonces el sistema lo rechaza e indica que solicite uno nuevo
```

### HU-07 · Ver los integrantes

> **Como** integrante,
> **quiero** ver quiénes componen mi hogar
> **para** saber de quién son los gastos que aparecen.

*Requisitos: RF-10*

```gherkin
Escenario: Listado del hogar
  Dado que mi hogar tiene tres integrantes
  Cuando abro la pantalla del hogar
  Entonces veo los tres con su nombre
  Y no veo integrantes de ningún otro hogar
```

---

## M3. Gastos

### HU-08 · Registrar un gasto con el mínimo esfuerzo

> **Como** integrante,
> **quiero** registrar un gasto indicando solo el importe y el comercio
> **para** que anotarlo no me cueste más que el gasto mismo.

*Requisitos: RF-13, RF-14, RF-20 · Reglas: RN-06, RN-07, RN-12, RN-17*

```gherkin
Escenario: Alta mínima con categoría automática
  Dado que mi hogar tiene una regla que asocia "carrefour" a "Supermercado"
  Cuando registro un gasto de 1234,56 en "Carrefour"
  Entonces el gasto queda con fecha de hoy
  Y con mi usuario como responsable
  Y con la categoría "Supermercado"

Escenario: Coinciden varias reglas y gana el patrón más largo
  Dado que mi hogar tiene una regla que asocia "uber" a "Transporte"
  Y otra que asocia "uber eats" a "Restaurantes y delivery"
  Cuando registro un gasto en "Uber Eats"
  Entonces el gasto queda con la categoría "Restaurantes y delivery"

Escenario: Comercio sin regla
  Dado que ninguna regla de mi hogar coincide con "Almacén de Pepe"
  Cuando registro un gasto en ese comercio
  Entonces el gasto se guarda sin categoría
  Y aparece en la bandeja de no categorizados

Escenario: Importe inválido
  Cuando intento registrar un gasto de importe 0
  Entonces el sistema lo rechaza indicando que debe ser mayor que cero
```

### HU-09 · Corregir o dar de baja un gasto

> **Como** integrante,
> **quiero** editar o dar de baja un gasto ya cargado
> **para** arreglar un error de tipeo sin que quede sucio el análisis.

*Requisitos: RF-15, RF-16 · Reglas: RN-06, RN-07, RN-08, RN-16*

```gherkin
Escenario: Edición
  Dado que cargué un gasto con el importe equivocado
  Cuando lo edito con el importe correcto
  Entonces el gasto queda actualizado
  Y los totales del período lo reflejan

Escenario: Baja de un gasto
  Dado que cargué por error un gasto que no ocurrió
  Cuando lo doy de baja
  Entonces deja de figurar en el listado, en la bandeja y en el análisis
  Y el gasto se conserva con su importe, su comercio y su responsable

Escenario: Puedo corregir gastos de otro integrante
  Dado que un gasto lo cargó otro integrante de mi hogar
  Cuando lo edito
  Entonces el sistema lo permite

Escenario: No puedo tocar gastos de otro hogar
  Dado que conozco el identificador de un gasto de otro hogar
  Cuando intento editarlo o darlo de baja
  Entonces el sistema responde como si no existiera
```

### HU-10 · Buscar entre los movimientos

> **Como** integrante,
> **quiero** filtrar los gastos por período, categoría, persona y medio de pago
> **para** encontrar un movimiento concreto o entender un consumo puntual.

*Requisitos: RF-17, RF-18 · No funcionales: RNF-12*

```gherkin
Escenario: Filtros combinados
  Dado que mi hogar tiene gastos de varios meses y categorías
  Cuando filtro por septiembre, categoría "Transporte" y medio "Tarjeta"
  Entonces veo solo los gastos que cumplen las tres condiciones
  Y ordenados por fecha descendente

Escenario: Paginación
  Dado que hay 250 gastos en el período
  Cuando abro el listado
  Entonces recibo la primera página
  Y puedo pedir las siguientes
```

---

## M4. Categorización

### HU-11 · Vaciar la bandeja de no categorizados

> **Como** integrante,
> **quiero** ver los gastos que el sistema no supo clasificar
> **para** enseñarle esos comercios y que no vuelva a preguntarme.

*Requisitos: RF-22, RF-23 · Reglas: RN-13*

```gherkin
Escenario: Crear una regla desde la bandeja
  Dado que tengo 7 gastos sin categoría en "Almacén de Pepe"
  Cuando creo una regla que asocia "almacen de pepe" a "Supermercado"
  Entonces los 7 gastos quedan clasificados como "Supermercado"
  Y desaparecen de la bandeja

Escenario: La regla no pisa clasificaciones previas
  Dado que tengo un gasto en ese comercio ya clasificado como "Hogar"
  Cuando creo la regla que lo asociaría a "Supermercado"
  Entonces ese gasto conserva la categoría "Hogar"

Escenario: Patrón duplicado
  Dado que ya existe una regla con el patrón "carrefour"
  Cuando intento crear otra con el mismo patrón
  Entonces el sistema la rechaza
```

### HU-12 · Administrar el catálogo de categorías

> **Como** integrante,
> **quiero** agregar, renombrar o dar de baja categorías
> **para** que reflejen cómo gasta mi casa y no una lista genérica.

*Requisitos: RF-24 · Reglas: RN-09, RN-16*

```gherkin
Escenario: Nombre duplicado
  Dado que mi hogar ya tiene la categoría "Supermercado"
  Cuando intento crear "supermercado"
  Entonces el sistema la rechaza por duplicada

Escenario: Baja de una categoría con gastos asociados
  Dado que la categoría "Transporte" tiene gastos asociados
  Cuando la doy de baja
  Entonces deja de ofrecerse para clasificar gastos nuevos
  Y sus reglas dejan de aplicarse
  Y los gastos que ya la tenían la conservan en el historial y en el análisis
```

---

## M5. Análisis

### HU-13 · Entender el gasto del período

> **Como** integrante,
> **quiero** ver el total del mes y su distribución por categoría, persona y medio de pago
> **para** saber en qué se nos va el dinero.

*Requisitos: RF-25, RF-26 · Reglas: RN-16 · No funcionales: RNF-11*

```gherkin
Escenario: Resumen del mes
  Dado que mi hogar registró gastos en septiembre
  Cuando abro el tablero con ese período
  Entonces veo el total del período
  Y su desglose por categoría, por integrante y por medio de pago
  Y los totales solo incluyen gastos de mi hogar

Escenario: Los gastos dados de baja no cuentan
  Dado que en septiembre di de baja un gasto de 5000
  Cuando abro el tablero con ese período
  Entonces el total y los desgloses no incluyen ese gasto
```

### HU-14 · Comparar contra lo habitual

> **Como** integrante,
> **quiero** ver la evolución mensual contra la media histórica
> **para** darme cuenta si este mes nos fuimos de lo normal.

*Requisitos: RF-27 · Reglas: RN-14*

```gherkin
Escenario: Serie con media
  Dado que mi hogar registra gastos desde marzo
  Cuando consulto la evolución mensual
  Entonces veo un punto por cada mes con gasto
  Y la media histórica calculada solo sobre esos meses
  Y los meses anteriores a marzo no se cuentan como cero
```

### HU-15 · Explorar desde los gráficos

> **Como** integrante,
> **quiero** que al tocar un mes o una categoría se filtre el resto de la pantalla
> **para** investigar un consumo sin armar filtros a mano.

*Requisitos: RF-37*

```gherkin
Escenario: Filtrado interactivo
  Dado que estoy viendo el tablero del año
  Cuando selecciono el mes de julio en el gráfico de evolución
  Entonces el total, los desgloses y el listado pasan a mostrar solo julio
```

---

## M6. API de integración

### HU-16 · Registrar un gasto desde el teléfono

> **Como** integrante con una automatización en mi teléfono,
> **quiero** que registre el gasto apenas me llega la notificación del banco
> **para** no tener que abrir la aplicación.

*Requisitos: RF-30, RF-31, RF-32 · No funcionales: RNF-10*

```gherkin
Escenario: Captura rápida
  Dado que tengo un token de cliente máquina válido
  Cuando envío importe "1.234,56" y comercio "Carrefour"
  Entonces el sistema registra el gasto con importe 1234.56
  Y le aplica las reglas de categorización de mi hogar

Escenario: Formato de importe anglosajón
  Cuando envío el importe "1,234.56"
  Entonces el sistema lo interpreta como 1234.56

Escenario: Token revocado
  Dado que revoqué ese token
  Cuando intento registrar un gasto
  Entonces el sistema rechaza la petición
```

---

## Trazabilidad

| Historia | Módulo | Requisitos | Reglas |
|---|---|---|---|
| HU-01 Registro | M1 | RF-01, RF-02, RF-03 | RN-01, RN-11 |
| HU-02 Confirmación de correo | M1 | RF-03, RF-04 | RN-02, RN-03 |
| HU-03 Inicio de sesión | M1 | RF-05 | RN-02 |
| HU-04 Recuperación de contraseña | M1 | RF-06 | RN-03 |
| HU-05 Invitar | M2 | RF-08, RF-12 | RN-05 |
| HU-06 Unirse a un hogar | M2 | RF-09 | RN-04, RN-05 |
| HU-07 Ver integrantes | M2 | RF-10 | ninguna |
| HU-08 Registrar un gasto | M3 | RF-13, RF-14, RF-20 | RN-06, RN-07, RN-12, RN-17 |
| HU-09 Corregir o dar de baja | M3 | RF-15, RF-16 | RN-06, RN-08, RN-16 |
| HU-10 Buscar movimientos | M3 | RF-17, RF-18 | ninguna |
| HU-11 Vaciar la bandeja | M4 | RF-22, RF-23 | RN-13 |
| HU-12 Catálogo de categorías | M4 | RF-24 | RN-09, RN-16 |
| HU-13 Gasto del período | M5 | RF-25, RF-26 | RN-16 |
| HU-14 Evolución mensual | M5 | RF-27 | RN-14 |
| HU-15 Filtrado interactivo | M5, M7 | RF-37 | ninguna |
| HU-16 Captura desde el teléfono | M6 | RF-30 a RF-32 | RN-06, RN-07 |
