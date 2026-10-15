package com.asendus.api.timetable;

import com.asendus.api.timetable.ParsedTimetable.ParsedTeacher;
import com.asendus.api.timetable.TimetableDtos.LessonDto;
import com.asendus.api.timetable.TimetableDtos.TeacherGridDto;
import com.asendus.api.timetable.TimetableDtos.TeacherSummaryDto;
import com.asendus.api.timetable.TimetableDtos.TimetablePreviewDto;
import com.asendus.api.timetable.TimetableDtos.TimetableSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TimetableService {
    private final TimetablePdfParser parser;
    private final TimetableVersionRepository versionRepository;
    private final TeacherRepository teacherRepository;
    private final LessonRepository lessonRepository;
    private final PlaceholderFlagRepository placeholderFlagRepository;
    private final TeacherNameOverrideRepository nameOverrideRepository;

    /** Parses the PDF into a new inactive version, dropping any earlier version that isn't active. */
    @Transactional
    public TimetablePreviewDto upload(String fileName, byte[] pdf, Long userId) {
        ParsedTimetable parsed;
        try {
            parsed = parser.parse(pdf);
        } catch (TimetablePdfParser.NotATimetableException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        versionRepository.deleteInactive();
        TimetableVersion v = versionRepository.save(TimetableVersion.builder()
                .uploadedBy(userId).fileName(fileName).active(false).warnings(parsed.warnings()).build());
        for (ParsedTeacher pt : parsed.teachers()) {
            Teacher t = teacherRepository.save(Teacher.builder()
                    .versionId(v.getId()).teacherKey(TeacherKey.of(pt.name())).name(pt.name()).build());
            lessonRepository.saveAll(pt.lessons().stream().map(l -> Lesson.builder().teacherId(t.getId())
                    .weekday((short) l.weekday()).period((short) l.period()).classes(l.classes())
                    .subject(l.subject()).room(l.room()).note(l.note()).build()).toList());
        }
        return preview(v);
    }

    /** Makes the version the only active one. */
    @Transactional
    public TimetablePreviewDto activate(Long id) {
        TimetableVersion v = versionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tunniplaani ei leitud"));
        versionRepository.findByActiveTrue().ifPresent(old -> {
            old.setActive(false);
            versionRepository.saveAndFlush(old); // the one-active unique index needs this before the next update
        });
        v.setActive(true);
        versionRepository.saveAndFlush(v);
        return preview(v);
    }

    @Transactional(readOnly = true)
    public TimetableSummaryDto active() {
        TimetableVersion v = activeVersion()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tunniplaan puudub"));
        List<Teacher> teachers = teacherRepository.findByVersionIdOrderByName(v.getId());
        Map<Long, Long> lessonCounts = lessonRepository.findByTeacherIdIn(ids(teachers)).stream()
                .collect(Collectors.groupingBy(Lesson::getTeacherId, Collectors.counting()));
        Set<String> placeholders = placeholderKeys();
        Map<String, String> names = nameOverrides();
        List<TeacherSummaryDto> dtos = teachers.stream().map(t -> new TeacherSummaryDto(
                        t.getTeacherKey(), t.getName(), displayName(t, names),
                        placeholders.contains(t.getTeacherKey()),
                        lessonCounts.getOrDefault(t.getId(), 0L).intValue()))
                .sorted(Comparator.comparing(TeacherSummaryDto::displayName, TeacherKey.DISPLAY_ORDER))
                .toList();
        return new TimetableSummaryDto(v.getId(), v.getFileName(), v.getUploadedAt(), dtos);
    }

    @Transactional(readOnly = true)
    public TeacherGridDto grid(String key) {
        Teacher t = activeTeacher(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Õpetajat ei leitud"));
        List<LessonDto> lessons = lessonRepository.findByTeacherIdOrderByWeekdayAscPeriodAsc(t.getId()).stream()
                .map(l -> new LessonDto(l.getWeekday(), l.getPeriod(), l.getClasses(), l.getSubject(), l.getRoom(), l.getNote()))
                .toList();
        Optional<TeacherNameOverride> set = nameOverrideRepository.findById(t.getTeacherKey());
        return new TeacherGridDto(t.getTeacherKey(), t.getName(),
                set.map(TeacherNameOverride::getDisplayName).orElseGet(() -> TeacherKey.displayName(t.getName())),
                set.isPresent(), placeholderFlagRepository.existsById(t.getTeacherKey()), lessons);
    }

    /** Stored by key, so the flag carries over to later versions. */
    @Transactional
    public void setPlaceholder(String key, boolean placeholder) {
        String k = TeacherKey.of(key);
        if (placeholder) {
            placeholderFlagRepository.save(new PlaceholderFlag(k));
        } else {
            placeholderFlagRepository.deleteById(k);
        }
    }

    /**
     * The active version's teachers that teach on the weekday (1 = Monday … 5 = Friday), each with that day's
     * lessons ordered by period, iterated in name order. Teachers with no lessons that day are left out.
     * Empty if there is no active version.
     */
    @Transactional(readOnly = true)
    public Map<Teacher, List<Lesson>> lessonsOn(int weekday) {
        Optional<TimetableVersion> v = activeVersion();
        if (v.isEmpty()) return Map.of();
        List<Teacher> teachers = teacherRepository.findByVersionIdOrderByName(v.get().getId());
        Map<Long, List<Lesson>> byTeacher = lessonRepository.findByTeacherIdInAndWeekday(ids(teachers), (short) weekday).stream()
                .sorted(Comparator.comparingInt(Lesson::getPeriod))
                .collect(Collectors.groupingBy(Lesson::getTeacherId));
        Map<Teacher, List<Lesson>> out = new LinkedHashMap<>();
        for (Teacher t : teachers) {
            List<Lesson> lessons = byTeacher.get(t.getId());
            if (lessons != null) out.put(t, lessons);
        }
        return out;
    }

    /** The teacher's lessons on the weekday (1 = Monday … 5 = Friday), ordered by period. */
    @Transactional(readOnly = true)
    public List<Lesson> lessonsOf(Teacher teacher, int weekday) {
        return lessonRepository.findByTeacherIdInAndWeekday(List.of(teacher.getId()), (short) weekday).stream()
                .sorted(Comparator.comparingInt(Lesson::getPeriod).thenComparing(Lesson::getId))
                .toList();
    }

    /** The active version's teachers in name order; empty if there is no active version. */
    @Transactional(readOnly = true)
    public List<Teacher> activeTeachers() {
        return activeVersion().map(v -> teacherRepository.findByVersionIdOrderByName(v.getId())).orElse(List.of());
    }

    /** The teacher with this key in the active version; empty if there's no active version or no such teacher. */
    @Transactional(readOnly = true)
    public Optional<Teacher> activeTeacher(String key) {
        return activeVersion().flatMap(v -> teacherRepository.findByVersionIdAndTeacherKey(v.getId(), TeacherKey.of(key)));
    }

    /**
     * For each active-version teacher, the distinct non-empty subject codes they teach across the whole week,
     * sorted. Teachers with no subjects map to an empty list. Empty if there is no active version.
     */
    @Transactional(readOnly = true)
    public Map<String, List<String>> subjectsByTeacherKey() {
        Optional<TimetableVersion> v = activeVersion();
        if (v.isEmpty()) return Map.of();
        List<Teacher> teachers = teacherRepository.findByVersionIdOrderByName(v.get().getId());
        Map<Long, SortedSet<String>> byTeacher = new HashMap<>();
        for (Lesson l : lessonRepository.findByTeacherIdIn(ids(teachers))) {
            if (!l.getSubject().isBlank()) {
                byTeacher.computeIfAbsent(l.getTeacherId(), id -> new TreeSet<>()).add(l.getSubject());
            }
        }
        Map<String, List<String>> out = new HashMap<>();
        for (Teacher t : teachers) {
            out.put(t.getTeacherKey(), List.copyOf(byTeacher.getOrDefault(t.getId(), new TreeSet<>())));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public boolean hasActiveVersion() {
        return activeVersion().isPresent();
    }

    /** Keys of all teachers flagged as placeholders (across versions). */
    @Transactional(readOnly = true)
    public Set<String> placeholderKeys() {
        return placeholderFlagRepository.findAll().stream().map(PlaceholderFlag::getTeacherKey)
                .collect(Collectors.toSet());
    }

    /** key → display name set by hand (across versions). */
    @Transactional(readOnly = true)
    public Map<String, String> nameOverrides() {
        return nameOverrideRepository.findAll().stream()
                .collect(Collectors.toMap(TeacherNameOverride::getTeacherKey, TeacherNameOverride::getDisplayName));
    }

    /** The name shown everywhere: set by hand, else the PDF's "Firstname Surname". */
    public static String displayName(Teacher t, Map<String, String> overrides) {
        String set = overrides.get(t.getTeacherKey());
        return set != null ? set : TeacherKey.displayName(t.getName());
    }

    @Transactional(readOnly = true)
    public String displayName(Teacher t) {
        return nameOverrideRepository.findById(t.getTeacherKey()).map(TeacherNameOverride::getDisplayName)
                .orElseGet(() -> TeacherKey.displayName(t.getName()));
    }

    private Optional<TimetableVersion> activeVersion() {
        return versionRepository.findByActiveTrue();
    }

    private TimetablePreviewDto preview(TimetableVersion v) {
        List<Teacher> teachers = teacherRepository.findByVersionIdOrderByName(v.getId());
        int lessonCount = (int) lessonRepository.countByTeacherIdIn(ids(teachers));

        List<String> added = List.of();
        List<String> removed = List.of();
        Optional<TimetableVersion> active = activeVersion().filter(a -> !a.getId().equals(v.getId()));
        if (active.isPresent()) {
            Map<String, String> mine = namesByKey(teachers);
            Map<String, String> theirs = namesByKey(teacherRepository.findByVersionIdOrderByName(active.get().getId()));
            added = difference(mine, theirs);
            removed = difference(theirs, mine);
        }
        return new TimetablePreviewDto(v.getId(), v.getFileName(), v.isActive(), teachers.size(), lessonCount,
                added, removed, v.getWarnings());
    }

    private static Map<String, String> namesByKey(List<Teacher> teachers) {
        return teachers.stream().collect(Collectors.toMap(Teacher::getTeacherKey, Teacher::getName, (a, b) -> a));
    }

    /** Display names of the teachers in {@code a} whose key isn't in {@code b}, sorted. */
    private static List<String> difference(Map<String, String> a, Map<String, String> b) {
        return a.entrySet().stream().filter(e -> !b.containsKey(e.getKey()))
                .map(e -> TeacherKey.displayName(e.getValue())).sorted(TeacherKey.DISPLAY_ORDER).toList();
    }

    private static List<Long> ids(List<Teacher> teachers) {
        return teachers.stream().map(Teacher::getId).toList();
    }
}
