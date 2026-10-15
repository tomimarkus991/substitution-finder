package com.asendus.api.timetable;

import com.asendus.api.duty.DutyService;
import com.asendus.api.timetable.TimetableDtos.TeacherGridDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Renames a teacher and lets break duties pick up the new surname, in one transaction. */
@Service
@RequiredArgsConstructor
public class TeacherRenameService {
    private final TimetableService timetableService;
    private final DutyService dutyService;

    @Transactional
    public TeacherGridDto rename(String key, String displayName) {
        TeacherGridDto grid = timetableService.rename(key, displayName);
        dutyService.rematch(grid.key());
        return grid;
    }
}
