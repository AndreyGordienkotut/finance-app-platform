package transaction_service.transaction_service.service;

import core.core.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import transaction_service.transaction_service.event.TransactionCompletedEvent;
import transaction_service.transaction_service.model.Status;
import transaction_service.transaction_service.model.Transaction;
import transaction_service.transaction_service.model.TransactionStep;
import transaction_service.transaction_service.repository.TransactionRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionStateService {
    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;
    @Transactional
    public void updateStep(Long txId, TransactionStep step) {
        Transaction tx = transactionRepository.findById(txId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));
        tx.setStep(step);
        tx.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(tx);
        log.info("TX {} step updated to {}", txId, step);
    }
    @Transactional
    public void updateStatus(Long id, Status status, String error) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));
        log.info("TX {} updated status: {}", tx.getId(), status);
        tx.setStatus(status);
        tx.setErrorMessage(error);
        tx.setUpdatedAt(LocalDateTime.now());

        transactionRepository.save(tx);
        if (status == Status.COMPLETED || status == Status.FAILED) {
            eventPublisher.publishEvent(new TransactionCompletedEvent(tx.getUserId()));
        }

    }
}
