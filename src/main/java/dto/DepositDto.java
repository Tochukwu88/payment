package dto;

import java.math.BigDecimal;

public record DepositDto(String externalAccountRef,
                         String userWalletRef,
                         BigDecimal amount,
                         String reference,
                         String description) {
}
