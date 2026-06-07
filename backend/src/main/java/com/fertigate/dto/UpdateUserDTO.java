package com.fertigate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class UpdateUserDTO {

    @Size(max = 50, message = "真实姓名长度不能超过50个字符")
    private String realName;

    private String email;
    private String phone;
    private Boolean enabled;
    private Set<UUID> roleIds;
}
