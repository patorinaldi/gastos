package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.domain.Household;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * La tabla households todavía no tiene RLS, así que
 * el acceso no está acotado por el contexto de hogar y hay que buscar siempre por id.
 */
public interface HouseholdRepository extends JpaRepository<Household, UUID> {
}
