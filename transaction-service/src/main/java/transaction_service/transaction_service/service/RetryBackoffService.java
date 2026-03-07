package transaction_service.transaction_service.service;

import org.springframework.stereotype.Service;

@Service
public class RetryBackoffService {

    public void backoff(int attempt) throws InterruptedException {
        Thread.sleep(100L * attempt);
    }
}

