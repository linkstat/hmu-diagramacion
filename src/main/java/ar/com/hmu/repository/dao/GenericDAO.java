package ar.com.hmu.repository.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public interface GenericDAO<T> {

    void create(T entity) throws SQLException;
    T readByUUID(UUID id) throws SQLException;
    List<T> readAll() throws SQLException;
    void update(T entity) throws SQLException;
    void delete(T entity) throws SQLException;

}
