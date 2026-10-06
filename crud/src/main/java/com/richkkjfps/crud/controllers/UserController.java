package com.richkkjfps.crud.controllers;

import com.richkkjfps.crud.config.AccessContext;
import com.richkkjfps.crud.config.AccountWriter;
import com.richkkjfps.crud.dtos.UserDto;
import com.richkkjfps.crud.model.Users;
import com.richkkjfps.crud.repositories.UserRepository;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/users")
public class UserController {

    private static final String OWNER_ROLE = "OWNER";
    private static final Set<String> TEAM_ROLES = Set.of("ADMIN", "USER");

    private final UserRepository userRepository;
    private final AccessContext access;
    private final AccountWriter accountWriter;

    public UserController(
        UserRepository userRepository,
        AccessContext access,
        AccountWriter accountWriter
    ) {
        this.userRepository = userRepository;
        this.access = access;
        this.accountWriter = accountWriter;
    }

    @GetMapping
    public List<Users> getAll() {
        Users administrator = access.administrator();
        return userRepository.findAllByTenantId(administrator.getTenantId(), Sort.by("name"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Users create(@RequestBody UserDto request) {
        Users administrator = access.administrator();
        validateTeamRole(request.role());
        Users account = new Users();
        account.setTenantId(administrator.getTenantId());
        return accountWriter.save(account, request, true);
    }

    @PutMapping("/{id}")
    @Transactional
    public Users update(@PathVariable Integer id, @RequestBody UserDto request) {
        Users administrator = access.administrator();
        userRepository.lockAdministrators(administrator.getTenantId());
        // Confere o acesso novamente depois de obter o bloqueio da empresa.
        administrator = access.administrator();
        Users account = findCompanyAccount(id, administrator.getTenantId());
        validateAccountUpdate(administrator, account, request);
        return accountWriter.save(account, request, false);
    }

    private Users findCompanyAccount(Integer accountId, Integer tenantId) {
        return userRepository
            .findByIdAndTenantId(accountId, tenantId)
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado.")
            );
    }

    private void validateAccountUpdate(Users administrator, Users account, UserDto request) {
        boolean ownAccount = Objects.equals(account.getId(), administrator.getId());
        boolean ownerAccount = OWNER_ROLE.equals(account.getRole());
        if (ownerAccount && !ownAccount) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Este acesso pertence ao proprietário."
            );
        }
        boolean changingOwnRole = ownAccount && !account.getRole().equals(request.role());
        boolean disablingOwnAccess = ownAccount && Boolean.FALSE.equals(request.active());
        if (changingOwnRole || disablingOwnAccess) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Você não pode remover seu próprio perfil ou acesso."
            );
        }
        boolean keepingOwnerRole = ownAccount && ownerAccount && OWNER_ROLE.equals(request.role());
        if (!keepingOwnerRole) {
            validateTeamRole(request.role());
        }
    }

    private void validateTeamRole(String role) {
        if (!TEAM_ROLES.contains(Objects.toString(role, ""))) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Escolha Usuário ou Administrador."
            );
        }
    }
}
