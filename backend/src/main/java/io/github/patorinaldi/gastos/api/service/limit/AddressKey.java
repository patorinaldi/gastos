package io.github.patorinaldi.gastos.api.service.limit;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Clave de una dirección IP para los contadores por dirección.
 */
public final class AddressKey {

    // Bytes que se conservan de una IPv6: los primeros 64 bits, el prefijo que suele recibir
    // entero un solo cliente.
    private static final int IPV6_PREFIX_BYTES = 8;

    private AddressKey() {
    }

    /**
     * Una IPv6 se agrupa por su prefijo /64: un solo cliente suele tener el prefijo entero, y con
     * cada dirección por separado podría rotar entre miles de millones.
     *
     * @param ip dirección del cliente, sin puerto
     */
    public static String of(String ip) {
        // Solo una IPv6 tiene ":". El chequeo evita además que getByName resuelva por DNS algo
        // que no sea una dirección literal.
        if (!ip.contains(":")) {
            return ip;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (!(address instanceof Inet6Address)) {
                // Una IPv4 escrita como IPv6 (::ffff:203.0.113.7) cuenta como la IPv4.
                return address.getHostAddress();
            }
            byte[] bytes = Arrays.copyOf(address.getAddress(), 16);
            Arrays.fill(bytes, IPV6_PREFIX_BYTES, 16, (byte) 0);
            return InetAddress.getByAddress(bytes).getHostAddress() + "/64";
        } catch (UnknownHostException e) {
            return ip;
        }
    }
}
