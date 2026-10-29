package com.flowdesk.audit;

import com.flowdesk.security.AuthenticatedUser;
import com.flowdesk.security.CurrentUser;
import com.flowdesk.serviceorder.ServiceOrder;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the audit trail in the same transaction as the change it describes, so a change and its
 * history either both exist or neither does. Rows are append-only.
 */
@Service
public class AuditService {

    private static final int MAX_VALUE_LENGTH = 1000;
    private static final String SYSTEM = "System";

    private final AuditLogRepository repository;
    private final CurrentUser currentUser;

    public AuditService(AuditLogRepository repository, CurrentUser currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(ServiceOrder order, AuditAction action, String field, String oldValue, String newValue) {
        AuthenticatedUser actor = currentUser.find().orElse(null);
        repository.save(new AuditLog(
                order.getCompanyId(),
                order.getId(),
                actor == null ? null : actor.id(),
                actor == null ? SYSTEM : actor.name(),
                action,
                field,
                trim(oldValue),
                trim(newValue)));
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> timeline(UUID companyId, UUID orderId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return repository.findByServiceOrderIdAndCompanyId(orderId, companyId, pageable);
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_VALUE_LENGTH ? value : value.substring(0, MAX_VALUE_LENGTH) + "...";
    }

    /** Public view of an audit row. */
    public record Entry(
            UUID id, AuditAction action, String field, String oldValue, String newValue, UUID actorId, String actorName, Instant createdAt) {

        public static Entry from(AuditLog l) {
            return new Entry(
                    l.getId(), l.getAction(), l.getFieldName(), l.getOldValue(), l.getNewValue(),
                    l.getActorId(), l.getActorName(), l.getCreatedAt());
        }
    }
}
