package io.github.patorinaldi.gastos.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Pasa el hogar del usuario autenticado a {@link CurrentHousehold}, de donde lo toma
 * {@link HouseholdTransactionListener} al comenzar cada transacción de la petición.
 *
 * <p>Corre después de la autenticación. Una petición sin usuario autenticado (el login, el
 * registro, el chequeo de estado) sigue sin hogar en contexto, y las tablas con RLS se comportan
 * como vacías (RNF-03).
 *
 * <p>El {@code finally} es lo importante: el servidor reutiliza los hilos, y sin limpiar el
 * contexto la siguiente petición que tome este hilo arrancaría con el hogar de la anterior.
 *
 * <p>No es un {@code @Component}: Spring Boot lo registraría también como filtro del servlet,
 * fuera de la cadena de seguridad, y correría dos veces.
 */
public class HouseholdContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            CurrentHousehold.set(user.householdId());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            CurrentHousehold.clear();
        }
    }
}