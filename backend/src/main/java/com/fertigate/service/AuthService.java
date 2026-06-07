package com.fertigate.service;

import com.fertigate.dto.*;
import com.fertigate.entity.SysPermission;
import com.fertigate.entity.SysRole;
import com.fertigate.entity.SysUser;
import com.fertigate.repository.SysPermissionRepository;
import com.fertigate.repository.SysRoleRepository;
import com.fertigate.repository.SysUserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysPermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final HttpServletRequest request;

    @Value("${jwt.secret:fertigate-secret-key-2024-very-long-string-for-security}")
    private String jwtSecret;

    @Value("${jwt.expiration-hours:24}")
    private int jwtExpirationHours;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional
    public void initDefaultUsersAndRoles() {
        initRolesAndPermissions();

        if (!userRepository.existsByUsername("admin")) {
            SysUser admin = new SysUser();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRealName("系统管理员");
            admin.setEmail("admin@fertigate.com");
            admin.setPhone("13800138000");
            admin.setEnabled(true);

            SysRole adminRole = roleRepository.findByRoleCode(SysUser.RoleEnum.ADMIN.getCode())
                    .orElseThrow(() -> new RuntimeException("管理员角色不存在"));
            admin.setRoles(new HashSet<>(Collections.singletonList(adminRole)));

            userRepository.save(admin);
        }

        if (!userRepository.existsByUsername("operator")) {
            SysUser operator = new SysUser();
            operator.setUsername("operator");
            operator.setPassword(passwordEncoder.encode("operator123"));
            operator.setRealName("系统操作员");
            operator.setEmail("operator@fertigate.com");
            operator.setPhone("13800138001");
            operator.setEnabled(true);

            SysRole operatorRole = roleRepository.findByRoleCode(SysUser.RoleEnum.OPERATOR.getCode())
                    .orElseThrow(() -> new RuntimeException("操作员角色不存在"));
            operator.setRoles(new HashSet<>(Collections.singletonList(operatorRole)));

            userRepository.save(operator);
        }

        if (!userRepository.existsByUsername("viewer")) {
            SysUser viewer = new SysUser();
            viewer.setUsername("viewer");
            viewer.setPassword(passwordEncoder.encode("viewer123"));
            viewer.setRealName("只读用户");
            viewer.setEmail("viewer@fertigate.com");
            viewer.setPhone("13800138002");
            viewer.setEnabled(true);

            SysRole viewerRole = roleRepository.findByRoleCode(SysUser.RoleEnum.VIEWER.getCode())
                    .orElseThrow(() -> new RuntimeException("只读用户角色不存在"));
            viewer.setRoles(new HashSet<>(Collections.singletonList(viewerRole)));

            userRepository.save(viewer);
        }
    }

    @Transactional
    public void initRolesAndPermissions() {
        Map<String, String[]> allPermissions = new LinkedHashMap<>();
        allPermissions.put("user:view", new String[]{"用户查看", "/user/**", "GET"});
        allPermissions.put("user:manage", new String[]{"用户管理", "/user/**", "POST,PUT,DELETE"});
        allPermissions.put("role:view", new String[]{"角色查看", "/user/roles", "GET"});
        allPermissions.put("log:view", new String[]{"日志查看", "/operation-log/**", "GET,DELETE"});
        allPermissions.put("auth:manage", new String[]{"认证管理", "/auth/logout,/auth/userinfo,/auth/permissions", "POST,GET"});
        allPermissions.put("zone:view", new String[]{"灌区查看", "/zone/**", "GET"});
        allPermissions.put("zone:manage", new String[]{"灌区管理", "/zone/**", "POST,PUT,DELETE"});
        allPermissions.put("device:view", new String[]{"设备查看", "/device/**", "GET"});
        allPermissions.put("device:manage", new String[]{"设备管理", "/device/**", "POST,PUT,DELETE"});
        allPermissions.put("crop:view", new String[]{"作物查看", "/crop/**,/growth-stage/**", "GET"});
        allPermissions.put("crop:manage", new String[]{"作物管理", "/crop/**,/growth-stage/**", "POST,PUT,DELETE"});
        allPermissions.put("threshold:view", new String[]{"阈值查看", "/threshold/**", "GET"});
        allPermissions.put("threshold:manage", new String[]{"阈值管理", "/threshold/**", "POST,PUT,DELETE"});
        allPermissions.put("rotation:view", new String[]{"轮灌查看", "/rotation/**", "GET"});
        allPermissions.put("rotation:manage", new String[]{"轮灌管理", "/rotation/**", "POST,PUT,DELETE"});
        allPermissions.put("irrigation:view", new String[]{"灌溉查看", "/irrigation/**", "GET"});
        allPermissions.put("irrigation:control", new String[]{"灌溉控制", "/irrigation/**,/manual/**", "POST,PUT"});
        allPermissions.put("alert:view", new String[]{"告警查看", "/alert/**", "GET"});
        allPermissions.put("alert:acknowledge", new String[]{"告警确认", "/alert/**/acknowledge,/alert/acknowledge-all", "POST,PUT"});
        allPermissions.put("record:view", new String[]{"记录查看", "/fertigation/**,/irrigation/**", "GET"});
        allPermissions.put("record:export", new String[]{"记录导出", "/fertigation/**/export", "GET"});
        allPermissions.put("record:manage", new String[]{"记录管理", "/fertigation/**,/irrigation/**", "POST,PUT,DELETE"});
        allPermissions.put("monitor:view", new String[]{"监控查看", "/monitor/**,/dashboard/**", "GET"});
        allPermissions.put("sensor:view", new String[]{"传感器查看", "/sensor/**", "GET"});
        allPermissions.put("pump:manage", new String[]{"水泵管理", "/pump/**", "POST,PUT,DELETE"});
        allPermissions.put("pump:view", new String[]{"水泵查看", "/pump/**", "GET"});

        Map<String, SysPermission> permissionMap = new HashMap<>();
        for (Map.Entry<String, String[]> entry : allPermissions.entrySet()) {
            String code = entry.getKey();
            String[] info = entry.getValue();
            SysPermission perm = permissionRepository.findByPermissionCode(code)
                    .orElse(new SysPermission());
            perm.setPermissionCode(code);
            perm.setPermissionName(info[0]);
            perm.setResourceUrl(info[1]);
            perm.setResourceMethod(info[2]);
            perm.setDescription(info[0]);
            perm = permissionRepository.save(perm);
            permissionMap.put(code, perm);
        }

        String[][] roles = {
                {SysUser.RoleEnum.ADMIN.getCode(), "管理员", "拥有所有权限"},
                {SysUser.RoleEnum.OPERATOR.getCode(), "操作员", "可操作设备、修改策略"},
                {SysUser.RoleEnum.VIEWER.getCode(), "只读用户", "仅可查看数据"}
        };

        for (String[] roleInfo : roles) {
            String roleCode = roleInfo[0];
            SysRole role = roleRepository.findByRoleCode(roleCode)
                    .orElse(new SysRole());
            role.setRoleCode(roleCode);
            role.setRoleName(roleInfo[1]);
            role.setDescription(roleInfo[2]);

            Set<SysPermission> perms = new HashSet<>();
            switch (roleCode) {
                case "admin":
                    perms.addAll(permissionMap.values());
                    break;
                case "operator":
                    permissionMap.entrySet().stream()
                            .filter(e -> !e.getKey().startsWith("user:") && !e.getKey().startsWith("role:"))
                            .forEach(e -> perms.add(e.getValue()));
                    break;
                case "viewer":
                    permissionMap.entrySet().stream()
                            .filter(e -> e.getKey().endsWith(":view") 
                                    || e.getKey().equals("auth:manage")
                                    || e.getKey().equals("monitor:view"))
                            .forEach(e -> perms.add(e.getValue()));
                    break;
            }
            role.setPermissions(perms);
            roleRepository.save(role);
        }
    }

    public LoginResponseDTO login(LoginRequestDTO request) {
        SysUser user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        if (!user.getEnabled()) {
            throw new RuntimeException("账户已被禁用");
        }

        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(getClientIp());
        userRepository.save(user);

        Set<String> roles = user.getRoles().stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toSet());

        Set<String> permissions = userRepository.findPermissionCodesByUserId(user.getId())
                .stream().collect(Collectors.toSet());

        String token = generateToken(user);

        LoginResponseDTO response = new LoginResponseDTO();
        response.setToken(token);
        response.setTokenType("Bearer");
        response.setExpiresIn((long) jwtExpirationHours * 3600);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setRealName(user.getRealName());
        response.setRoles(roles);
        response.setPermissions(permissions);

        return response;
    }

    private String generateToken(SysUser user) {
        long expirationMillis = (long) jwtExpirationHours * 3600 * 1000;
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMillis);

        Set<String> roles = user.getRoles().stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toSet());

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    public Optional<SysUser> getCurrentUser() {
        String token = extractToken();
        if (token == null) {
            return Optional.empty();
        }
        Claims claims = validateToken(token);
        if (claims == null) {
            return Optional.empty();
        }
        try {
            UUID userId = UUID.fromString(claims.getSubject());
            return userRepository.findById(userId);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public UUID getCurrentUserId() {
        return getCurrentUser().map(SysUser::getId).orElse(null);
    }

    public String getCurrentUsername() {
        return getCurrentUser().map(SysUser::getUsername).orElse("system");
    }

    private String extractToken() {
        try {
            String bearerToken = request.getHeader("Authorization");
            if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
                return bearerToken.substring(7);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String getClientIp() {
        try {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public boolean hasPermission(String permissionCode) {
        return getCurrentUser()
                .map(user -> userRepository.findPermissionCodesByUserId(user.getId()))
                .map(perms -> perms.contains(permissionCode))
                .orElse(false);
    }

    public List<String> getCurrentUserPermissions() {
        return getCurrentUser()
                .map(user -> new java.util.ArrayList<>(userRepository.findPermissionCodesByUserId(user.getId())))
                .orElse(new java.util.ArrayList<>());
    }

    public boolean hasAnyRole(String... roleCodes) {
        return getCurrentUser()
                .map(user -> user.getRoles().stream()
                        .anyMatch(r -> Arrays.asList(roleCodes).contains(r.getRoleCode())))
                .orElse(false);
    }

    public List<SysUserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(SysUserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public SysUserDTO getUserById(UUID id) {
        return userRepository.findById(id)
                .map(SysUserDTO::fromEntity)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    @Transactional
    public SysUserDTO createUser(CreateUserDTO dto) {
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);

        if (dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
            Set<SysRole> roles = new HashSet<>(roleRepository.findAllById(dto.getRoleIds()));
            user.setRoles(roles);
        }

        user = userRepository.save(user);
        return SysUserDTO.fromEntity(user);
    }

    @Transactional
    public SysUserDTO updateUser(UUID id, UpdateUserDTO dto) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (dto.getRealName() != null) {
            user.setRealName(dto.getRealName());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }
        if (dto.getEnabled() != null) {
            user.setEnabled(dto.getEnabled());
        }
        if (dto.getRoleIds() != null) {
            Set<SysRole> roles = new HashSet<>(roleRepository.findAllById(dto.getRoleIds()));
            user.setRoles(roles);
        }

        user = userRepository.save(user);
        return SysUserDTO.fromEntity(user);
    }

    @Transactional
    public void deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("用户不存在");
        }
        userRepository.deleteById(id);
    }

    @Transactional
    public void resetPassword(UUID id, String newPassword) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public List<SysRoleDTO> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(SysRoleDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
