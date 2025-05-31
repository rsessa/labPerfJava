package com.lab.receiver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Clase principal que inicia la aplicación Spring Boot.
 * Ahora se ejecutará indefinidamente hasta que se detenga manualmente.
 */
@SpringBootApplication
public class SpringReceiverApplication {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("🚀 Iniciando Servidor TCP Spring Integration...");
        System.out.println("   Escuchando en el puerto 12345 (esperando CRLF)");
        System.out.println("=================================================");

        // Inicia la aplicación Spring.
        // Esto iniciará los hilos del servidor TCP y se quedará corriendo.
        SpringApplication.run(SpringReceiverApplication.class, args);

        System.out.println("\n✅ Servidor TCP Iniciado. Esperando conexiones...");
        System.out.println("   (El servidor seguirá corriendo hasta que lo detengas manualmente).");

        // --- HEMOS QUITADO EL SCANNER Y System.exit(0) ---
        // La aplicación Spring Boot se mantendrá activa por sí misma
        // gracias a sus hilos de servidor. Para detenerla:
        // - Usa el botón 'Stop' (cuadrado rojo) en la consola de VSC.
        // - O cierra la ventana de la terminal.
        // - O usa Ctrl+C si lo ejecutas desde una terminal externa.
    }
}