package com.asendus.api;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalTime;

/** A break-duty Excel like the school's "alates …" sheet (columns A–I), built in memory with made-up names. */
public final class TestDutySheet {
    private final Workbook wb = new XSSFWorkbook();
    private final Sheet sheet = wb.createSheet("alates 7.09");
    private final CellStyle timeStyle = wb.createCellStyle();
    private int next = 0;

    public TestDutySheet() {
        this(false);
    }

    /** amPm: show the time cells as "1:00 PM" like an English-locale Excel would. */
    public TestDutySheet(boolean amPm) {
        timeStyle.setDataFormat(wb.createDataFormat().getFormat(amPm ? "h:mm AM/PM" : "h:mm"));
        cells(sheet.createRow(next++), "", "SÖÖGI-JA ÕUEVAHETUNNID");
        next++; // empty row, like the real sheet
    }

    /** A day header: A = E/T/K/N/R, C/D/E = ÕVT/SVT/ÕVT. */
    public TestDutySheet day(String letter) {
        cells(sheet.createRow(next++), letter, "", "ÕVT", "SVT", "ÕVT", "1.1 ja 1.2 klassid", "pos", "1.3 ja 1.4 klassid", "pos");
        return this;
    }

    /**
     * A class-group row. c = ÕVT time, canteen = SVT start (null for none), e = ÕVT range or note,
     * f/g and h/i = names and position (null position for none).
     */
    public TestDutySheet group(String b, String c, LocalTime canteen, String e,
                               String f, Integer g, String h, Integer i) {
        Row row = sheet.createRow(next++);
        cells(row, "", b, c);
        if (canteen != null) {
            Cell d = row.createCell(3);
            d.setCellValue(canteen.toSecondOfDay() / 86400.0);
            d.setCellStyle(timeStyle);
        }
        row.createCell(4).setCellValue(e);
        row.createCell(5).setCellValue(f);
        if (g != null) row.createCell(6).setCellValue(g);
        row.createCell(7).setCellValue(h);
        if (i != null) row.createCell(8).setCellValue(i);
        row.createCell(14).setCellValue("1.1 217 märkus"); // side notes far right are ignored
        return this;
    }

    /** An empty row ends a day block. */
    public TestDutySheet blank() {
        next++;
        return this;
    }

    public byte[] bytes() {
        try (wb; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void cells(Row row, String... values) {
        for (int c = 0; c < values.length; c++) row.createCell(c).setCellValue(values[c]);
    }
}
