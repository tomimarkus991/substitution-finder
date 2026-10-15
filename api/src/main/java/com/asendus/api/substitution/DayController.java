package com.asendus.api.substitution;

import com.asendus.api.substitution.DayDtos.AbsenceDto;
import com.asendus.api.substitution.DayDtos.CandidateDto;
import com.asendus.api.substitution.DayDtos.CreateAbsenceRequest;
import com.asendus.api.substitution.DayDtos.DayDto;
import com.asendus.api.substitution.DayDtos.SubstitutionDto;
import com.asendus.api.substitution.DayDtos.UpdateSubstitutionRequest;
import com.asendus.api.util.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class DayController {
    private final SubstitutionService substitutionService;

    /** Dates with at least one absence, newest first. */
    @GetMapping("/days")
    public List<LocalDate> days() {
        return substitutionService.days();
    }

    @GetMapping("/days/{date}")
    public DayDto day(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
        return substitutionService.day(date);
    }

    @PostMapping("/days/{date}/absences")
    @ResponseStatus(HttpStatus.CREATED)
    public AbsenceDto createAbsence(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate date,
                                    @Valid @RequestBody CreateAbsenceRequest request) {
        return substitutionService.createAbsence(date, request, JwtUtils.extractUserId(jwt));
    }

    @DeleteMapping("/absences/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAbsence(@PathVariable Long id) {
        substitutionService.deleteAbsence(id);
    }

    /** row: the substitution row being filled; adds teachers teaching the same class at the same time. */
    @GetMapping("/days/{date}/candidates")
    public List<CandidateDto> candidates(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate date,
                                         @RequestParam int period,
                                         @RequestParam(required = false) Long row) {
        return substitutionService.candidates(date, period, row);
    }

    @PutMapping("/substitutions/{id}")
    public SubstitutionDto updateSubstitution(@PathVariable Long id, @Valid @RequestBody UpdateSubstitutionRequest request) {
        return substitutionService.updateSubstitution(id, request);
    }
}
