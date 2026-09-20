package cn.miniants.platform.data.type;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.jts.io.ByteOrderValues;
import org.locationtech.jts.io.InputStreamInStream;
import org.locationtech.jts.io.OutputStreamOutStream;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * MySQL {@code POINT} 列（SRID + WKB）与 {@link org.springframework.data.geo.Point} 互转。
 *
 * <p>注意 MySQL 的 POINT 存的是 (经度, 纬度)，Spring 的 Point 是 (纬度, 经度)，两边 x/y 对调。
 */
public class GeoPointConverter {

    private static final Logger log = LoggerFactory.getLogger(GeoPointConverter.class);

    private static final int BYTE_ORDER = ByteOrderValues.LITTLE_ENDIAN;
    private static final int OUTPUT_DIMENSION = 2;
    private static final int SRID_BYTES = 4;

    private final PrecisionModel precisionModel = new PrecisionModel();

    public org.springframework.data.geo.Point from(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            byte[] sridBytes = inputStream.readNBytes(SRID_BYTES);
            int srid = ByteOrderValues.getInt(sridBytes, BYTE_ORDER);
            WKBReader wkbReader = new WKBReader(new GeometryFactory(precisionModel, srid));
            Geometry geometry = wkbReader.read(new InputStreamInStream(inputStream));
            Point point = (Point) geometry;
            return new org.springframework.data.geo.Point(point.getY(), point.getX());
        } catch (IOException | ParseException e) {
            log.error("地理位置转换错误", e);
            throw new IllegalArgumentException(e);
        }
    }

    public byte[] to(org.springframework.data.geo.Point geoPoint) {
        if (geoPoint == null) {
            return null;
        }
        Coordinate coordinate = new Coordinate(geoPoint.getY(), geoPoint.getX());
        Point point = new Point(new CoordinateArraySequence(new Coordinate[]{coordinate}, OUTPUT_DIMENSION),
                new GeometryFactory());
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] sridBytes = new byte[SRID_BYTES];
            ByteOrderValues.putInt(point.getSRID(), sridBytes, BYTE_ORDER);
            outputStream.write(sridBytes);
            new WKBWriter(OUTPUT_DIMENSION, BYTE_ORDER).write(point, new OutputStreamOutStream(outputStream));
            return outputStream.toByteArray();
        } catch (IOException e) {
            log.error("地理位置转换错误", e);
            throw new IllegalArgumentException(e);
        }
    }
}
