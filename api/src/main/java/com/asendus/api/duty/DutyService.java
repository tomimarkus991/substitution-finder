package com.asendus.api.duty;

import com.asendus.api.duty.DutyDtos.*;
import com.asendus.api.duty.ParsedDuties.ParsedDuty;
import com.asendus.api.timetable.Teacher;
import com.asendus.api.timetable.TeacherKey;
import com.asendus.api.timetable.TimetableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DutyService {
    private final DutyExcelParser parser;
    private final DutyVersionRepository versionRepository;
    private final DutyRepository dutyRepository;
    private final TimetableService timetableService;

    /** Replaces the duties with the Excel's, matching surnames to the active timetable's teachers. */
    @Transactional
    public DutySummaryDto upload(String fileName, byte[] xlsx, Long userId) {
        ParsedDuties parsed;
        try {
            parsed = parser.parse(xlsx);
        } catch (DutyExcelParser.NotADutySheetException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        List<Teacher> teachers = timetableService.activeTeachers();
        if (teachers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lae esmalt tunniplaan üles");
        }
        Map<String, List<Teacher>> bySurname = teachers.stream()
                .collect(Collectors.groupingBy(t -> fold(surnameOf(t.getName()))));

        versionRepository.deleteAllInBatch(); // the database cascades to the old duties
        DutyVersion v = versionRepository.save(DutyVersion.builder()
                .uploadedBy(userId).fileName(fileName).warnings(parsed.warnings()).build());
        dutyRepository.saveAll(parsed.duties().stream().map(p -> toDuty(v.getId(), p, bySurname)).toList());
        return summary(v);
    }

    @Transactional(readOnly = true)
    public Optional<DutySummaryDto> current() {
        return versionRepository.findFirstByOrderByIdDesc().map(this::summary);
    }

    /** Sets the teacher of a duty whose surname didn't match exactly one teacher. */
    @Transactional
    public DutySummaryDto resolve(Long id, String teacherKey) {
        Duty d = unresolved(id);
        Teacher t = timetableService.activeTeacher(TeacherKey.of(teacherKey))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tundmatu õpetaja"));
        d.setTeacherKey(t.getTeacherKey());
        d.setTeacherName(TeacherKey.displayName(t.getName()));
        d.setCandidateKeys(new ArrayList<>());
        dutyRepository.save(d);
        return summary(versionRepository.findById(d.getVersionId()).orElseThrow());
    }

    /** Drops a duty, e.g. a teaching assistant who isn't in the teacher timetable. */
    @Transactional
    public DutySummaryDto skip(Long id) {
        Duty d = unresolved(id);
        dutyRepository.delete(d);
        dutyRepository.flush();
        return summary(versionRepository.findById(d.getVersionId()).orElseThrow());
    }

    /** Matched duties on the weekday (1 = Monday … 5 = Friday), by teacher key. Empty if none uploaded. */
    @Transactional(readOnly = true)
    public Map<String, List<Duty>> dutiesOn(int weekday) {
        return versionRepository.findFirstByOrderByIdDesc()
                .map(v -> dutyRepository.findByVersionIdAndWeekdayAndTeacherKeyNotNull(v.getId(), (short) weekday).stream()
                        .collect(Collectors.groupingBy(Duty::getTeacherKey)))
                .orElse(Map.of());
    }

    /** Only duties whose surname didn't match can be resolved or skipped. */
    private Duty unresolved(Long id) {
        Duty d = dutyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Valvet ei leitud"));
        if (d.getTeacherKey() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valve on juba määratud");
        }
        return d;
    }

    private static Duty toDuty(Long versionId, ParsedDuty p, Map<String, List<Teacher>> bySurname) {
        List<Teacher> matches = bySurname.getOrDefault(fold(p.surname()), List.of());
        Duty.DutyBuilder b = Duty.builder().versionId(versionId)
                .weekday((short) p.weekday()).period((short) p.period()).kind(p.kind())
                .starts(p.starts()).ends(p.ends()).classGroup(p.classGroup()).position(p.position())
                .rawName(p.surname());
        if (matches.size() == 1) {
            Teacher t = matches.get(0);
            b.teacherKey(t.getTeacherKey()).teacherName(TeacherKey.displayName(t.getName()));
        } else {
            b.candidateKeys(new ArrayList<>(matches.stream().map(Teacher::getTeacherKey).toList()));
        }
        return b.build();
    }

    private DutySummaryDto summary(DutyVersion v) {
        List<Duty> all = dutyRepository.findByVersionIdOrderByWeekdayAscPeriodAscIdAsc(v.getId());
        Map<String, String> names = timetableService.activeTeachers().stream()
                .collect(Collectors.toMap(Teacher::getTeacherKey, t -> TeacherKey.displayName(t.getName()), (a, b) -> a));
        List<Duty> matched = all.stream().filter(d -> d.getTeacherKey() != null).toList();
        List<UnresolvedDutyDto> unresolved = all.stream().filter(d -> d.getTeacherKey() == null)
                .map(d -> new UnresolvedDutyDto(d.getId(), d.getWeekday(), DutyLabels.label(d), d.getClassGroup(),
                        d.getRawName(), d.getCandidateKeys().stream().filter(names::containsKey)
                                .map(k -> new TeacherOptionDto(k, names.get(k))).toList()))
                .toList();
        int teacherCount = (int) matched.stream().map(Duty::getTeacherKey).distinct().count();
        return new DutySummaryDto(v.getFileName(), v.getUploadedAt(), matched.size(), teacherCount,
                v.getWarnings(), unresolved);
    }

    /** aSc prints "Surname Firstname(s)". */
    private static String surnameOf(String ascName) {
        return ascName.trim().split("\\s+")[0];
    }

    /** Lowercase without diacritics, so "Mänd" and "mand" match. */
    private static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
