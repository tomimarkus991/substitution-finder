package com.asendus.api.substitution;

import com.asendus.api.substitution.DayDtos.AbsenceDto;
import com.asendus.api.substitution.DayDtos.CandidateDto;
import com.asendus.api.substitution.DayDtos.CreateAbsenceRequest;
import com.asendus.api.substitution.DayDtos.DayDto;
import com.asendus.api.substitution.DayDtos.SubstitutionDto;
import com.asendus.api.substitution.DayDtos.UpdateSubstitutionRequest;
import com.asendus.api.timetable.Lesson;
import com.asendus.api.timetable.Teacher;
import com.asendus.api.timetable.TeacherKey;
import com.asendus.api.timetable.TimetableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubstitutionService {
    private static final int FIRST_PERIOD = 1;
    private static final int LAST_PERIOD = 7;
    private static final Comparator<Substitution> ROW_ORDER =
            Comparator.comparingInt(Substitution::getPeriod).thenComparing(Substitution::getId);
    private static final Comparator<CandidateDto> CANDIDATE_ORDER = Comparator
            .comparing(CandidateDto::status)               // FREE before ASK_FIRST (enum order)
            .thenComparingInt(CandidateDto::todayCount)
            .thenComparing(CandidateDto::name, TeacherKey.DISPLAY_ORDER);
    private static final String NAME_SEPARATOR = " / ";

    private final TimetableService timetableService;
    private final AbsenceRepository absenceRepository;
    private final SubstitutionRepository substitutionRepository;

    // ---------- days ----------

    @Transactional(readOnly = true)
    public List<LocalDate> days() {
        return absenceRepository.findDistinctDates();
    }

    /** Weekends have no lessons, so they're always an empty day. */
    @Transactional(readOnly = true)
    public DayDto day(LocalDate date) {
        int weekday = date.getDayOfWeek().getValue();
        List<Absence> absences = absencesOn(date);
        Map<Long, List<Substitution>> rows = rowsOf(absences).stream()
                .collect(Collectors.groupingBy(Substitution::getAbsenceId));
        List<AbsenceDto> dtos = absences.stream()
                .map(a -> toDto(a, rows.getOrDefault(a.getId(), List.of())))
                .toList();
        return new DayDto(date, weekday, dtos);
    }

    // ---------- absences ----------

    /** Marks the teacher absent and snapshots one row per lesson of theirs in the period range. */
    @Transactional
    public AbsenceDto createAbsence(LocalDate date, CreateAbsenceRequest request, Long userId) {
        int weekday = schoolWeekday(date);
        int from = request.fromPeriod() != null ? request.fromPeriod() : FIRST_PERIOD;
        int to = request.toPeriod() != null ? request.toPeriod() : LAST_PERIOD;
        if (from > to) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Algustund ei saa olla pärast lõputundi");
        }
        Teacher teacher = timetableService.activeTeacher(TeacherKey.of(request.teacherKey()))
                .orElseThrow(() -> timetableService.hasActiveVersion()
                        ? new ResponseStatusException(HttpStatus.NOT_FOUND, "Õpetajat ei leitud")
                        : new ResponseStatusException(HttpStatus.CONFLICT, "Lae esmalt tunniplaan üles"));
        if (absenceRepository.existsByAbsenceDateAndTeacherKey(date, teacher.getTeacherKey())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "See õpetaja on juba puudujaks märgitud");
        }

        // Flush now so a double-tap that got past the check above fails on the unique constraint here (→ 409)
        Absence absence = absenceRepository.saveAndFlush(Absence.builder()
                .absenceDate(date).teacherKey(teacher.getTeacherKey())
                .teacherName(TeacherKey.displayName(teacher.getName()))
                .fromPeriod((short) from).toPeriod((short) to).createdBy(userId).build());

        List<Lesson> lessons = timetableService.lessonsOf(teacher, weekday);
        List<Substitution> rows = substitutionRepository.saveAll(lessons.stream()
                .filter(l -> l.getPeriod() >= from && l.getPeriod() <= to)
                .map(l -> Substitution.builder().absenceId(absence.getId()).period(l.getPeriod())
                        .classes(l.getClasses()).subject(l.getSubject()).build())
                .toList());
        return toDto(absence, rows);
    }

    @Transactional
    public void deleteAbsence(Long id) {
        if (!absenceRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Puudujat ei leitud");
        }
        absenceRepository.deleteById(id); // rows go with it (ON DELETE CASCADE)
    }

    // ---------- substitutions ----------

    /**
     * Sets the row's substitutes (in the given order) and free text. Eligibility isn't re-checked: the organiser
     * may override the suggestions. A key must be in the active version, or already on the row: a substitute
     * from an older timetable keeps their stored name.
     */
    @Transactional
    public SubstitutionDto updateSubstitution(Long id, UpdateSubstitutionRequest request) {
        Substitution row = substitutionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asendust ei leitud"));
        Map<String, String> stored = storedNames(row);
        Map<String, String> names = new LinkedHashMap<>(); // key → display name, deduplicated, in order
        for (String raw : request.substituteKeys()) {
            String key = TeacherKey.of(raw == null ? "" : raw);
            if (!names.containsKey(key)) names.put(key, nameFor(key, stored));
        }
        row.setSubstituteKeys(new ArrayList<>(names.keySet()));
        row.setSubstituteNames(String.join(NAME_SEPARATOR, names.values()));
        row.setFreeText(request.freeText().trim());
        return toDto(substitutionRepository.save(row));
    }

    private String nameFor(String key, Map<String, String> stored) {
        if (stored.containsKey(key)) return stored.get(key);
        return timetableService.activeTeacher(key)
                .map(t -> TeacherKey.displayName(t.getName()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tundmatu õpetaja"));
    }

    /** The row's current key → name pairs (names are stored joined in key order). */
    private static Map<String, String> storedNames(Substitution row) {
        List<String> keys = row.getSubstituteKeys();
        String[] names = row.getSubstituteNames().isEmpty() ? new String[0] : row.getSubstituteNames().split(NAME_SEPARATOR);
        Map<String, String> out = new HashMap<>();
        if (names.length != keys.size()) return out; // shouldn't happen; fall back to the active version
        for (int i = 0; i < keys.size(); i++) out.put(keys.get(i), names[i]);
        return out;
    }

    // ---------- candidates ----------

    /**
     * Who can cover period p on the date: every active-version teacher who teaches that weekday, except
     * placeholders, teachers absent in p and teachers already substituting in p. HIDDEN ones are left out.
     */
    @Transactional(readOnly = true)
    public List<CandidateDto> candidates(LocalDate date, int period) {
        int weekday = schoolWeekday(date);
        if (period < FIRST_PERIOD || period > LAST_PERIOD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tund peab olema vahemikus 1–7");
        }
        Map<Teacher, List<Lesson>> lessons = timetableService.lessonsOn(weekday);
        Set<String> placeholders = timetableService.placeholderKeys();
        List<Absence> absences = absencesOn(date);
        List<Substitution> rows = rowsOf(absences);

        Set<String> blocked = new HashSet<>(placeholders);
        blocked.addAll(absentIn(absences, period));
        blocked.addAll(substitutingIn(rows, period));
        Map<String, Integer> todayCounts = todayCounts(rows);
        Map<String, List<String>> subjects = timetableService.subjectsByTeacherKey();

        List<CandidateDto> out = new ArrayList<>();
        lessons.forEach((teacher, dayLessons) -> {
            String key = teacher.getTeacherKey();
            Availability status = Eligibility.of(periodsOf(dayLessons), period, blocked.contains(key));
            if (status != Availability.HIDDEN) {
                out.add(new CandidateDto(key, TeacherKey.displayName(teacher.getName()), status,
                        subjects.getOrDefault(key, List.of()), todayCounts.getOrDefault(key, 0)));
            }
        });
        out.sort(CANDIDATE_ORDER);
        return out;
    }

    private static Set<Integer> periodsOf(List<Lesson> lessons) {
        return lessons.stream().map(l -> (int) l.getPeriod()).collect(Collectors.toSet());
    }

    /** Keys of teachers whose absence that day covers the period. */
    private static Set<String> absentIn(List<Absence> absences, int period) {
        return absences.stream()
                .filter(a -> a.getFromPeriod() <= period && period <= a.getToPeriod())
                .map(Absence::getTeacherKey)
                .collect(Collectors.toSet());
    }

    /** Keys of teachers already assigned to some row in the period. */
    private static Set<String> substitutingIn(List<Substitution> rows, int period) {
        return rows.stream()
                .filter(r -> r.getPeriod() == period)
                .flatMap(r -> r.getSubstituteKeys().stream())
                .collect(Collectors.toSet());
    }

    /** Per teacher key, the number of distinct periods that day in which they substitute. */
    private static Map<String, Integer> todayCounts(List<Substitution> rows) {
        Map<String, Set<Integer>> periods = new HashMap<>();
        for (Substitution r : rows) {
            for (String key : r.getSubstituteKeys()) {
                periods.computeIfAbsent(key, k -> new HashSet<>()).add((int) r.getPeriod());
            }
        }
        Map<String, Integer> counts = new HashMap<>();
        periods.forEach((key, ps) -> counts.put(key, ps.size()));
        return counts;
    }

    // ---------- helpers ----------

    /** 1 = Monday … 5 = Friday; weekends are rejected. */
    private static int schoolWeekday(LocalDate date) {
        int weekday = date.getDayOfWeek().getValue();
        if (weekday > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nädalavahetusel tunde pole");
        }
        return weekday;
    }

    /** The day's absences in Estonian order of the teacher's display name. */
    private List<Absence> absencesOn(LocalDate date) {
        return absenceRepository.findByAbsenceDate(date).stream()
                .sorted(Comparator.comparing(Absence::getTeacherName, TeacherKey.DISPLAY_ORDER))
                .toList();
    }

    private List<Substitution> rowsOf(List<Absence> absences) {
        if (absences.isEmpty()) return List.of();
        return substitutionRepository.findByAbsenceIdInOrderByPeriod(absences.stream().map(Absence::getId).toList());
    }

    private static AbsenceDto toDto(Absence a, List<Substitution> rows) {
        return new AbsenceDto(a.getId(), a.getTeacherKey(), a.getTeacherName(), a.getFromPeriod(), a.getToPeriod(),
                rows.stream().sorted(ROW_ORDER).map(SubstitutionService::toDto).toList());
    }

    private static SubstitutionDto toDto(Substitution s) {
        return new SubstitutionDto(s.getId(), s.getPeriod(), s.getClasses(), s.getSubject(),
                List.copyOf(s.getSubstituteKeys()), s.getSubstituteNames(), s.getFreeText());
    }
}
