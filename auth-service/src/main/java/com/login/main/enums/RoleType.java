package com.login.main.enums;

public enum RoleType {
    USER,
    ADMIN;

    public String getRoleName() {
        return "ROLE_" + this.name();
    }
}
