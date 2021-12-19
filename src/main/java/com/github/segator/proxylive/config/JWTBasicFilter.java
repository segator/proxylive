package com.github.segator.proxylive.config;

import com.github.segator.proxylive.helper.JwtHelper;
import com.github.segator.proxylive.service.AuthenticationService;
import io.jsonwebtoken.*;
import org.apache.http.client.utils.URIBuilder;
import org.eclipse.jgit.util.Base64;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
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
            chain.doFilter(request, response);
        } catch (ExpiredJwtException | UnsupportedJwtException | MalformedJwtException e) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
            return;
        }catch (IOException  | ServletException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            return;
        }

    }

    private Claims validateToken(String jwtToken) {
        return Jwts.parser().setSigningKey(jwtConfiguration.getSecret().getBytes()).parseClaimsJws(jwtToken).getBody();
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

    private String getJWTToken(HttpServletRequest request) {
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
}
