package com.flowdesk.user;

import com.flowdesk.common.exception.BusinessRuleException;
import com.flowdesk.common.exception.ConflictException;
import com.flowdesk.common.exception.NotFoundException;
import com.flowdesk.security.AuthenticatedUser;
import com.flowdesk.security.CurrentUser;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, CurrentUser currentUser) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
    }

    @Transactional
    public UserDtos.Response create(UserDtos.CreateRequest request) {
        AuthenticatedUser actor = currentUser.get();
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("A user with this email already exists");
        }
        User user = users.save(new User(
                actor.companyId(), request.name().trim(), email, passwordEncoder.encode(request.password()), request.role()));
        return UserDtos.Response.from(user);
    }

    public List<UserDtos.Response> list() {
        return users.findByCompanyIdOrderByNameAsc(currentUser.companyId()).stream()
                .map(UserDtos.Response::from)
                .toList();
    }

    public List<UserDtos.Response> listTechnicians() {
        return users.findByCompanyIdAndRoleAndActiveTrueOrderByNameAsc(currentUser.companyId(), Role.TECHNICIAN).stream()
                .map(UserDtos.Response::from)
                .toList();
    }

    @Transactional
    public UserDtos.Response setActive(UUID id, boolean active) {
        AuthenticatedUser actor = currentUser.get();
        if (actor.id().equals(id) && !active) {
            throw new BusinessRuleException("You cannot deactivate your own account");
        }
        User user = users.findByIdAndCompanyId(id, actor.companyId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        user.setActive(active);
        return UserDtos.Response.from(user);
    }

    public UserDtos.Response me() {
        AuthenticatedUser actor = currentUser.get();
        User user = users.findByIdAndCompanyId(actor.id(), actor.companyId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        return UserDtos.Response.from(user);
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
