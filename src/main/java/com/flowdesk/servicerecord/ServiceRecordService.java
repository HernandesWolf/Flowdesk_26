package com.flowdesk.servicerecord;

import com.flowdesk.audit.AuditAction;
import com.flowdesk.audit.AuditService;
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
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ServiceRecordService {

    private final ServiceRecordRepository records;
    private final ServiceOrderService orders;
    private final UserRepository users;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public ServiceRecordService(
            ServiceRecordRepository records,
            ServiceOrderService orders,
            UserRepository users,
            AuditService audit,
            CurrentUser currentUser) {
        this.records = records;
        this.orders = orders;
        this.users = users;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Transactional
    public ServiceRecordDtos.Response add(UUID orderId, ServiceRecordDtos.CreateRequest request) {
        AuthenticatedUser actor = currentUser.get();
        ServiceOrder order = orders.requireVisible(orderId);
        if (order.getStatus() == ServiceOrderStatus.CANCELLED) {
            throw new BusinessRuleException("Records cannot be added to a cancelled service order");
        }
        User author = users.findByIdAndCompanyId(actor.id(), actor.companyId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        ServiceRecord record = records.save(
                new ServiceRecord(actor.companyId(), order, author, request.content().trim(), request.minutesSpent()));
        audit.record(order, AuditAction.RECORD_ADDED, "record", null, request.content().trim());
        return ServiceRecordDtos.Response.from(record);
    }

    public List<ServiceRecordDtos.Response> list(UUID orderId) {
        ServiceOrder order = orders.requireVisible(orderId);
        return records.findByServiceOrderIdAndCompanyIdOrderByCreatedAtAsc(order.getId(), order.getCompanyId()).stream()
                .map(ServiceRecordDtos.Response::from)
                .toList();
    }
}
