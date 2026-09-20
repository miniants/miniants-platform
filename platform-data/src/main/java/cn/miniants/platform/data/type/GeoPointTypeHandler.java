package cn.miniants.platform.data.type;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.springframework.data.geo.Point;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes({Point.class})
public class GeoPointTypeHandler extends BaseTypeHandler<Point> {

    private final GeoPointConverter converter = new GeoPointConverter();

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, Point point, JdbcType jdbcType)
            throws SQLException {
        statement.setBytes(index, converter.to(point));
    }

    @Override
    public Point getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return converter.from(resultSet.getBytes(columnName));
    }

    @Override
    public Point getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return converter.from(resultSet.getBytes(columnIndex));
    }

    @Override
    public Point getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return converter.from(statement.getBytes(columnIndex));
    }
}
