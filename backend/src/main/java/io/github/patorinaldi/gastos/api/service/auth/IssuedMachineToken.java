package io.github.patorinaldi.gastos.api.service.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * Un token de cliente máquina recién emitido, con su valor en claro. Es la única vez que existe: la
 * base guarda solo el hash.
 */
public record IssuedMachineToken(UUID id, String name, String token, Instant createdAt) {

    // Sin esto, el toString del record mostraría el token si alguien lo registra en un log.
    @Override
    public String toString() {
        return "IssuedMachineToken[id=" + id + ", name=" + name + ", createdAt=" + createdAt + "]";
    }
}
