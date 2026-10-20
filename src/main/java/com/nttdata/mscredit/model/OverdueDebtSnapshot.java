package com.nttdata.mscredit.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Customers currently known to have overdue debt, as last announced on the overdue events topic. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("overdue_debt_snapshots")
public class OverdueDebtSnapshot {

    @Id
    private String customerId;
    private Instant detectedAt;
}
