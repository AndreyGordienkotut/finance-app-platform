package transaction_service.transaction_service.mapper;

import org.mapstruct.Mapper;
import transaction_service.transaction_service.dto.TransactionResponseDto;
import transaction_service.transaction_service.model.Transaction;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TransactionMapper {
    TransactionResponseDto toDto(Transaction transaction);
    List<TransactionResponseDto> toDtoList(List<Transaction> transactions);
}
