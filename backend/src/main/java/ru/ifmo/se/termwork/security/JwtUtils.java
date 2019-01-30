package ru.ifmo.se.termwork.security;

import io.jsonwebtoken.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtUtils {

    private final static String EMAIL_CLAIM_NAME = "email";

    private final static String ROLES_CLAIM_NAME = "roles";

    private static SecretKey secretKey;

    private static int validityInMinutes = 15;

    @PostConstruct
    private void init(){
        //ToDo: replace with spring crypto
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(new SecureRandom());
            secretKey = keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public UserDetails getAuthentication(String token){
        try{
            Jws<Claims> claims = Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
            String email = claims.getBody().get(EMAIL_CLAIM_NAME, String.class);
            List<String> strRoles = (List<String>) claims.getBody().get(ROLES_CLAIM_NAME);
            List<SimpleGrantedAuthority> roles =
                    strRoles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
            return new User(email, "password", roles);
        } catch (JwtException e){
            throw new BadCredentialsException("Token is invalid");
        }
    }


    public String getToken(String mail, List<String> roles) {
        Date expirationDate = Date.from(Instant.now().plus(validityInMinutes, ChronoUnit.MINUTES));
        return Jwts.builder()
                .setIssuedAt(new Date())
                .setExpiration(expirationDate)
                .claim(EMAIL_CLAIM_NAME, mail)
                .claim(ROLES_CLAIM_NAME, roles)
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }

}
