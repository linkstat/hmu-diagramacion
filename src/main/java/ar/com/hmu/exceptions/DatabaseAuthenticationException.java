package ar.com.hmu.exceptions;

public class DatabaseAuthenticationException extends RuntimeException {

    private final DatabaseErrorType errorType;

    public DatabaseAuthenticationException(String message, Throwable cause, DatabaseErrorType errorType) {
        super(message, cause);
        this.errorType = errorType;
    }

    public DatabaseErrorType getErrorCode() {
        return errorType;
    }

}