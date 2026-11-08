package ar.com.hmu.exceptions;

/** Error de negocio: el mensaje es el que ve el usuario. */
public class ServiceException extends Exception {

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }

}
