/**
 * Aromito - Sistema de Gestión de Novedades para el Hospital de Urgencias de Córdoba
 * <p>
 * Esta aplicación tiene como propósito principal facilitar la gestión de novedades de RRHH (como faltas justificadas,
 * injustificadas o por fuerza mayor, omisiones de marcación, cambios de horario y/o de guardia, etc), dentro del ámbito
 * del Hospital de Urgencias de Córdoba.
 * <p>
 * La arquitectura se basa en un enfoque modular, utilizando prácticas modernas de desarrollo como:
 * <ul>
 *     <li>Patrones de diseño (DAO, Factory, etc.) para la organización eficiente del código.</li>
 *     <li>JavaFX para proporcionar una interfaz gráfica amigable para el usuario.</li>
 *     <li>Un enfoque en capas que separa la lógica de negocio, la presentación, y el acceso a datos.</li>
 * </ul>
 * El proyecto está organizado en diferentes paquetes que representan distintas responsabilidades, de los cuales,
 * por mencionar algunos tenemos a:
 * <ul>
 *     <li>{@link ar.com.hmu.controller}: Controladores para gestionar la interacción entre la vista y la lógica de negocio.</li>
 *     <li>{@link ar.com.hmu.service}: Lógica de negocio encapsulada en servicios.</li>
 *     <li>{@link ar.com.hmu.repository}: Repositorios para la persistencia de datos y el manejo de entidades.</li>
 *     <li>{@link ar.com.hmu.ui}: Componentes de la interfaz gráfica desarrollados con JavaFX.</li>
 * </ul>
 * <p>
 * La aplicación está diseñada teniendo en mente la escalabilidad y facilidad de mantenimiento, asegurando
 * que las funciones administrativas puedan ser extendidas según las necesidades del usuario.
 */
package ar.com.hmu;
