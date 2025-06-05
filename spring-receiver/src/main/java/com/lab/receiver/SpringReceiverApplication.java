package com.lab.receiver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringReceiverApplication {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("🚀 Iniciando Servidor TCP Spring Integration...");
        System.out.println("   Escuchando en el puerto 12345");
        System.out.println("=================================================");
        SpringApplication.run(SpringReceiverApplication.class, args);
        System.out.println("\n✅ Servidor TCP Iniciado. Esperando conexiones...");
        System.out.println("   (El servidor seguirá corriendo hasta que lo detengas manually).");
    }
}