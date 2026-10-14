package com.asendus.api.service;

import com.asendus.api.dto.UserDto;
import com.asendus.api.model.AppUser;
import com.asendus.api.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserService {
    private final AppUserRepository userRepository;

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(Long userId) {
        AppUser user = getUser(userId);
        return new UserDto(user.getId(), user.getEmail(), user.getName());
    }

    public AppUser getUser(Long userId) {
        // The token outlived its account, so the client should sign in again
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}
