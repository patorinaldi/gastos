-- Administrador de la base como el de una Postgres gestionada: dueño de la base y con
-- CREATEROLE, pero sin SUPERUSER, BYPASSRLS ni REPLICATION. Las migraciones corren con este rol,
-- así una sentencia que solo funciona como superusuario falla en las pruebas y no recién al
-- desplegar. Lo ejecuta Testcontainers como superusuario al levantar el contenedor.
create role gastos_admin login createrole password 'gastos_admin';
alter database test owner to gastos_admin;
alter schema public owner to gastos_admin;
