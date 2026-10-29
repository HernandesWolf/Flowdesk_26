package com.flowdesk.auth;

import com.flowdesk.common.exception.ConflictException;
import com.flowdesk.common.exception.UnauthorizedException;
import com.flowdesk.company.Company;
import com.flowdesk.company.CompanyRepository;
import com.flowdesk.security.JwtService;
import com.flowdesk.user.Role;
import com.flowdesk.user.User;
import com.flowdesk.user.UserDtos;
import com.flowdesk.user.UserRepository;
import com.flowdesk.user.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final CompanyRepository companies;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            CompanyRepository companies, UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.companies = companies;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** Self-service onboarding: creates a new tenant and its first administrator. */
    @Transactional
    public AuthDtos.AuthResponse registerCompany(AuthDtos.RegisterCompanyRequest request) {
        String email = UserService.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("A user with this email already exists");
        }
        Company company = companies.save(new Company(request.companyName().trim()));
        User admin = users.save(new User(
                company.getId(), request.adminName().trim(), email, passwordEncoder.encode(request.password()), Role.ADMIN));
        return toResponse(admin);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        User user = users.findByEmail(UserService.normalizeEmail(request.email()))
                .filter(User::isActive)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        return toResponse(user);
    }

    private AuthDtos.AuthResponse toResponse(User user) {
        return new AuthDtos.AuthResponse(
                jwtService.generate(user), "Bearer", jwtService.ttl().toSeconds(), UserDtos.Response.from(user));
    }
}
