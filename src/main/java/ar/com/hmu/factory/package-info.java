/**
 * Paquete Factory: contiene clases y métodos responsables de la creación de instancias complejas de objetos según su tipo específico.
 * <p>
 * El objetivo principal de este paquete es centralizar la lógica de instanciación, respetando el principio de
 * responsabilidad única (SRP). Al agrupar la lógica de creación en un solo lugar, se facilita el mantenimiento,
 * la reutilización del código y la separación clara de responsabilidades dentro de la aplicación.
 * <p>
 * Este paquete incluye la clase {@code UsuarioFactory}, encargada de la creación de instancias de
 * {@code Usuario} a partir de los datos obtenidos de la base de datos. El "tipo" del usuario
 * (Empleado, JefaturaDeServicio, OficinaDePersonal, Direccion) se resuelve por sus roles asignados,
 * no por una columna discriminadora.
 */
package ar.com.hmu.factory;
