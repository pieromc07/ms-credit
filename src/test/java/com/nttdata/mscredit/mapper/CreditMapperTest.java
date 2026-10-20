package com.nttdata.mscredit.mapper;

import static com.nttdata.mscredit.support.Fixtures.NOW;
import static com.nttdata.mscredit.support.Fixtures.TODAY;
import static com.nttdata.mscredit.support.Fixtures.card;
import static com.nttdata.mscredit.support.Fixtures.clock;
import static com.nttdata.mscredit.support.Fixtures.product;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.nttdata.mscredit.generated.dto.CreditCardStatus;
import com.nttdata.mscredit.generated.dto.CreditProductStatus;
import com.nttdata.mscredit.generated.dto.CustomerType;
import com.nttdata.mscredit.model.CreditCard;
import com.nttdata.mscredit.model.CreditProduct;

class CreditMapperTest {

    private final CreditMapper mapper = new CreditMapper(clock());

    @Test
    void mapsOverdueProductToApiDto() {
        CreditProduct product = product("p1", com.nttdata.mscredit.model.CustomerType.BUSINESS, "300.00",
                TODAY.minusDays(1), com.nttdata.mscredit.model.CreditProductStatus.ACTIVE);

        com.nttdata.mscredit.generated.dto.CreditProduct dto = mapper.toProductDto(product);

        assertThat(dto.getId()).isEqualTo("p1");
        assertThat(dto.getCustomerType()).isEqualTo(CustomerType.BUSINESS);
        assertThat(dto.getAmount()).isEqualTo(1000.0);
        assertThat(dto.getOutstandingBalance()).isEqualTo(300.0);
        assertThat(dto.getDueDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(dto.getStatus()).isEqualTo(CreditProductStatus.ACTIVE);
        assertThat(dto.getOverdue()).isTrue();
        assertThat(dto.getCreatedAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    void marksProductAsNotOverdueWhenDueInTheFuture() {
        CreditProduct product = product("p1", com.nttdata.mscredit.model.CustomerType.PERSONAL, "300.00",
                TODAY.plusDays(1), com.nttdata.mscredit.model.CreditProductStatus.ACTIVE);

        assertThat(mapper.toProductDto(product).getOverdue()).isFalse();
    }

    @Test
    void mapsProductWithoutOptionalFields() {
        com.nttdata.mscredit.generated.dto.CreditProduct dto = mapper.toProductDto(CreditProduct.builder().build());

        assertThat(dto.getCustomerType()).isNull();
        assertThat(dto.getStatus()).isNull();
        assertThat(dto.getAmount()).isNull();
        assertThat(dto.getCreatedAt()).isNull();
        assertThat(dto.getOverdue()).isFalse();
    }

    @Test
    void mapsCardWithAvailableCredit() {
        CreditCard card = card("card-1", "5000.00", "1200.00");

        com.nttdata.mscredit.generated.dto.CreditCard dto = mapper.toCardDto(card);

        assertThat(dto.getId()).isEqualTo("card-1");
        assertThat(dto.getMaskedNumber()).isEqualTo("**** **** **** 1234");
        assertThat(dto.getCreditLimit()).isEqualTo(5000.0);
        assertThat(dto.getConsumedAmount()).isEqualTo(1200.0);
        assertThat(dto.getAvailableCredit()).isEqualTo(3800.0);
        assertThat(dto.getStatus()).isEqualTo(CreditCardStatus.ACTIVE);
        assertThat(dto.getUpdatedAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    void mapsCardWithoutOptionalFields() {
        CreditCard card = CreditCard.builder()
                .creditLimit(java.math.BigDecimal.ZERO)
                .consumedAmount(java.math.BigDecimal.ZERO)
                .build();

        com.nttdata.mscredit.generated.dto.CreditCard dto = mapper.toCardDto(card);

        assertThat(dto.getCustomerType()).isNull();
        assertThat(dto.getStatus()).isNull();
        assertThat(dto.getUpdatedAt()).isNull();
        assertThat(dto.getAvailableCredit()).isEqualTo(0.0);
    }

    @Test
    void mapsApiEnumsToModelEnums() {
        assertThat(mapper.toModelCustomerType(CustomerType.BUSINESS))
                .isEqualTo(com.nttdata.mscredit.model.CustomerType.BUSINESS);
        assertThat(mapper.toModelProductStatus(CreditProductStatus.PAID))
                .isEqualTo(com.nttdata.mscredit.model.CreditProductStatus.PAID);
        assertThat(mapper.toModelCardStatus(CreditCardStatus.CANCELLED))
                .isEqualTo(com.nttdata.mscredit.model.CreditCardStatus.CANCELLED);
    }

    @Test
    void nullApiEnumsMapToNull() {
        assertThat(mapper.toModelCustomerType(null)).isNull();
        assertThat(mapper.toModelProductStatus(null)).isNull();
        assertThat(mapper.toModelCardStatus(null)).isNull();
    }

    @Test
    void convertsDoubleAmountsToBigDecimal() {
        assertThat(mapper.toBigDecimal(12.5)).isEqualByComparingTo("12.5");
        assertThat(mapper.toBigDecimal(null)).isNull();
    }
}
