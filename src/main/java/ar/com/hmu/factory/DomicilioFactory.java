package ar.com.hmu.factory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import ar.com.hmu.model.Domicilio;

public class DomicilioFactory {

    public static Domicilio createDomicilio(ResultSet rs) throws SQLException {
        return new Domicilio.Builder()
                .id(rs.getObject("id", UUID.class))
                .calle(rs.getString("calle"))
                .numeracion(rs.getInt("numeracion"))
                .barrio(rs.getString("barrio"))
                .ciudad(rs.getString("ciudad"))
                .localidad(rs.getString("localidad"))
                .provincia(rs.getString("provincia"))
                .build();
    }

}
