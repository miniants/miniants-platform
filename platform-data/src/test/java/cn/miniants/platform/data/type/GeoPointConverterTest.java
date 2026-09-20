package cn.miniants.platform.data.type;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.io.WKBReader;
import org.springframework.data.geo.Point;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GeoPointConverterTest {

    private final GeoPointConverter converter = new GeoPointConverter();

    /**
     * MySQL POINT 存 (经度, 纬度)，Spring Point 是 (纬度, 经度)，两边 x/y 对调。
     * 只调一边就会把北京写成南极圈外，而且往返测不出来——所以用非对称坐标。
     */
    @Test
    void roundTripKeepsLatitudeAndLongitudeOnTheSameSide() {
        Point original = new Point(39.98, 116.34);

        Point restored = converter.from(converter.to(original));

        assertEquals(39.98, restored.getX(), 1e-9);
        assertEquals(116.34, restored.getY(), 1e-9);
    }

    @Test
    void wkbDecodesToTheCoordinatesMysqlWouldRead() throws Exception {
        byte[] stored = converter.to(new Point(39.98, 116.34));

        // 前 4 字节是 SRID，WKBReader 只认后面的 WKB
        org.locationtech.jts.geom.Point wkb = (org.locationtech.jts.geom.Point) new WKBReader()
                .read(Arrays.copyOfRange(stored, 4, stored.length));

        assertEquals(116.34, wkb.getX(), 1e-9);
        assertEquals(39.98, wkb.getY(), 1e-9);
    }

    @Test
    void nullPassesThroughInsteadOfBlowingUp() {
        assertNull(converter.to(null));
        assertNull(converter.from(null));
    }
}
