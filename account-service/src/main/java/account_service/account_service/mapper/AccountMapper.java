package account_service.account_service.mapper;

import account_service.account_service.model.Account;
import core.core.dto.AccountResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    @Mapping(source = "statusAccount", target = "status")
    AccountResponseDto toDto(Account account);
}