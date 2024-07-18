package com.github.segator.proxylive.config;

import com.github.segator.proxylive.helper.JwtHelper;
import com.github.segator.proxylive.service.AuthenticationService;
import io.jsonwebtoken.*;

import org.apache.hc.core5.net.URIBuilder;
import org.eclipse.jgit.util.Base64;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class JWTBasicFilter  extends OncePerRequestFilter {

    private final String HEADER = "Authorization";
    private final String PREFIXBearer = "Bearer ";
    private final String PREFIXBasic = "Basic ";
    private final String PARAM_OLD_USERNAME="user";
    private final String PARAM_OLD_PASSWORD="pass";
    private final JwtConfiguration jwtConfiguration;
    private final AuthenticationService authenticationService;
    private final JwtHelper jwtHelper;

    public JWTBasicFilter(JwtConfiguration jwtConfiguration, AuthenticationService authenticationService, JwtHelper jwtHelper) {
        this.jwtConfiguration = jwtConfiguration;
        this.authenticationService = authenticationService;
        this.jwtHelper = jwtHelper;
    }

    private Claims validateToken(String jwtToken) {
        SecretKey secretKey = new SecretKeySpec(jwtConfiguration.getSecret().getBytes(),"HmacSHA512");
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(jwtToken).getPayload();
    }

    /**
     * Metodo para autenticarnos dentro del flujo de Spring
     *
     * @param claims
     */
    private void setUpSpringAuthentication(Claims claims,String jwtToken) {
        @SuppressWarnings("unchecked")
        List<String> authorities = (List) claims.get("authorities");

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(claims.getSubject(), jwtToken,
                authorities.stream().map(role->new SimpleGrantedAuthority("ROLE_"+role)).collect(Collectors.toList()));
        SecurityContextHolder.getContext().setAuthentication(auth);

    }

    private String getJWTToken(jakarta.servlet.http.HttpServletRequest request) {
        String parameterToken = request.getParameter("token");
        if(parameterToken!=null){
            return parameterToken;
        }

        String authenticationHeader = request.getHeader(HEADER);
        if (authenticationHeader == null) {
            return null;
        }

        if(authenticationHeader.startsWith(PREFIXBearer)){
            return authenticationHeader.replace(PREFIXBearer,"");
        }else if(authenticationHeader.startsWith(PREFIXBasic)){
            String basicB64=authenticationHeader.replace(PREFIXBasic,"");
            String basicSecret = new String(Base64.decode(basicB64));
            return basicSecret.split(":")[1];
        }
        return null;
    }

    @Override
    protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, jakarta.servlet.FilterChain filterChain) throws jakarta.servlet.ServletException, IOException {
        try {
            if(!request.getRequestURI().startsWith("/login")) {
                String jwtToken = getJWTToken(request);
                if (jwtToken != null) {

                    Claims claims = validateToken(jwtToken);
                    if (claims.get("authorities") != null) {
                        setUpSpringAuthentication(claims, jwtToken);
                    } else {
                        SecurityContextHolder.clearContext();
                    }
                    filterChain.doFilter(request,response);
                } else {
                    //Old Authentication system compatibility, redirecting user and pass to token on the fly with 301 redirect.
                    String username = request.getParameter(PARAM_OLD_USERNAME);
                    if (username == null) {
                        username = "anonymous";
                    }
                    String password = request.getParameter(PARAM_OLD_PASSWORD);
                    if (authenticationService.loginUser(username, password)) {
                        jwtToken = jwtHelper.createJwtForClaims(username, authenticationService.getUserRoles(username));
                        Map<String, String[]> parameters = new HashMap<>(request.getParameterMap());
                        parameters.remove(PARAM_OLD_USERNAME);
                        parameters.remove(PARAM_OLD_PASSWORD);
                        parameters.put("token", new String[]{jwtToken});
                        response.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);
                        URIBuilder uriBuilder = new URIBuilder(request.getRequestURI());
                        for (Map.Entry<String, String[]> kv : parameters.entrySet()) {
                            for (String value : kv.getValue()) {
                                uriBuilder.addParameter(kv.getKey(), value);
                            }
                        }
                        response.setHeader("Location", uriBuilder.toString());
                        return;
                    }
                    SecurityContextHolder.clearContext();
                }
            }
        } catch (ExpiredJwtException | UnsupportedJwtException | MalformedJwtException e) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        }catch (jakarta.servlet.ServletException e){
            throw e;
        }catch (IOException e) {
            throw new jakarta.servlet.ServletException(e);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
