package com.asendus.api.timetable;

import com.asendus.api.timetable.ParsedTimetable.ParsedLesson;
import com.asendus.api.timetable.ParsedTimetable.ParsedTeacher;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the aSc "õpetajad" PDF export: one page per teacher, 5 day rows × 7 period columns.
 * Layout measurements behind the constants: docs/superpowers/notes/2026-10-14-pdf-layout.md.
 */
@Component
public class TimetablePdfParser {

    private static final String TITLE_PREFIX = "Õpetaja ";
    private static final List<String> DAY_LABELS = List.of("Es", "Te", "Ko", "Ne", "Re");

    // Font sizes (notes §1): header digits 19pt, day labels 43pt, labels and notes 10pt, class text 28pt.
    private static final float HEADER_DIGIT_MIN_SIZE = 15f;
    private static final float HEADER_DIGIT_MAX_SIZE = 25f;
    private static final float DAY_LABEL_MIN_SIZE = 35f;
    private static final float LABEL_SIZE = 10f;
    private static final float CLASS_TEXT_SIZE = 28f;
    private static final float SIZE_TOLERANCE = 1f;

    /** A double lesson's class text is centered on the column boundary within 0.3pt; single cells are 52pt off (notes §4). */
    private static final float MERGED_CELL_TOLERANCE = 3f;
    /** Notes are centered in their cell within 0.3pt; subject and room labels sit at the edges (notes §3). */
    private static final float CENTERED_TOLERANCE = 2f;
    /** Subject and room of one label line share a baseline. */
    private static final float SAME_LINE_TOLERANCE = 1f;

    public static class NotATimetableException extends RuntimeException {
        public NotATimetableException(String message) {
            super(message);
        }
    }

    public ParsedTimetable parse(byte[] pdf) {
        List<ParsedTeacher> teachers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> keys = new HashSet<>();

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            for (int page = 1; page <= doc.getNumberOfPages(); page++) {
                Optional<ParsedTeacher> parsed = parsePage(words(doc, page));
                if (parsed.isEmpty()) {
                    warnings.add("Lk " + page + ": ei tundu õpetaja tunniplaanina, jäeti vahele");
                    continue;
                }
                ParsedTeacher teacher = parsed.get();
                if (!keys.add(TeacherKey.of(teacher.name()))) {
                    String renamed = teacher.name() + " (2)";
                    warnings.add("Lk " + page + ": õpetaja \"" + teacher.name() + "\" on topelt, nimetatud \"" + renamed + "\"");
                    keys.add(TeacherKey.of(renamed));
                    teacher = new ParsedTeacher(renamed, teacher.lessons());
                }
                teachers.add(teacher);
            }
        } catch (IOException e) {
            throw new NotATimetableException("Faili ei õnnestunud PDF-ina lugeda");
        }

        if (teachers.isEmpty()) {
            throw new NotATimetableException("See ei tundu olevat aSc õpetajate tunniplaan");
        }
        return new ParsedTimetable(teachers, warnings);
    }

    /** One word per drawn text run; the spike found no run that spans two cells (notes §2). */
    private static List<PdfWord> words(PDDocument doc, int page) throws IOException {
        List<PdfWord> words = new ArrayList<>();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> glyphs) {
                toWord(glyphs).ifPresent(words::add);
            }
        };
        stripper.setSortByPosition(true);
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        stripper.getText(doc);
        return words;
    }

    /** Drops space glyphs with zero advance, an export artifact ("Karamõ kin" is really "Karamõkin", notes §2). */
    private static Optional<PdfWord> toWord(List<TextPosition> glyphs) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < glyphs.size(); i++) {
            TextPosition g = glyphs.get(i);
            boolean zeroAdvanceSpace = g.getUnicode().isBlank() && i + 1 < glyphs.size()
                    && Math.abs(glyphs.get(i + 1).getXDirAdj() - g.getXDirAdj()) < 0.5f;
            if (!zeroAdvanceSpace) text.append(g.getUnicode());
        }
        if (text.toString().isBlank()) return Optional.empty();
        TextPosition first = glyphs.getFirst();
        TextPosition last = glyphs.getLast();
        return Optional.of(new PdfWord(text.toString().trim(), first.getXDirAdj(), last.getXDirAdj() + last.getWidthDirAdj(),
                first.getYDirAdj(), first.getFontSizeInPt()));
    }

    private Optional<ParsedTeacher> parsePage(List<PdfWord> words) {
        Optional<String> name = teacherName(words);
        Optional<Grid> grid = grid(words);
        if (name.isEmpty() || grid.isEmpty()) return Optional.empty();

        List<PdfWord> cellWords = words.stream()
                .filter(w -> isLabel(w) || isClassText(w))
                .filter(grid.get()::contains)
                .toList();
        List<ParsedLesson> lessons = new ArrayList<>();
        for (int day = 1; day <= Grid.DAYS; day++) {
            int d = day;
            List<PdfWord> row = cellWords.stream().filter(w -> grid.get().day(w) == d).toList();
            for (Cell cell : cells(row, grid.get())) {
                lessons.addAll(lessons(cell, row.stream().filter(cell::covers).toList(), day));
            }
        }
        lessons.sort(Comparator.comparingInt(ParsedLesson::weekday).thenComparingInt(ParsedLesson::period));
        return Optional.of(new ParsedTeacher(name.get(), lessons));
    }

    private static Optional<String> teacherName(List<PdfWord> words) {
        return words.stream()
                .filter(w -> w.text().startsWith(TITLE_PREFIX))
                .max(Comparator.comparing(PdfWord::size))
                .map(w -> w.text().substring(TITLE_PREFIX.length()).trim());
    }

    /** Needs all seven header digits and all five day labels; anything else is not a teacher page. */
    private static Optional<Grid> grid(List<PdfWord> words) {
        float[] colCenters = new float[Grid.PERIODS];
        float[] dayBaselines = new float[Grid.DAYS];
        for (PdfWord w : words) {
            boolean digitSize = w.size() >= HEADER_DIGIT_MIN_SIZE && w.size() <= HEADER_DIGIT_MAX_SIZE;
            if (digitSize && w.text().matches("[1-7]")) {
                int p = Integer.parseInt(w.text()) - 1;
                if (colCenters[p] == 0) colCenters[p] = w.cx();
            }
            int d = DAY_LABELS.indexOf(w.text());
            if (d >= 0 && w.size() >= DAY_LABEL_MIN_SIZE && dayBaselines[d] == 0) dayBaselines[d] = w.y();
        }
        for (float c : colCenters) if (c == 0) return Optional.empty();
        for (float y : dayBaselines) if (y == 0) return Optional.empty();
        return Optional.of(new Grid(colCenters, dayBaselines));
    }

    private static boolean isLabel(PdfWord w) {
        return Math.abs(w.size() - LABEL_SIZE) <= SIZE_TOLERANCE;
    }

    private static boolean isClassText(PdfWord w) {
        return Math.abs(w.size() - CLASS_TEXT_SIZE) <= SIZE_TOLERANCE;
    }

    /** A cell spans one period, or two when a double lesson's class text is centered on the boundary between them. */
    private record Cell(int firstPeriod, int lastPeriod, float left, float right) {
        float center() {
            return (left + right) / 2;
        }

        boolean covers(PdfWord w) {
            return w.cx() >= left && w.cx() < right;
        }
    }

    /** Finds merged cells first so that a double lesson's labels (subject in p, room in p+1) stay together. */
    private static List<Cell> cells(List<PdfWord> row, Grid grid) {
        List<Cell> cells = new ArrayList<>();
        int p = 1;
        while (p <= Grid.PERIODS) {
            int last = p < Grid.PERIODS && isCenteredOnBoundary(row, grid.colRight(p)) ? p + 1 : p;
            cells.add(new Cell(p, last, grid.colLeft(p), grid.colRight(last)));
            p = last + 1;
        }
        return cells;
    }

    private static boolean isCenteredOnBoundary(List<PdfWord> row, float boundary) {
        return row.stream().anyMatch(w -> isClassText(w) && Math.abs(w.cx() - boundary) < MERGED_CELL_TOLERANCE);
    }

    /**
     * Each label line (subject and/or room at one y) starts a lesson that owns the class lines and notes below it,
     * down to the next label line. A cell without labels is one lesson.
     */
    private static List<ParsedLesson> lessons(Cell cell, List<PdfWord> words, int day) {
        if (words.isEmpty()) return List.of();
        List<PdfWord> classLines = words.stream().filter(TimetablePdfParser::isClassText).toList();
        List<PdfWord> notes = words.stream().filter(w -> isLabel(w) && isCentered(w, cell)).toList();
        List<PdfWord> labels = words.stream().filter(w -> isLabel(w) && !isCentered(w, cell)).toList();
        List<List<PdfWord>> labelLines = groupByLine(labels);
        if (labelLines.isEmpty()) labelLines = List.of(List.of());

        List<ParsedLesson> out = new ArrayList<>();
        for (int i = 0; i < labelLines.size(); i++) {
            float from = i == 0 ? Float.NEGATIVE_INFINITY : labelLines.get(i).getFirst().y();
            float to = i + 1 < labelLines.size() ? labelLines.get(i + 1).getFirst().y() : Float.POSITIVE_INFINITY;
            List<PdfWord> line = labelLines.get(i);
            String classes = joinClasses(textsBetween(classLines, from, to));
            String subject = String.join(" ", line.stream().filter(w -> w.cx() < cell.center()).map(PdfWord::text).toList());
            String room = String.join(" ", line.stream().filter(w -> w.cx() >= cell.center()).map(PdfWord::text).toList());
            String note = String.join(" ", textsBetween(notes, from, to));
            for (int period = cell.firstPeriod(); period <= cell.lastPeriod(); period++) {
                out.add(new ParsedLesson(day, period, classes, subject, room, note));
            }
        }
        return out;
    }

    private static boolean isCentered(PdfWord w, Cell cell) {
        return Math.abs(w.cx() - cell.center()) < CENTERED_TOLERANCE;
    }

    private static List<List<PdfWord>> groupByLine(List<PdfWord> labels) {
        List<List<PdfWord>> lines = new ArrayList<>();
        for (PdfWord w : labels.stream().sorted(Comparator.comparing(PdfWord::y).thenComparing(PdfWord::x0)).toList()) {
            if (lines.isEmpty() || w.y() - lines.getLast().getFirst().y() > SAME_LINE_TOLERANCE) lines.add(new ArrayList<>());
            lines.getLast().add(w);
        }
        return lines;
    }

    private static List<String> textsBetween(List<PdfWord> words, float from, float to) {
        return words.stream()
                .filter(w -> w.y() >= from && w.y() < to)
                .sorted(Comparator.comparing(PdfWord::y).thenComparing(PdfWord::x0))
                .map(PdfWord::text)
                .toList();
    }

    /** "2.3/2.4", "2.5/2.3V", "2.5V" → "2.3/2.4/2.5/2.3V/2.5V"; "2.3/2.4/" + "2.5" → "2.3/2.4/2.5". */
    static String joinClasses(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '/') sb.append('/');
            sb.append(line.replace(" ", ""));
        }
        return sb.toString();
    }
}
