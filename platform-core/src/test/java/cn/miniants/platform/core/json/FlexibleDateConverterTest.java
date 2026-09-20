package cn.miniants.platform.core.json;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlexibleDateConverterTest {

    @Test
    void parsesChineseDateFormats() {
        Date dateTime = FlexibleDateConverter.INSTANCE.convert("2026-08-25 12:16:30");
        Date date = FlexibleDateConverter.INSTANCE.convert("2026-08-25");

        LocalDate expected = LocalDate.of(2026, 8, 25);
        assertEquals(expected, dateTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        assertEquals(expected, date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        assertEquals(LocalDateTime.of(2026, 8, 25, 12, 16, 30),
                FlexibleLocalDateTimeConverter.INSTANCE.convert("2026-08-25 12:16:30"));
        assertEquals(LocalDateTime.of(2026, 8, 25, 12, 16, 0),
                FlexibleLocalDateTimeConverter.INSTANCE.convert("2026-08-25 12:16"));
    }

    @Test
    void blankBecomesNull() {
        assertNull(FlexibleDateConverter.INSTANCE.convert(""));
        assertNull(FlexibleDateConverter.INSTANCE.convert(null));
    }

    @Test
    void rejectsUnknownPattern() {
        assertThrows(IllegalArgumentException.class, () -> FlexibleDateConverter.INSTANCE.convert("25/08/2026"));
    }
}
