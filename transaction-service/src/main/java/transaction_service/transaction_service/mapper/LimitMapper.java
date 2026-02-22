package transaction_service.transaction_service.mapper;

import org.mapstruct.Mapper;
import transaction_service.transaction_service.dto.LimitResponseDto;
import transaction_service.transaction_service.model.TransactionLimit;

@Mapper(componentModel = "spring")
public interface LimitMapper {
    LimitResponseDto toDto(TransactionLimit limit);
}