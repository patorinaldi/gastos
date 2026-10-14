package io.github.patorinaldi.gastos.api.web;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dirección IP del cliente, sin puerto.
 *
 * <p>Con {@code server.forward-headers-strategy: native}, Tomcat toma la dirección de
 * {@code X-Forwarded-For} tal como llega, y algunos proxies agregan el puerto del cliente
 * ({@code 203.0.113.7:54321}). Sin quitarlo, cada conexión parecería venir de
 * una dirección distinta, y el límite de intentos de login (RNF-06) no frenaría nada.
 */
final class ClientAddress {

    private static final Pattern IPV4_WITH_PORT = Pattern.compile("^(\\d{1,3}(?:\\.\\d{1,3}){3}):\\d+$");
    private static final Pattern IPV6_WITH_PORT = Pattern.compile("^\\[([0-9A-Fa-f:.]+)]:\\d+$");

    private ClientAddress() {
    }

    // Una IPv6 sin corchetes también tiene ":", así que solo se quita el puerto en las dos formas
    // que no son ambiguas.
    static String withoutPort(String address) {
        Matcher ipv4 = IPV4_WITH_PORT.matcher(address);
        if (ipv4.matches()) {
            return ipv4.group(1);
        }
        Matcher ipv6 = IPV6_WITH_PORT.matcher(address);
        if (ipv6.matches()) {
            return ipv6.group(1);
        }
        return address;
    }
}