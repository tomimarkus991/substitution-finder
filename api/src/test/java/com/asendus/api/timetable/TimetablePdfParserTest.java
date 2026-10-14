package com.asendus.api.timetable;

import com.asendus.api.TestPdfs;
import com.asendus.api.timetable.ParsedTimetable.ParsedLesson;
import com.asendus.api.timetable.ParsedTimetable.ParsedTeacher;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class TimetablePdfParserTest {

    static ParsedTimetable b;

    @BeforeAll
    static void parse() {
        b = new TimetablePdfParser().parse(TestPdfs.load("opetajad-b.pdf"));
    }

    static ParsedTeacher teacher(ParsedTimetable t, String name) {
        return t.teachers().stream().filter(x -> x.name().equals(name)).findFirst().orElseThrow();
    }

    static List<ParsedLesson> at(String name, int weekday, int period) {
        return at(b, name, weekday, period);
    }

    static List<ParsedLesson> at(ParsedTimetable t, String name, int weekday, int period) {
        return teacher(t, name).lessons().stream().filter(l -> l.weekday() == weekday && l.period() == period).toList();
    }

    @Test
    void readsEveryTeacherPage() {
        assertThat(b.teachers()).hasSize(46);
        assertThat(b.teachers().getFirst().name()).isEqualTo("Bondar Julia");
        assertThat(b.warnings()).isEmpty();
    }

    @Test
    void readsASimpleCell() {
        // Pärnpuu Taimi, Monday period 1: Ek, room 221, class 4R
        assertThat(at("Pärnpuu Taimi", 1, 1)).containsExactly(new ParsedLesson(1, 1, "4R", "Ek", "221", ""));
        // Friday period 4: Aj 6R
        assertThat(at("Pärnpuu Taimi", 5, 4)).extracting(ParsedLesson::subject, ParsedLesson::classes)
                .containsExactly(tuple("Aj", "6R"));
        assertThat(at("Pärnpuu Taimi", 1, 5)).isEmpty();
    }

    @Test
    void readsGroupNotesAndMultiClassText() {
        assertThat(at("Pärnpuu Taimi", 1, 3)).singleElement().satisfies(l -> {
            assertThat(l.classes()).isEqualTo("4R/4M/4S");
            assertThat(l.note()).isEqualTo("Grupp 1/grupp 1/grupp 1");
        });
        // three stacked lines in one cell
        assertThat(at("Moora Piret", 3, 1)).singleElement()
                .extracting(ParsedLesson::classes).isEqualTo("2.3/2.4/2.5/2.3V/2.5V");
    }

    @Test
    void splitsADoubleLessonIntoTwoPeriods() {
        // Pupart Krista, Tuesday: "5R" spans periods 1–2, TÕ room 017
        assertThat(at("Pupart Krista", 2, 1)).extracting(ParsedLesson::classes, ParsedLesson::subject, ParsedLesson::room)
                .containsExactly(tuple("5R", "TÕ", "017"));
        assertThat(at("Pupart Krista", 2, 2)).extracting(ParsedLesson::classes).containsExactly("5R");
        assertThat(at("Pupart Krista", 2, 3)).extracting(ParsedLesson::classes).containsExactly("5D");
        // Thursday 6–7 "5M/5MV"
        assertThat(at("Pupart Krista", 4, 7)).extracting(ParsedLesson::classes).containsExactly("5M/5MV");
    }

    @Test
    void givesBothPeriodsOfADoubleLessonTheSameLabels() {
        // The subject sits in period 1's column and the room in period 2's; both periods need both.
        assertThat(at("Pupart Krista", 2, 1)).containsExactly(new ParsedLesson(2, 1, "5R", "TÕ", "017", ""));
        assertThat(at("Pupart Krista", 2, 2)).containsExactly(new ParsedLesson(2, 2, "5R", "TÕ", "017", ""));
    }

    @Test
    void splitsASplitCellIntoTwoLessons() {
        // Ambos Algi, Thursday period 5: 6M (219) and 6R (217)
        assertThat(at("Ambos Algi", 4, 5)).extracting(ParsedLesson::classes, ParsedLesson::subject, ParsedLesson::room)
                .containsExactlyInAnyOrder(tuple("6M", "Ettevõtlus/digi", "219"), tuple("6R", "Ettevõtlus/digi", "217"));
    }

    @Test
    void keepsASplitHalfWithASubjectButNoRoom() {
        assertThat(at("Randmaa Janell", 1, 6)).containsExactly(
                new ParsedLesson(1, 6, "2.1", "Digikirjaoskus", "130", "grupp 1"),
                new ParsedLesson(1, 6, "2.2", "Digikirjaoskus", "", "grupp 2"));
    }

    @Test
    void keepsWholeFileTotalsStable() {
        assertThat(b.teachers()).flatExtracting(ParsedTeacher::lessons).hasSize(810)
                .filteredOn(l -> l.classes().isEmpty()).hasSize(1);
        ParsedTimetable t = new TimetablePdfParser().parse(TestPdfs.load("opetajad-0304.pdf"));
        assertThat(t.teachers()).flatExtracting(ParsedTeacher::lessons).hasSize(266);
    }

    @Test
    void splitsAMondaySplitCellWithANoteBetweenItsHalves() {
        assertThat(at("Loo Ingrid", 1, 1)).containsExactly(
                new ParsedLesson(1, 1, "6S", "KKt", "Võimla 1", "Tüdrukud"),
                new ParsedLesson(1, 1, "3.1", "ujumine", "21.Kooli ujula", ""));
    }

    @Test
    void keepsEmptyAndNoteOnlyTeachers() {
        assertThat(teacher(b, "Malken Mariliis").lessons()).isEmpty();
        assertThat(at("Reinaru Tiia", 1, 2)).singleElement().satisfies(l -> {
            assertThat(l.classes()).isEmpty();
            assertThat(l.note()).isEqualTo("3.kl ek õa");
            assertThat(l.room()).isEqualTo("403");
        });
    }

    @Test
    void readsMultiWordRooms() {
        assertThat(at("Illak Pille- Riin", 1, 1)).extracting(ParsedLesson::room).containsExactly("Mäe 212");
    }

    @Test
    void ignoresThePageFooter() {
        assertThat(b.teachers()).flatExtracting(ParsedTeacher::lessons)
                .noneMatch(l -> l.note().contains("aSc Tunniplaan"));
        assertThat(at("Kitsing Mari", 5, 7)).isEmpty();
        assertThat(teacher(b, "Kitsing Mari").lessons()).noneMatch(l -> l.weekday() == 5);
    }

    @Test
    void dropsZeroWidthSpacesFromTheTitle() {
        assertThat(b.teachers()).extracting(ParsedTeacher::name)
                .contains("Karamõkin Deniss", "Illak Pille- Riin")
                .doesNotContain("Karamõ kin Deniss");
    }

    @Test
    void parsesTheTwoDaySample() {
        ParsedTimetable t = new TimetablePdfParser().parse(TestPdfs.load("opetajad-0304.pdf"));
        assertThat(t.teachers()).hasSize(45);
        assertThat(t.warnings()).isEmpty();
        // Ambos Algi, Friday period 3
        assertThat(at(t, "Ambos Algi", 5, 3)).containsExactly(new ParsedLesson(5, 3, "5M/5MV", "Ettevõtlusõpe", "217", ""));
    }

    @Test
    void renamesDuplicateTeachersAndSkipsForeignPages() throws Exception {
        byte[] pdf = samplePages(doc -> {}, 0, 0, 0, -1, 1);
        ParsedTimetable t = new TimetablePdfParser().parse(pdf);
        assertThat(t.teachers()).extracting(ParsedTeacher::name)
                .containsExactly("Bondar Julia", "Bondar Julia (2)", "Bondar Julia (3)", "Karamõkin Deniss");
        assertThat(t.warnings()).containsExactly(
                "Lk 2: õpetaja \"Bondar Julia\" on topelt, nimetatud \"Bondar Julia (2)\"",
                "Lk 3: õpetaja \"Bondar Julia\" on topelt, nimetatud \"Bondar Julia (3)\"",
                "Lk 4: ei tundu õpetaja tunniplaanina, jäeti vahele");
    }

    @Test
    void warnsAboutTextItCannotPlaceAndReadsOddSizedClassText() throws Exception {
        // Malken Mariliis (page 22) has an empty grid; draw into it.
        byte[] pdf = samplePages(doc -> {
            PDPage page = doc.getPage(0);
            // Prepend: the aSc content leaves a scaled transform behind, which would move appended text.
            try (var cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.PREPEND, true)) {
                var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                float height = page.getMediaBox().getHeight();
                drawText(cs, font, 15, "Y", 350, height - 161);   // Monday period 3, an unknown size
                drawText(cs, font, 22, "7A", 455, height - 253);  // Tuesday period 4, larger than labels
            }
        }, 21);
        ParsedTimetable t = new TimetablePdfParser().parse(pdf);
        assertThat(t.warnings()).containsExactly("Lk 1: tekst \"Y\" jäi paigutamata");
        assertThat(teacher(t, "Malken Mariliis").lessons()).containsExactly(new ParsedLesson(2, 4, "7A", "", "", ""));
    }

    @Test
    void rejectsBytesThatAreNotAPdf() {
        assertThatThrownBy(() -> new TimetablePdfParser().parse("nope".getBytes()))
                .isInstanceOf(TimetablePdfParser.NotATimetableException.class)
                .hasMessage("Faili ei õnnestunud PDF-ina lugeda")
                .hasCauseInstanceOf(IOException.class);
    }

    interface DocEdit {
        void apply(PDDocument doc) throws IOException;
    }

    /** A PDF made of opetajad-b.pdf pages by zero-based index; -1 adds a blank page. */
    static byte[] samplePages(DocEdit edit, int... pages) throws IOException {
        try (var src = Loader.loadPDF(TestPdfs.load("opetajad-b.pdf")); var doc = new PDDocument();
             var out = new ByteArrayOutputStream()) {
            for (int p : pages) {
                if (p < 0) doc.addPage(new PDPage());
                else doc.importPage(src.getPage(p));
            }
            edit.apply(doc);
            doc.save(out);
            return out.toByteArray();
        }
    }

    static void drawText(PDPageContentStream cs, PDType1Font font, float size, String text, float x, float y) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    @Test
    void rejectsANonTimetablePdf() throws Exception {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            assertThatThrownBy(() -> new TimetablePdfParser().parse(out.toByteArray()))
                    .isInstanceOf(TimetablePdfParser.NotATimetableException.class);
        }
    }
}
