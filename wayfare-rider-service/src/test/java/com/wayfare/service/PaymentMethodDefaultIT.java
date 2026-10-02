package com.wayfare.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.wayfare.domain.PaymentMethod;
import com.wayfare.dto.AddPaymentMethodRequest;
import com.wayfare.repository.PaymentMethodRepository;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(PaymentMethodService.class)
class PaymentMethodDefaultIT {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired PaymentMethodService service;
    @Autowired PaymentMethodRepository repo;

    @Test
    void newDefaultReplacesExistingDefault() {
        UUID user = UUID.randomUUID();
        PaymentMethod first = service.addPaymentMethod(user, new AddPaymentMethodRequest("tok1", "visa", "4242", true));
        PaymentMethod second = service.addPaymentMethod(user, new AddPaymentMethodRequest("tok2", "visa", "1111", true));

        assertThat(repo.findByUserId(user)).filteredOn(PaymentMethod::isDefault)
                .extracting(PaymentMethod::getId).containsExactly(second.getId());

        service.setDefault(user, first.getId());
        assertThat(repo.findByUserId(user)).filteredOn(PaymentMethod::isDefault)
                .extracting(PaymentMethod::getId).containsExactly(first.getId());
    }

    @Test
    void databaseRejectsTwoDefaultsForOneRider() {
        UUID user = UUID.randomUUID();
        repo.saveAndFlush(new PaymentMethod(null, user, "a", "visa", "4242", true));
        assertThatThrownBy(() -> repo.saveAndFlush(new PaymentMethod(null, user, "b", "visa", "1111", true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
