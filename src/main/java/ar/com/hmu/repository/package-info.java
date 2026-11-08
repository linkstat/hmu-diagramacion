/**
 * Paquete Repository: encargado de gestionar la persistencia de datos de alto nivel.
 * <p>
 * Este paquete implementa el patrón de repositorio, proporcionando un nivel de abstracción
 * para la interacción con la capa de persistencia. Contiene las clases que manejan las
 * operaciones relacionadas con la persistencia de datos desde una perspectiva más general
 * o de "servicio", coordinando el acceso a los datos y gestionando su flujo entre la
 * aplicación y la base de datos.
 * <p>
 * Los repositorios de este paquete, como {@code UsuarioRepository}, no solo realizan
 * operaciones CRUD (crear, leer, actualizar, eliminar), sino que también incluyen
 * lógica adicional necesaria para cumplir con los requerimientos de negocio, combinando
 * información de diversas fuentes o aplicando filtros más complejos a las consultas.
 * <p>
 * Además, las clases del paquete `repository` interactúan con la capa DAO contenida en
 * el subpaquete `repository.dao`, delegando la ejecución de las consultas específicas a
 * la base de datos a dicho subpaquete. De este modo, se desacopla la lógica de negocio
 * del acceso directo a los datos, promoviendo la reutilización y la separación de
 * responsabilidades.
 */
package ar.com.hmu.repository;
