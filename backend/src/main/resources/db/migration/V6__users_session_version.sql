-- V6 · Versión de sesión de cada usuario
--
-- El token de sesión es autocontenido: el servidor lo acepta mientras la firma sea válida y no
-- haya vencido, sin guardarlo en ningún lado. Para poder invalidarlo antes de tiempo, el token
-- lleva la versión de sesión del usuario al momento de emitirse, y en cada petición se compara
-- con la de esta columna. Incrementarla invalida de una vez todos los tokens emitidos antes, en
-- todos los dispositivos: es lo que hace el restablecimiento de contraseña, que se usa justamente
-- cuando alguien sospecha que otra persona tiene la suya.
--
-- No hace falta un permiso nuevo: gastos_api ya puede actualizar users desde la V2.
alter table users
    add column session_version integer not null default 0,
    add constraint users_session_version_non_negative check (session_version >= 0);