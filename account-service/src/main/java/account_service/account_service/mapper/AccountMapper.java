package account_service.account_service.mapper;

import account_service.account_service.model.Account;
import core.core.dto.AccountResponseDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AccountMapper {
    AccountResponseDto toDto(Account account);
}