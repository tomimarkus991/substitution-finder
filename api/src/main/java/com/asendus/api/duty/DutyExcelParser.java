package com.asendus.api.duty;

import com.asendus.api.duty.ParsedDuties.ParsedDuty;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the break duties (ÕVT outdoor, SVT canteen) from the first sheet of the school's
 * "SVT, ÕVT, muud graafikud" Excel. Anything it can't read safely becomes a warning, never a guess.
 */
@Component
public class DutyExcelParser {
    public static final int CANTEEN_MINUTES = 45;
    private static final List<String> DAYS = List.of("E", "T", "K", "N", "R");
    private static final int A = 0, B = 1, C = 2, D = 3, E = 4;
    private static final int[][] HALVES = {{5, 6}, {7, 8}}; // names, position: F/G and H/I
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})");

    public static class NotADutySheetException extends RuntimeException {
        public NotADutySheetException() {
            super("See ei tundu olevat vahetunni valvete tabel");
        }
    }

    public ParsedDuties parse(byte[] xlsx) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            if (wb.getNumberOfSheets() == 0) throw new NotADutySheetException();
            return parse(wb.getSheetAt(0));
        } catch (IOException | EncryptedDocumentException | POIXMLException e) {
            throw new NotADutySheetException();
        }
    }

    private ParsedDuties parse(Sheet sheet) {
        DataFormatter fmt = new DataFormatter();
        List<ParsedDuty> duties = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean sawHeader = false;
        int weekday = 0;
        String day = "";
        for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) { weekday = 0; continue; }
            String a = text(fmt, row, A);
            if (DAYS.contains(a) && text(fmt, row, C).equals("ÕVT")) {
                weekday = DAYS.indexOf(a) + 1;
                day = a;
                sawHeader = true;
                continue;
            }
            if (weekday == 0) continue;
            String group = text(fmt, row, B);
            if (group.isEmpty()) { weekday = 0; continue; } // block ends
            for (int[] half : HALVES) {
                readHalf(fmt, row, half, weekday, day + ", " + group, group, duties, warnings);
            }
        }
        if (!sawHeader) throw new NotADutySheetException();
        return new ParsedDuties(duties, warnings);
    }

    private void readHalf(DataFormatter fmt, Row row, int[] half, int weekday, String where, String group,
                          List<ParsedDuty> duties, List<String> warnings) {
        String names = text(fmt, row, half[0]);
        if (names.isEmpty()) return;
        String at = where + ": „" + names + "“";

        String kind;
        LocalTime start, end;
        int period;
        try {
            if (DutyNames.isCanteen(names)) {
                Optional<LocalTime> t = firstTime(text(fmt, row, D));
                if (t.isEmpty()) { warnings.add(at + " – söögivahetunni kellaaeg puudub"); return; }
                kind = "SVT";
                start = t.get();
                end = start.plusMinutes(CANTEEN_MINUTES);
            } else {
                String e = DutyNames.lower(text(fmt, row, E));
                Optional<LocalTime[]> range = e.startsWith("klj") || e.startsWith("ujumas") ? Optional.empty() : range(e);
                if (range.isEmpty()) range = range(text(fmt, row, C));
                if (range.isEmpty()) { warnings.add(at + " – õuevahetunni kellaaeg puudub"); return; }
                kind = "OVT";
                start = range.get()[0];
                end = range.get()[1];
            }
            period = PeriodTimes.periodOf(start, end);
            if (period == 0) { warnings.add(at + " – kellaaeg jääb tundidest välja"); return; }
        } catch (DateTimeException ex) {
            warnings.add(at + " – vigane kellaaeg");
            return;
        }
        if (DutyNames.hidesDuty(names)) warnings.add(at + " – siin võib olla veel üks valve, kontrolli käsitsi");

        String position = text(fmt, row, half[1]);
        for (String surname : DutyNames.surnames(names)) {
            if (surname.equalsIgnoreCase("klassijuhataja")) {
                warnings.add(at + " – klassijuhatajat ei saa nime järgi leida");
            } else {
                duties.add(new ParsedDuty(weekday, period, kind, start, end, group, position, surname));
            }
        }
    }

    private static Optional<LocalTime[]> range(String s) {
        Matcher m = DutyNames.RANGE.matcher(s);
        if (!m.find()) return Optional.empty();
        return Optional.of(new LocalTime[]{time(m.group(1), m.group(2)), time(m.group(3), m.group(4))});
    }

    private static Optional<LocalTime> firstTime(String s) {
        Matcher m = TIME.matcher(s);
        return m.find() ? Optional.of(time(m.group(1), m.group(2))) : Optional.empty();
    }

    private static LocalTime time(String h, String m) {
        return LocalTime.of(Integer.parseInt(h), Integer.parseInt(m));
    }

    /** The cell as shown in Excel (times as "10:25"), NFC, trimmed; "" when missing. */
    private static String text(DataFormatter fmt, Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalTime t = cell.getLocalDateTimeCellValue().toLocalTime(); // not the cell's own format (AM/PM)
            return t.getHour() + ":" + String.format("%02d", t.getMinute());
        }
        return Normalizer.normalize(fmt.formatCellValue(cell), Normalizer.Form.NFC).replace(' ', ' ').trim();
    }
}
