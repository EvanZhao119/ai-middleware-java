package org.estech.gateway.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecurityService {
    @Value("${gateway.jwt-secret}")
    private String secret;

    public boolean validateToken(String token) {
        try {
//            Key key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
//            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
