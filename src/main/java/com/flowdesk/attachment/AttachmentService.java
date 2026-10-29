package com.flowdesk.attachment;

import com.flowdesk.audit.AuditAction;
import com.flowdesk.audit.AuditService;
import com.flowdesk.common.exception.BadRequestException;
import com.flowdesk.common.exception.BusinessRuleException;
import com.flowdesk.common.exception.NotFoundException;
import com.flowdesk.security.AuthenticatedUser;
import com.flowdesk.security.CurrentUser;
import com.flowdesk.serviceorder.ServiceOrder;
import com.flowdesk.serviceorder.ServiceOrderService;
import com.flowdesk.serviceorder.ServiceOrderStatus;
import com.flowdesk.user.User;
import com.flowdesk.user.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class AttachmentService {

    /** Allow-list of upload types. Anything else is rejected rather than trusted. */
    static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/webp",
            "text/plain",
            "text/csv",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    public record Download(Resource resource, String filename, String contentType, long sizeBytes) {}

    private final AttachmentRepository attachments;
    private final ServiceOrderService orders;
    private final UserRepository users;
    private final FileStorage storage;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public AttachmentService(
            AttachmentRepository attachments,
            ServiceOrderService orders,
            UserRepository users,
            FileStorage storage,
            AuditService audit,
            CurrentUser currentUser) {
        this.attachments = attachments;
        this.orders = orders;
        this.users = users;
        this.storage = storage;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Transactional
    public AttachmentDtos.Response upload(UUID orderId, MultipartFile file) {
        AuthenticatedUser actor = currentUser.get();
        ServiceOrder order = orders.requireVisible(orderId);
        if (order.getStatus() == ServiceOrderStatus.CANCELLED) {
            throw new BusinessRuleException("Files cannot be attached to a cancelled service order");
        }
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("The uploaded file is empty");
        }
        // Drop parameters such as "; charset=UTF-8" before checking the allow-list.
        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessRuleException("File type not allowed. Allowed types: " + ALLOWED_TYPES);
        }
        User uploader = users.findByIdAndCompanyId(actor.id(), actor.companyId())
                .orElseThrow(() -> new NotFoundException("User not found"));

        String key = storage.store(actor.companyId(), orderId, file);
        // If the database transaction fails after the bytes were written, do not leave an orphan file behind.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.delete(key);
                }
            }
        });

        String filename = sanitizeFilename(file.getOriginalFilename());
        Attachment attachment = attachments.save(new Attachment(
                actor.companyId(), order, uploader, filename, contentType, file.getSize(), key));
        audit.record(order, AuditAction.ATTACHMENT_ADDED, "attachment", null, filename);
        return AttachmentDtos.Response.from(attachment);
    }

    public List<AttachmentDtos.Response> list(UUID orderId) {
        ServiceOrder order = orders.requireVisible(orderId);
        return attachments.findByServiceOrderIdAndCompanyIdOrderByCreatedAtAsc(order.getId(), order.getCompanyId()).stream()
                .map(AttachmentDtos.Response::from)
                .toList();
    }

    public Download download(UUID orderId, UUID attachmentId) {
        ServiceOrder order = orders.requireVisible(orderId);
        Attachment attachment = attachments
                .findByIdAndServiceOrderIdAndCompanyId(attachmentId, order.getId(), order.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Attachment not found"));
        return new Download(
                storage.load(attachment.getStorageKey()),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes());
    }

    /** Keeps only the last path segment and strips control characters from a client-supplied name. */
    static String sanitizeFilename(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            name = "attachment";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
