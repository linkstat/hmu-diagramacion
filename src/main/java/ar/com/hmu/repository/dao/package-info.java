/**
 * Paquete DAO (Data Access Object): gestiona la interacción directa con la base de datos.
 * <p>
 * Este subpaquete contiene la clase `GenericDAO&lt;T&gt;`, que implementa un patrón DAO genérico,
 * encargado de gestionar operaciones comunes de acceso a la base de datos, como realizar
 * consultas, insertar, actualizar o eliminar registros. Al ser genérico, `GenericDAO&lt;T&gt;`
 * permite reutilizar código y aplicar las operaciones CRUD (Create, Read, Update, Delete) a
 * diferentes entidades, minimizando la duplicación de lógica de acceso a datos y facilitando
 * la escalabilidad del proyecto.
 * <p>
 * La clase `GenericDAO&lt;T&gt;` define métodos básicos que pueden ser extendidos por clases DAO
 * específicas, como `UsuarioDAO`, proporcionando la funcionalidad común para interactuar con
 * las tablas de la base de datos. Esto permite a las clases hijas enfocarse en sus propias
 * particularidades y lógicas específicas, mientras heredan la funcionalidad común.
 * <p>
 * A diferencia del paquete `repository`, este subpaquete se centra exclusivamente en la
 * ejecución de consultas y la gestión de transacciones de la base de datos. Las clases
 * en `repository.dao` actúan como una interfaz directa entre la aplicación y la base de
 * datos, pero no contienen lógica de negocio más allá de las consultas y operaciones de
 * persistencia.
 * <p>
 * Los DAO son utilizados por los repositorios del paquete superior (`repository`),
 * que proporcionan un mayor nivel de abstracción y gestionan la lógica de negocio para la
 * aplicación. De esta manera, el acceso a los datos se mantiene bien organizado y fácilmente
 * mantenible, siguiendo los principios de responsabilidad única y separación de preocupaciones.
 */
package ar.com.hmu.repository.dao;

