package com.codeflow;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.*;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;

@Configuration
class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean SecurityFilterChain filterChain(HttpSecurity http,JwtFilter jwt) throws Exception {
  return http.csrf(c->c.disable()).cors(c->{}).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.requestMatchers("/api/auth/register","/api/auth/login","/api/auth/password-reset/request","/api/auth/password-reset/confirm","/error").permitAll().requestMatchers("/api/admin/**").hasRole("ADMIN").anyRequest().authenticated())
   .addFilterBefore(jwt,UsernamePasswordAuthenticationFilter.class).build();
 }
 @Bean CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.origin}") String origin){var c=new CorsConfiguration();c.setAllowedOrigins(List.of(origin));c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));c.setAllowedHeaders(List.of("Authorization","Content-Type"));var s=new UrlBasedCorsConfigurationSource();s.registerCorsConfiguration("/**",c);return s;}
}

@Component class JwtService {
 private final Key key;
 JwtService(@Value("${app.jwt.secret}") String secret){key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));}
 String create(AppUser u){return Jwts.builder().subject(u.email).claim("role",u.role).claim("uid",u.id).claim("ver",u.tokenVersion).issuedAt(new Date()).expiration(new Date(System.currentTimeMillis()+86400000)).signWith(key).compact();}
 Claims parse(String token){return Jwts.parser().verifyWith((javax.crypto.SecretKey)key).build().parseSignedClaims(token).getPayload();}
}

@Component class JwtFilter extends org.springframework.web.filter.OncePerRequestFilter {
 private final JwtService jwt; private final UserRepository users; JwtFilter(JwtService jwt,UserRepository users){this.jwt=jwt;this.users=users;}
 protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws java.io.IOException,ServletException{
  String h=req.getHeader("Authorization");if(h!=null&&h.startsWith("Bearer "))try{var claims=jwt.parse(h.substring(7));AppUser account=users.findByEmail(claims.getSubject()).orElse(null);Object claimVersion=claims.get("ver");int version=claimVersion instanceof Number n?n.intValue():0;Object claimId=claims.get("uid");if(account!=null&&account.id.equals(((Number)claimId).longValue())&&account.tokenVersion==version&&account.role.equals(claims.get("role"))){var auth=new UsernamePasswordAuthenticationToken(account.email,null,List.of(new SimpleGrantedAuthority("ROLE_"+account.role)));org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);}}catch(Exception ignored){}
  chain.doFilter(req,res);
 }
}
