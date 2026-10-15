package com.asendus.api.timetable;

import com.asendus.api.timetable.TimetableDtos.PlaceholderRequest;
import com.asendus.api.timetable.TimetableDtos.RenameRequest;
import com.asendus.api.timetable.TimetableDtos.TeacherGridDto;
import com.asendus.api.timetable.TimetableDtos.TimetablePreviewDto;
import com.asendus.api.timetable.TimetableDtos.TimetableSummaryDto;
import com.asendus.api.util.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class TimetableController {
    private final TimetableService timetableService;
    private final TeacherRenameService teacherRenameService;

    @PostMapping("/timetables")
    @ResponseStatus(HttpStatus.CREATED)
    public TimetablePreviewDto upload(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fail puudub");
        }
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "tunniplaan.pdf";
        return timetableService.upload(fileName, file.getBytes(), JwtUtils.extractUserId(jwt));
    }

    @PostMapping("/timetables/{id}/activate")
    public TimetablePreviewDto activate(@PathVariable Long id) {
        return timetableService.activate(id);
    }

    @GetMapping("/timetables/active")
    public TimetableSummaryDto active() {
        return timetableService.active();
    }

    @GetMapping("/timetables/active/teachers/{key}")
    public TeacherGridDto grid(@PathVariable String key) {
        return timetableService.grid(key);
    }

    @PutMapping("/teachers/{key}/placeholder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPlaceholder(@PathVariable String key, @RequestBody PlaceholderRequest request) {
        timetableService.setPlaceholder(key, request.placeholder());
    }

    /** A blank name goes back to the PDF's. */
    @PutMapping("/teachers/{key}/name")
    public TeacherGridDto rename(@PathVariable String key, @Valid @RequestBody RenameRequest request) {
        return teacherRenameService.rename(key, request.displayName());
    }
}
