package com.fertigate.dto;

import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class LoginResponseDTO {

    private String token;
    private String tokenType = "Bearer";
    private Long expiresIn;
    private UUID userId;
    private String username;
    private String realName;
    private Set<String> roles;
    private Set<String> permissions;
}
