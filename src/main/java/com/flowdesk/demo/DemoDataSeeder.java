package com.flowdesk.demo;

import com.flowdesk.auth.AuthDtos;
import com.flowdesk.auth.AuthService;
import com.flowdesk.customer.CustomerDtos;
import com.flowdesk.customer.CustomerService;
import com.flowdesk.security.AuthenticatedUser;
import com.flowdesk.serviceorder.Priority;
import com.flowdesk.serviceorder.ServiceOrderDtos;
import com.flowdesk.serviceorder.ServiceOrderService;
import com.flowdesk.serviceorder.ServiceOrderStatus;
import com.flowdesk.user.Role;
import com.flowdesk.user.UserDtos;
import com.flowdesk.user.UserRepository;
import com.flowdesk.user.UserService;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Loads a small, realistic data set when the app runs with {@code SPRING_PROFILES_ACTIVE=demo}.
 * It goes through the real services (as an administrator), so the audit trail, workflow rules and
 * notifications are exercised exactly as in production.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    static final String DEMO_PASSWORD = "Demo@12345";
    static final String ADMIN_EMAIL = "admin@demo.flowdesk.dev";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AuthService authService;
    private final UserService userService;
    private final CustomerService customerService;
    private final ServiceOrderService orderService;
    private final UserRepository userRepository;

    public DemoDataSeeder(
            AuthService authService,
            UserService userService,
            CustomerService customerService,
            ServiceOrderService orderService,
            UserRepository userRepository) {
        this.authService = authService;
        this.userService = userService;
        this.customerService = customerService;
        this.orderService = orderService;
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            log.info("Demo data already present, skipping");
            return;
        }
        UserDtos.Response admin = authService
                .registerCompany(new AuthDtos.RegisterCompanyRequest(
                        "Demo Tech Assistance", "Alex Admin", ADMIN_EMAIL, DEMO_PASSWORD))
                .user();
        actAs(admin);
        try {
            userService.create(new UserDtos.CreateRequest("Maria Manager", "manager@demo.flowdesk.dev", DEMO_PASSWORD, Role.MANAGER));
            UUID bruno = userService.create(new UserDtos.CreateRequest(
                    "Bruno Tech", "bruno@demo.flowdesk.dev", DEMO_PASSWORD, Role.TECHNICIAN)).id();
            UUID carla = userService.create(new UserDtos.CreateRequest(
                    "Carla Tech", "carla@demo.flowdesk.dev", DEMO_PASSWORD, Role.TECHNICIAN)).id();

            UUID acme = customerService.create(new CustomerDtos.Request(
                    "Acme Logistics", "it@acme.example", "+55 84 99999-0001", null, "Av. Central, 100")).id();
            UUID padaria = customerService.create(new CustomerDtos.Request(
                    "Padaria Bom Pao", "contato@bompao.example", "+55 84 99999-0002", null, null)).id();
            UUID clinica = customerService.create(new CustomerDtos.Request(
                    "Clinica Vida", "ti@vida.example", "+55 84 99999-0003", null, null)).id();

            // 1) Fresh order, still unassigned
            create("Office Wi-Fi keeps dropping", "Intermittent drops every afternoon.", acme, Priority.MEDIUM, null);

            // 2) Assigned, waiting for the technician to start
            create("Replace failing UPS battery", "UPS beeps continuously.", padaria, Priority.HIGH, bruno);

            // 3) In diagnosis
            UUID inDiagnosis = create("POS terminal will not boot", "Black screen after power on.", padaria, Priority.URGENT, carla);
            move(inDiagnosis, ServiceOrderStatus.IN_DIAGNOSIS, null, null, null);

            // 4) Waiting for the customer's approval
            UUID waiting = create("Server disk replacement quote", "SMART warnings on RAID disk 2.", clinica, Priority.HIGH, bruno);
            move(waiting, ServiceOrderStatus.IN_DIAGNOSIS, null, null, null);
            move(waiting, ServiceOrderStatus.WAITING_APPROVAL, "Disk 2 is degrading; replace before it fails.", null, null);

            // 5) In execution
            UUID executing = create("Install new network printer", "Reception printer for 12 users.", clinica, Priority.LOW, carla);
            move(executing, ServiceOrderStatus.IN_DIAGNOSIS, null, null, null);
            move(executing, ServiceOrderStatus.IN_EXECUTION, "Driver and static IP required.", null, null);

            // 6) Completed
            UUID done = create("Reset admin password on file server", "Locked out after rotation.", acme, Priority.MEDIUM, bruno);
            move(done, ServiceOrderStatus.IN_DIAGNOSIS, null, null, null);
            move(done, ServiceOrderStatus.IN_EXECUTION, "Account locked by policy.", null, null);
            move(done, ServiceOrderStatus.COMPLETED, null, "Password reset and policy exception documented.", null);

            // 7) Cancelled
            UUID cancelled = create("Quote for new CCTV system", "Customer asked for a quote.", acme, Priority.LOW, null);
            move(cancelled, ServiceOrderStatus.CANCELLED, null, null, "Customer chose another vendor.");

            log.info("Demo data loaded. Log in as {} / {} (also manager@, bruno@, carla@ demo.flowdesk.dev)",
                    ADMIN_EMAIL, DEMO_PASSWORD);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private UUID create(String title, String description, UUID customerId, Priority priority, UUID assigneeId) {
        return orderService
                .create(new ServiceOrderDtos.CreateRequest(title, description, customerId, priority, assigneeId))
                .id();
    }

    private void move(UUID orderId, ServiceOrderStatus to, String diagnosis, String resolution, String reason) {
        orderService.transition(orderId, new ServiceOrderDtos.TransitionRequest(to, diagnosis, resolution, reason));
    }

    private static void actAs(UserDtos.Response user) {
        AuthenticatedUser principal = new AuthenticatedUser(user.id(), user.companyId(), user.role(), user.name());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()))));
    }
}
