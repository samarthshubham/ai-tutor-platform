package io.github.samarthshubham.tutor.identity.service;

import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.identity.domain.AppUser;
import io.github.samarthshubham.tutor.identity.domain.AppUserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final AppUserRepository users;

  public UserService(AppUserRepository users) {
    this.users = users;
  }

  @Transactional(readOnly = true)
  public UserResponse get(UUID id) {
    return users
        .findById(id)
        .map(UserService::toResponse)
        .orElseThrow(() -> new NotFoundException("User not found"));
  }

  static UserResponse toResponse(AppUser user) {
    return new UserResponse(
        user.getId(),
        user.getUsername(),
        user.getDisplayName(),
        user.getEmail(),
        user.getRole(),
        user.getSchoolId());
  }
}
