# Casos de uso

Proyecto Gastos. Trabajo Final Integrador, TUP UTN.

Especificación detallada de los flujos críticos del sistema. Cada caso documenta el flujo
principal, los flujos alternativos (caminos secundarios válidos) y los flujos de excepción (manejo
de errores). Las historias de [historias-de-usuario.md](historias-de-usuario.md) expresan el valor.
Estos casos, la secuencia paso a paso.

## Índice

| ID | Caso de uso | Actor | Módulo |
|---|---|---|---|
| CU-01 | Registrar cuenta y crear hogar | Visitante | M1, M2, M4 |
| CU-02 | Iniciar sesión | Integrante | M1 |
| CU-03 | Incorporarse a un hogar existente | Visitante registrado | M2 |
| CU-04 | Registrar un gasto | Integrante | M3, M4 |
| CU-05 | Crear regla y reclasificar retroactivamente | Integrante | M4 |
| CU-06 | Consultar el análisis del período | Integrante | M5 |
| CU-07 | Capturar un gasto desde un cliente automatizado | Cliente automatizado | M6 |
| CU-08 | Restablecer la contraseña | Visitante | M1 |

---

## CU-01. Registrar cuenta y crear hogar

Actor principal: visitante. Módulos: M1 Identidad, M2 Hogares, M4 Categorización.
Requisitos: RF-01, RF-02, RF-03. Reglas: RN-01, RN-03, RN-11.

**Precondiciones.** El visitante no tiene cuenta en el sistema.

**Postcondiciones.** Existe un usuario no verificado, un hogar propio y el catálogo inicial de ese
hogar. Se envió un correo con el enlace de verificación.

**Flujo principal**

1. El visitante abre la pantalla de registro e ingresa nombre, correo y contraseña.
2. El sistema valida el formato del correo y la longitud mínima de la contraseña.
3. El sistema verifica que el correo no esté registrado, sin distinguir mayúsculas (RN-01).
4. El sistema abre una transacción y, dentro de ella:
   a. crea el hogar;
   b. crea el usuario asociado a ese hogar, con la contraseña cifrada y el correo sin verificar;
   c. fija el hogar recién creado como contexto de la transacción;
   d. siembra el catálogo inicial de categorías y reglas de ese hogar.
5. El sistema confirma la transacción.
6. El sistema genera un token de verificación de un solo uso, con vencimiento a 24 horas, y guarda
   solo su hash (RN-03).
7. El sistema envía el correo con el enlace de verificación, que es el único lugar donde existe el
   token en claro.
8. El sistema informa al visitante que revise su casilla.

**Flujo alternativo 3a. El correo ya está registrado**

- 3a.1. El sistema interrumpe el alta y responde que el correo ya está en uso.
- 3a.2. El sistema ofrece el acceso al restablecimiento de contraseña (CU-08).
- Fin del caso sin crear nada.

**Flujo de excepción 4d. Falla la siembra del catálogo**

- 4d.1. El sistema revierte la transacción completa.
- 4d.2. No queda creado ni el usuario ni el hogar (RN-11).
- 4d.3. El sistema informa un error temporal y sugiere reintentar.

**Flujo de excepción 7a. Falla el envío del correo**

- 7a.1. El sistema registra el fallo sin exponer el token.
- 7a.2. La cuenta y el hogar permanecen creados. El envío no es parte de la transacción.
- 7a.3. El sistema informa al visitante que puede solicitar el reenvío del correo (RNF-21).

---

## CU-02. Iniciar sesión

Actor principal: integrante. Módulo: M1. Requisitos: RF-05. Reglas: RN-02. No funcionales: RNF-06.

**Precondiciones.** El usuario tiene cuenta y confirmó su correo.

**Postcondiciones.** El cliente posee un token de sesión que incluye el identificador del hogar.

**Flujo principal**

1. El integrante ingresa correo y contraseña.
2. El sistema busca el usuario por correo, sin distinguir mayúsculas.
3. El sistema verifica que la cuenta no esté bloqueada por intentos fallidos.
4. El sistema compara la contraseña contra el hash almacenado.
5. El sistema verifica que el correo esté confirmado (RN-02).
6. El sistema emite un token de sesión que incluye el hogar del usuario.
7. El sistema reinicia el contador de intentos fallidos.

**Flujo de excepción 4a. Credenciales inválidas**

- 4a.1. El sistema incrementa el contador de intentos fallidos de la cuenta.
- 4a.2. El sistema responde con un mensaje genérico, idéntico al de correo inexistente, para no
  revelar qué correos están registrados.

**Flujo de excepción 3a. Cuenta bloqueada**

- 3a.1. Tras 10 intentos fallidos, el sistema rechaza los intentos durante 15 minutos (RNF-06).
- 3a.2. El sistema informa el bloqueo temporal sin precisar cuántos intentos restan.

**Flujo de excepción 5a. Correo sin verificar**

- 5a.1. El sistema rechaza el acceso e indica que debe confirmar el correo.
- 5a.2. El sistema ofrece reenviar el enlace de verificación.

---

## CU-03. Incorporarse a un hogar existente

Actor principal: visitante registrado. Módulo: M2. Requisitos: RF-09. Reglas: RN-04, RN-05.

**Precondiciones.** El usuario tiene cuenta verificada. Un integrante del hogar destino generó un
código de invitación vigente.

**Postcondiciones.** El usuario pasa a integrar el hogar de la invitación. El código queda
consumido.

**Flujo principal**

1. El usuario ingresa el código de invitación que le compartieron.
2. El sistema busca la invitación por el hash del código, ya que no guarda el código en claro, y
   verifica que exista, no esté canjeada y no haya vencido (RN-05).
3. El sistema traslada al usuario al hogar de la invitación.
4. El sistema marca la invitación como canjeada, registrando quién la canjeó y cuándo.
5. El sistema emite un token de sesión nuevo, con el hogar actualizado.
6. El usuario ve los gastos ya cargados por los integrantes de ese hogar.

**Flujo alternativo 3a. El usuario tenía un hogar propio con datos**

- 3a.1. El sistema advierte que los gastos cargados en su hogar anterior dejarán de estar visibles.
- 3a.2. Si el usuario confirma, el caso continúa en el paso 3.
- 3a.3. Si no confirma, el caso termina sin cambios.

Nota de alcance: la migración de los datos del hogar anterior al nuevo no está comprometida para
esta entrega. Como un usuario pertenece a un solo hogar (RN-04), el caso habitual es canjear la
invitación antes de cargar gastos.

**Flujo de excepción 2a. Código vencido o ya canjeado**

- 2a.1. El sistema rechaza el canje indicando que solicite un código nuevo.
- 2a.2. El usuario permanece en su hogar actual.

**Flujo de excepción 2b. Código inexistente**

- 2b.1. El sistema responde con el mismo mensaje que para un código vencido, para no permitir
  descubrir códigos válidos por tanteo.

---

## CU-04. Registrar un gasto

Actor principal: integrante. Módulos: M3 Gastos, M4 Categorización.
Requisitos: RF-13, RF-14, RF-20, RF-21. Reglas: RN-06, RN-07, RN-12.

**Precondiciones.** El integrante tiene sesión activa.

**Postcondiciones.** Existe un gasto en el hogar, con categoría resuelta o sin ella.

**Flujo principal**

1. El integrante abre el formulario de alta e ingresa importe y comercio.
2. El sistema normaliza el importe y valida que sea mayor que cero (RN-06).
3. El sistema valida que el comercio no esté vacío (RN-07).
4. El sistema asigna la fecha del día y al integrante autenticado como responsable (RF-14).
5. El sistema consulta el motor de reglas del hogar con el nombre del comercio.
6. El motor encuentra una regla cuyo patrón coincide y devuelve su categoría.
7. El sistema registra el gasto con esa categoría.
8. El sistema muestra el gasto en el listado y actualiza los totales del período.

**Flujo alternativo 1a. El integrante completa campos opcionales**

- 1a.1. Indica fecha, medio de pago o categoría distintos de los predeterminados.
- 1a.2. Si indica categoría explícitamente, el sistema omite los pasos 5 y 6 y respeta esa elección.

**Flujo alternativo 6a. Ningún patrón coincide**

- 6a.1. El sistema registra el gasto sin categoría (RN-12).
- 6a.2. El gasto aparece en la bandeja de no categorizados, desde donde se resuelve con CU-05.

**Flujo de excepción 2a. Importe inválido**

- 2a.1. El sistema rechaza el alta indicando que el importe debe ser mayor que cero.
- 2a.2. El formulario conserva lo ingresado para que el integrante corrija sin retipear.

**Flujo de excepción 7a. Medio de pago no admitido**

- 7a.1. Si el valor no es `Efectivo`, `Tarjeta` ni `Transferencia`, el sistema rechaza el alta
  (RN-07).

---

## CU-05. Crear regla y reclasificar retroactivamente

Actor principal: integrante. Módulo: M4. Requisitos: RF-23. Reglas: RN-10, RN-13.

**Precondiciones.** El integrante tiene sesión activa. Existe al menos un gasto sin categoría.
Habitualmente se llega desde la bandeja.

**Postcondiciones.** Existe una regla nueva en el hogar. Los gastos sin categoría que coinciden con
el patrón quedan clasificados.

**Flujo principal**

1. El integrante abre la bandeja de no categorizados y elige un gasto.
2. El sistema propone como patrón el nombre del comercio, normalizado.
3. El integrante ajusta el patrón si hace falta y elige la categoría destino.
4. El sistema verifica que el patrón no exista ya en el hogar (RN-10).
5. El sistema crea la regla.
6. El sistema busca los gastos sin categoría del hogar que coinciden con el patrón (RN-13).
7. El sistema les asigna la categoría de la regla.
8. El sistema informa cuántos gastos se reclasificaron y los quita de la bandeja.

**Flujo alternativo 3a. La categoría destino no existe**

- 3a.1. El integrante crea la categoría en el momento.
- 3a.2. El sistema verifica que su nombre no esté duplicado en el hogar (RN-09).
- 3a.3. El caso continúa en el paso 4.

**Flujo alternativo 6a. Hay gastos ya clasificados que coinciden**

- 6a.1. El sistema no los modifica. Una clasificación existente es una decisión previa, y
  eventualmente una corrección manual, que una regla nueva no debe sobrescribir (RN-13).

**Flujo de excepción 4a. Patrón duplicado**

- 4a.1. El sistema rechaza la creación e informa qué categoría tiene asignada la regla existente.
- 4a.2. El integrante puede editar esa regla en lugar de crear una nueva.

---

## CU-06. Consultar el análisis del período

Actor principal: integrante. Módulo: M5. Requisitos: RF-25 a RF-27. Reglas: RN-14.
No funcionales: RNF-11.

**Precondiciones.** El integrante tiene sesión activa.

**Flujo principal**

1. El integrante abre el tablero.
2. El sistema toma como período el mes en curso (RD-07).
3. El sistema propaga el hogar del token al contexto de la consulta.
4. El sistema calcula, agrupando en la base de datos, el total del período y los subtotales por
   categoría, por integrante y por medio de pago.
5. El sistema calcula la serie mensual y su media histórica sobre los meses con gasto registrado
   (RN-14).
6. El sistema presenta los indicadores, los gráficos y los movimientos recientes.

**Flujo alternativo 2a. El integrante cambia el período**

- 2a.1. Selecciona otro mes o un rango.
- 2a.2. El caso continúa en el paso 3 con el período elegido.

**Flujo alternativo 6a. Filtrado interactivo**

- 6a.1. El integrante selecciona un mes en el gráfico de evolución o una porción de un gráfico de
  distribución.
- 6a.2. El sistema re-filtra el resto de la pantalla con esa dimensión (RF-37).

**Flujo alternativo 4a. El hogar no tiene gastos en el período**

- 4a.1. El sistema presenta un estado vacío explicando cómo cargar el primer gasto (RNF-16), en
  lugar de gráficos en blanco.

---

## CU-07. Capturar un gasto desde un cliente automatizado

Actor principal: cliente automatizado. Módulo: M6. Requisitos: RF-30 a RF-32.
No funcionales: RNF-10.

**Precondiciones.** Existe un token de cliente máquina vigente, asociado a un hogar y a un
integrante.

**Flujo principal**

1. El cliente envía importe y comercio al punto de captura, con su token en la cabecera.
2. El sistema busca el token por su hash, ya que no guarda el valor en claro, y obtiene el hogar y
   el integrante asociados.
3. El sistema normaliza el importe, aceptando formato local (`1.234,56`), anglosajón (`1,234.56`)
   o sin separadores (RF-32).
4. El sistema valida el importe resultante contra RN-06 y el comercio contra RN-07.
5. El sistema resuelve la categoría con el motor de reglas del hogar.
6. El sistema registra el gasto y devuelve su identificador y el importe normalizado.
7. El sistema registra en el token el momento de su último uso.

**Flujo de excepción 2a. Token inválido o revocado**

- 2a.1. El sistema rechaza la petición sin indicar si el token existió alguna vez.

**Flujo de excepción 3a. El importe no se puede interpretar**

- 3a.1. El sistema rechaza la captura con un error que indica el formato esperado.
- 3a.2. El sistema no registra el gasto con un importe adivinado. Es preferible no registrarlo a
  registrarlo mal, porque un importe equivocado altera los totales y nadie lo revisa.

---

## CU-08. Restablecer la contraseña

Actor principal: visitante. Módulo: M1. Requisitos: RF-06. Reglas: RN-03.
No funcionales: RNF-08.

**Precondiciones.** Ninguna. El caso admite correos sin cuenta (ver 1a).

**Flujo principal**

1. El visitante solicita el restablecimiento indicando su correo.
2. El sistema genera un token de restablecimiento de un solo uso, con vencimiento a 24 horas, y
   guarda solo su hash.
3. El sistema envía el correo con el enlace, que es el único lugar donde existe el token en claro.
4. El visitante abre el enlace e ingresa la contraseña nueva.
5. El sistema busca el token por su hash y valida que sea de restablecimiento, que no esté usado ni
   vencido, y que la contraseña cumpla la longitud mínima.
6. El sistema actualiza el hash de la contraseña y registra el token como usado.
7. El sistema informa el cambio y ofrece iniciar sesión.

**Flujo alternativo 1a. El correo no está registrado**

- 1a.1. El sistema responde exactamente igual que si existiera, y no envía correo.
- 1a.2. Motivo: una respuesta diferenciada permitiría enumerar qué correos tienen cuenta.

**Flujo de excepción 5a. Token vencido o ya utilizado**

- 5a.1. El sistema rechaza el cambio e invita a solicitar uno nuevo (RN-03).

---

## El aislamiento como condición transversal

Ninguno de los casos anteriores incluye pasos de verificación de pertenencia al hogar, y es
deliberado. El aislamiento no se implementa como un paso más de cada flujo, que podría olvidarse en
alguno, sino como una condición del entorno de ejecución. La sesión fija el hogar al inicio de cada
transacción y las políticas del motor filtran toda consulta.

La consecuencia, válida para todos los casos de uso, es que si un actor intenta operar sobre datos
de otro hogar, aun conociendo sus identificadores exactos, el sistema responde como si esos datos
no existieran. Las lecturas devuelven vacío y las escrituras se rechazan.

