//package transaction_service.transaction_service.controller;
//
//import org.apache.kafka.common.security.TestSecurityConfig;
//import org.junit.jupiter.api.DisplayName;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.context.annotation.Import;
//import org.springframework.http.MediaType;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.test.web.servlet.MockMvc;
//import org.springframework.web.bind.annotation.RestController;
//import transaction_service.transaction_service.dto.TransactionRequestDto;
//import transaction_service.transaction_service.dto.TransactionResponseDto;
//import transaction_service.transaction_service.exception.BadRequestException;
//import transaction_service.transaction_service.exception.InternalServerErrorException;
//import transaction_service.transaction_service.model.Status;
//import transaction_service.transaction_service.service.TransactionService;
//import org.junit.jupiter.api.Test;
//
//import java.math.BigDecimal;
//
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.when;
//import static org.mockito.ArgumentMatchers.any;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//
//
//@WebMvcTest(TransactionController.class)
//@Import(TestSecurityConfig.class)
//public class TransactionControllerTest {
//    @Autowired
//    private MockMvc mockMvc;
//
//    @MockBean
//    private TransactionService transactionService;
//
//    @Test
//    @DisplayName("transfer success -> 200")
//    void testTransferSuccess() throws Exception {
//        TransactionResponseDto responseDto = TransactionResponseDto.builder()
//                .fromAccountId(1L)
//                .toAccountId(2L)
//                .amount(BigDecimal.valueOf(100))
//                .status(Status.SUCCESS)
//                .build();
//
//        when(transactionService.transfer(any(TransactionRequestDto.class), eq(100L)))
//                .thenReturn(responseDto);
//        mockMvc.perform(post("/api/transaction/transfer")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content("{\"fromAccountId\":1,\"toAccountId\":2,\"amount\":100}")
//                .with(req -> {
//                    req.setUserPrincipal(() -> "100");
//                    return req;
//                }))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.status").value("SUCCESS"))
//                .andExpect(jsonPath("$.fromAccountId").value(1))
//                .andExpect(jsonPath("$.toAccountId").value(2))
//                .andExpect(jsonPath("$.amount").value(100));
//    }
//    @Test
//    @DisplayName("transfer BadRequest -> 400")
//    void testTransferBadRequest() throws Exception {
//        when(transactionService.transfer(any(TransactionRequestDto.class), eq(100L)))
//                .thenThrow(new BadRequestException("Not enough money"));
//
//        mockMvc.perform(post("/api/transactions/transfer")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content("{\"fromAccountId\":1,\"toAccountId\":2,\"amount\":100}")
//                        .header("X-User-Id", 100))
//                .andExpect(status().isBadRequest());
//    }
//
//    @Test
//    @DisplayName("transfer InternalServerErrorException -> 500")
//    void testTransferInternalServerError() throws Exception {
//        when(transactionService.transfer(any(TransactionRequestDto.class), eq(100L)))
//                .thenThrow(new InternalServerErrorException("Unexpected error"));
//
//        mockMvc.perform(post("/api/transactions/transfer")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content("{\"fromAccountId\":1,\"toAccountId\":2,\"amount\":100}")
//                        .header("X-User-Id", 100))
//                .andExpect(status().isInternalServerError());
//    }
//
//    @Test
//    @DisplayName("transfer invalid json -> 400")
//    void testInvalidJson() throws Exception {
//        mockMvc.perform(post("/api/transactions/transfer")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content("{\"fromAccountId\":1,\"toAccountId\":2,\"amount\":null}")
//                        .header("X-User-Id", 100))
//                .andExpect(status().isBadRequest());
//    }
//
//    @Test
//    @DisplayName("transfer missing header -> 400")
//    void testMissingHeader() throws Exception {
//        mockMvc.perform(post("/api/transactions/transfer")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content("{\"fromAccountId\":1,\"toAccountId\":2,\"amount\":100}"))
//                .andExpect(status().isBadRequest());
//    }
//
//}
