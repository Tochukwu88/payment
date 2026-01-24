package com.ledger.pay.controller;

import com.ledger.pay.domain.Transaction;
import com.ledger.pay.service.LedgerService;
import dto.DepositDto;
import dto.TransferDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/")
public class LedgerController {
    private  final LedgerService ledgerService;
    @PostMapping("/transfer")
    public ResponseEntity<Transaction> transfer( @RequestBody TransferDto request){
       Transaction transaction = ledgerService.transfer(request.sourceAccountRef(),
                request.destinationAccountRef(),request.amount(),request.reference(),request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(transaction);

    }
    @PostMapping("/deposit")
    public ResponseEntity<Transaction> deposit(
            @RequestBody DepositDto request
    ) {

        Transaction transaction = ledgerService.deposit(request.externalAccountRef(), request.userWalletRef(), request.amount(), request.reference(), "API deposit");
        return ResponseEntity.status(HttpStatus.CREATED).body(transaction);

    }
}
