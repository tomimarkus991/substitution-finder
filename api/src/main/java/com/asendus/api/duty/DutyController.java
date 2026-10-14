package com.asendus.api.duty;

import com.asendus.api.duty.DutyDtos.*;
import com.asendus.api.util.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class DutyController {
    private final DutyService dutyService;

    @PostMapping("/duties")
    @ResponseStatus(HttpStatus.CREATED)
    public DutySummaryDto upload(@AuthenticationPrincipal Jwt jwt,
                                 @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fail puudub");
        }
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "valved.xlsx";
        return dutyService.upload(fileName, file.getBytes(), JwtUtils.extractUserId(jwt));
    }

    @GetMapping("/duties")
    public ResponseEntity<DutySummaryDto> current() {
        return dutyService.current().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/duties/{id}")
    public DutySummaryDto resolve(@PathVariable Long id, @Valid @RequestBody ResolveDutyRequest request) {
        return dutyService.resolve(id, request.teacherKey());
    }

    @DeleteMapping("/duties/{id}")
    public DutySummaryDto skip(@PathVariable Long id) {
        return dutyService.skip(id);
    }
}
