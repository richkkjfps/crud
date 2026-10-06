package com.richkkjfps.crud.config;

import com.richkkjfps.crud.model.*;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

public class AccountPrincipal extends User {

    private final Integer accountId;
    private final Integer tenantId;
    private final int authVersion;
    private final int tenantVersion;

    public AccountPrincipal(Users account, Tenant tenant) {
        super(
            account.getEmail(),
            account.getSenha(),
            account.isActive() && tenant.isActive(),
            true,
            true,
            true,
            AuthorityUtils.createAuthorityList("ROLE_" + account.getRole())
        );
        accountId = account.getId();
        tenantId = account.getTenantId();
        authVersion = account.getAuthVersion();
        tenantVersion = tenant.getAuthVersion();
    }

    public Integer getAccountId() {
        return accountId;
    }

    public Integer getTenantId() {
        return tenantId;
    }

    public int getAuthVersion() {
        return authVersion;
    }

    public int getTenantVersion() {
        return tenantVersion;
    }
}
