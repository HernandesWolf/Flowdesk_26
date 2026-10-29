package com.flowdesk.customer;

import com.flowdesk.common.exception.NotFoundException;
import com.flowdesk.common.web.PageResponse;
import com.flowdesk.security.CurrentUser;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customers;
    private final CurrentUser currentUser;

    public CustomerService(CustomerRepository customers, CurrentUser currentUser) {
        this.customers = customers;
        this.currentUser = currentUser;
    }

    @Transactional
    public CustomerDtos.Response create(CustomerDtos.Request r) {
        Customer customer = customers.save(new Customer(
                currentUser.companyId(), r.name().trim(), blankToNull(r.email()), blankToNull(r.phone()),
                blankToNull(r.document()), blankToNull(r.address())));
        return CustomerDtos.Response.from(customer);
    }

    @Transactional
    public CustomerDtos.Response update(UUID id, CustomerDtos.Request r) {
        Customer customer = find(id);
        customer.update(r.name().trim(), blankToNull(r.email()), blankToNull(r.phone()),
                blankToNull(r.document()), blankToNull(r.address()));
        return CustomerDtos.Response.from(customer);
    }

    public CustomerDtos.Response get(UUID id) {
        return CustomerDtos.Response.from(find(id));
    }

    public PageResponse<CustomerDtos.Response> list(String query, Pageable pageable) {
        UUID companyId = currentUser.companyId();
        Page<Customer> page = (query == null || query.isBlank())
                ? customers.findByCompanyId(companyId, pageable)
                : customers.findByCompanyIdAndNameContainingIgnoreCase(companyId, query.trim(), pageable);
        return PageResponse.map(page, CustomerDtos.Response::from);
    }

    private Customer find(UUID id) {
        return customers.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> new NotFoundException("Customer not found"));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
