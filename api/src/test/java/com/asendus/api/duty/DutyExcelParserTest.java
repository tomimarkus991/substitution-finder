package com.asendus.api.duty;

import com.asendus.api.TestDutySheet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DutyExcelParserTest {
    static ParsedDuties parsed;

    @BeforeAll static void parse() {
        byte[] xlsx = new TestDutySheet()
                .day("E")
                .group("I klassid", "", LocalTime.of(10, 25), "10:45-11:15", "Kask, Tamm", 4, "Mänd, Saar", 5)
                .group("II klassid", "", LocalTime.of(10, 45), "11:05-11:30", "Kuusk", 6, "Lepp", 4)
                .group("III klassid", "10:25", LocalTime.of(11, 5), "UJUMAS 3.1-3.2", "", null, "Pihlakas", 1)
                .group("VI klassid", "", LocalTime.of(12, 10), "klj 12:30-13:15", "sööklas: klassijuhataja", null, "", null)
                .group("IV klassid", "", LocalTime.of(13, 0), "klj 12:15-12:55", "", null, "4.S & 6.R ÕVT - Vaher", 1)
                .blank()
                .day("T")
                .group("III klassid", "10:25-11:05", LocalTime.of(11, 5), "", "Kask", 1, "", null)
                .group("VI klassid", "", LocalTime.of(12, 10), "", "sööklas: Tamm, Mänd", null, "", null)
                .group("V klassid", "", LocalTime.of(12, 35), "12:55- 13:20", "Kuusk", 1, "", null)
                .group("IV klassid", "12:15-12:55", LocalTime.of(12, 55), "", "Lepp // 4.s klj tund (315)", 1, "", null)
                .blank()
                .day("K")
                .group("I klassid", "", LocalTime.of(10, 25), "10:45-11:15", "Kask", 5, "", null)
                .blank()
                .day("N")
                .group("VI klassid", "", LocalTime.of(12, 10), "",
                        "sööklas: Saar, Vaher // 6.S ÕVT (12:35 -13:10) - pos 4 (Kask/Tamm)", null, "", null)
                .group("V klassid", "", LocalTime.of(12, 35), "12:55- 13:20", "", null, "Lepp (õvt +6.S)", 4)
                .blank()
                .day("R")
                .group("I klassid", "", LocalTime.of(10, 25), "10:45-11:15", "Tamm", 5, "", null)
                .bytes();
        parsed = new DutyExcelParser().parse(xlsx);
    }

    static List<ParsedDuties.ParsedDuty> of(String surname, int weekday) {
        return parsed.duties().stream().filter(d -> d.surname().equals(surname) && d.weekday() == weekday).toList();
    }

    @Test void outdoorDutyWithNumericPosition() {
        assertThat(of("Kask", 1)).singleElement().satisfies(d -> {
            assertThat(d.kind()).isEqualTo("OVT");
            assertThat(d.starts()).isEqualTo(LocalTime.of(10, 45));
            assertThat(d.ends()).isEqualTo(LocalTime.of(11, 15));
            assertThat(d.period()).isEqualTo(3);
            assertThat(d.classGroup()).isEqualTo("I klassid");
            assertThat(d.position()).isEqualTo("4");
        });
        assertThat(of("Saar", 1)).singleElement().satisfies(d -> assertThat(d.position()).isEqualTo("5"));
    }

    @Test void spacedRangeIsRead() {
        assertThat(of("Kuusk", 2)).singleElement().satisfies(d -> {
            assertThat(d.starts()).isEqualTo(LocalTime.of(12, 55));
            assertThat(d.period()).isEqualTo(5);
        });
    }

    @Test void rangeFromColumnCWhenEIsEmpty() {
        assertThat(of("Kask", 2)).singleElement().satisfies(d -> {
            assertThat(d.starts()).isEqualTo(LocalTime.of(10, 25));
            assertThat(d.period()).isEqualTo(3);
        });
    }

    @Test void canteenDutyLasts45MinutesFromTheTimeCell() {
        assertThat(of("Tamm", 2)).singleElement().satisfies(d -> {
            assertThat(d.kind()).isEqualTo("SVT");
            assertThat(d.starts()).isEqualTo(LocalTime.of(12, 10));
            assertThat(d.ends()).isEqualTo(LocalTime.of(12, 55));
            assertThat(d.period()).isEqualTo(5);
        });
        assertThat(of("Mänd", 2)).hasSize(1);
    }

    @Test void cutsNotesButKeepsTheName() {
        assertThat(of("Lepp", 2)).singleElement().satisfies(d -> assertThat(d.period()).isEqualTo(5));
        assertThat(of("Lepp", 4)).hasSize(1);
        assertThat(of("Saar", 4)).hasSize(1);
        assertThat(of("Vaher", 4)).hasSize(1);
    }

    @Test void everyWeekdayAndNoPlaceholderNames() {
        assertThat(parsed.duties()).extracting(ParsedDuties.ParsedDuty::weekday).contains(1, 2, 3, 4, 5);
        assertThat(parsed.duties()).noneMatch(d -> d.surname().equalsIgnoreCase("klassijuhataja"));
    }

    @Test void warnsInsteadOfGuessing() {
        assertThat(parsed.warnings())
                .anySatisfy(w -> assertThat(w).startsWith("E, III klassid").contains("Pihlakas"))  // only "10:25"
                .anySatisfy(w -> assertThat(w).startsWith("E, IV klassid").contains("Vaher"))      // E = klj…, C empty
                .anySatisfy(w -> assertThat(w).contains("klassijuhataja"))
                .anySatisfy(w -> assertThat(w).startsWith("N, VI klassid").contains("6.S ÕVT"))    // hidden after //
                .noneSatisfy(w -> assertThat(w).startsWith("T, IV klassid"));                     // "4.s klj tund" is fine
        assertThat(of("Pihlakas", 1)).isEmpty();
        assertThat(of("Vaher", 1)).isEmpty();
    }

    @Test void rejectsOtherFiles() {
        assertThatThrownBy(() -> new DutyExcelParser().parse("not excel".getBytes()))
                .isInstanceOf(DutyExcelParser.NotADutySheetException.class)
                .hasMessage("See ei tundu olevat vahetunni valvete tabel");
        byte[] noHeaders = new TestDutySheet().group("I klassid", "", null, "10:45-11:15", "Kask", 1, "", null).bytes();
        assertThatThrownBy(() -> new DutyExcelParser().parse(noHeaders))
                .isInstanceOf(DutyExcelParser.NotADutySheetException.class);
    }

    @Test void timeCellIsReadAsTwentyFourHourEvenWithAmPmFormat() {
        byte[] xlsx = new TestDutySheet(true).day("T")
                .group("IV klassid", "", LocalTime.of(12, 55), "", "sööklas: Kask", null, "", null)
                .bytes();
        assertThat(new DutyExcelParser().parse(xlsx).duties()).singleElement().satisfies(d -> {
            assertThat(d.kind()).isEqualTo("SVT");
            assertThat(d.starts()).isEqualTo(LocalTime.of(12, 55));
        });
    }

    @Test void invalidTimeWarnsForThatHalfOnly() {
        byte[] xlsx = new TestDutySheet().day("E")
                .group("I klassid", "", LocalTime.of(10, 25), "10:65-11:15", "Kask", 4, "sööklas: Tamm", null)
                .bytes();
        ParsedDuties result = new DutyExcelParser().parse(xlsx);
        assertThat(result.warnings()).singleElement().satisfies(w -> assertThat(w).contains("Kask").endsWith("vigane kellaaeg"));
        assertThat(result.duties()).extracting(ParsedDuties.ParsedDuty::surname).contains("Tamm").doesNotContain("Kask");
    }
}
