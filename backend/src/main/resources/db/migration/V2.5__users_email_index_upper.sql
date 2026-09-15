-- V2.5 · Índice de email alineado con la función que usa Spring Data JPA
--
-- findByEmailIgnoreCase / existsByEmailIgnoreCase (UserRepository) generan JPQL
-- con upper(email) = upper(?1): Spring Data JPA usa JpqlQueryTemplates.UPPER como
-- default para el keyword IgnoreCase en query derivada por nombre de método, no
-- lower(). El índice único de V1 estaba sobre lower(email), por lo que esas dos
-- queries de login no podían usarlo y terminaban en seq scan sobre users.
drop index users_email_key;
create unique index users_email_key on users (upper(email));
