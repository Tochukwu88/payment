package dto;

import java.math.BigDecimal;

public record TransferDto(String sourceAccountRef,
                          String destinationAccountRef,
                          BigDecimal amount,
                          String reference,
                          String description) {
}
