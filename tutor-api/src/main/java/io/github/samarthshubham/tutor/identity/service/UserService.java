package io.github.samarthshubham.tutor.identity.service;

import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.common.api.PageResponse;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.domain.AppUser;
import io.github.samarthshubham.tutor.identity.domain.AppUserRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private static final char LIKE_ESCAPE = '\\';
  private static final Sort BY_DISPLAY_NAME = Sort.by("displayName", "username");

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

  @Transactional(readOnly = true)
  public UserResponse getInSchool(UUID id, UUID schoolId) {
    return users
        .findById(id)
        .filter(user -> user.getSchoolId().equals(schoolId))
        .map(UserService::toResponse)
        .orElseThrow(() -> new NotFoundException("User not found"));
  }

  @Transactional(readOnly = true)
  public PageResponse<UserResponse> search(
      UUID schoolId, Role role, String query, int page, int size) {
    Specification<AppUser> filter =
        Specification.allOf(inSchool(schoolId), hasRole(role), matches(query));
    return PageResponse.from(
        users
            .findAll(filter, PageRequest.of(page, size, BY_DISPLAY_NAME))
            .map(UserService::toResponse));
  }

  private static Specification<AppUser> inSchool(UUID schoolId) {
    return (root, criteria, cb) -> cb.equal(root.get("schoolId"), schoolId);
  }

  private static Specification<AppUser> hasRole(Role role) {
    return role == null
        ? Specification.unrestricted()
        : (root, criteria, cb) -> cb.equal(root.get("role"), role);
  }

  private static Specification<AppUser> matches(String query) {
    if (query == null || query.isBlank()) {
      return Specification.unrestricted();
    }
    String pattern = "%" + escapeLike(query.strip().toLowerCase(Locale.ROOT)) + "%";
    return (root, criteria, cb) ->
        cb.or(
            cb.like(cb.lower(root.get("username")), pattern, LIKE_ESCAPE),
            cb.like(cb.lower(root.get("displayName")), pattern, LIKE_ESCAPE));
  }

  private static String escapeLike(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
