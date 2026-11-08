/**
 * Paquete Service: Encapsula la lógica de negocio de la aplicación.
 * <p>
 * Este paquete contiene las clases que implementan la lógica de negocio, aplicando las reglas y procesos
 * específicos del dominio del sistema. La capa de servicios es responsable de coordinar la interacción
 * entre la interfaz de usuario y la capa de persistencia, asegurando que los datos sean procesados de
 * acuerdo con las reglas establecidas antes de enviarlos al repositorio o presentarlos al usuario.
 * <p>
 * Entre las clases de este paquete se incluye, por ejemplo, {@code LoginService}, la cual se encarga
 * de validar las credenciales del usuario durante el proceso de autenticación, y {@code MainMenuMosaicoService},
 * que gestiona la lógica de negocio detrás de la personalización del menú principal según el tipo de usuario.
 * <p>
 * La capa de servicios permite mantener una separación clara de responsabilidades, centralizando la lógica
 * de negocio y asegurando que esta no esté dispersa en las clases del controlador ni en las del modelo.
 * Esto facilita el mantenimiento y la prueba de la lógica crítica del sistema.
 */
package ar.com.hmu.service;
