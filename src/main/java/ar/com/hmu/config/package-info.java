/**
 * Este paquete contiene las clases responsables de la configuración general del sistema.
 * <p>
 * Las clases incluidas en este paquete permiten centralizar la gestión de configuraciones
 * a partir de archivos externos, como `config.yaml`, proporcionando una única fuente de
 * verdad para los parámetros de conexión, ajustes de la aplicación, y otros valores clave
 * necesarios para el funcionamiento del sistema.
 * <p>
 * Clases principales:
 * <ul>
 *   <li>{@link ar.com.hmu.config.AppConfig}: Contiene las propiedades principales de configuración de la aplicación.</li>
 *   <li>{@link ar.com.hmu.config.AppConfigReader}: Proporciona métodos para leer y cargar configuraciones desde archivos externos.</li>
 *   <li>{@link ar.com.hmu.config.AppConfigSettings}: Define estructuras para representar y manejar configuraciones específicas de la aplicación.</li>
 * </ul>
 * Este enfoque asegura que los parámetros críticos se gestionen de manera centralizada,
 * facilitando el mantenimiento y la modificación de las configuraciones sin tener que realizar
 * cambios dispersos por todo el código de la aplicación.
 */
package ar.com.hmu.config;
