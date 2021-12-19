package com.github.segator.proxylive.controller;

import com.github.segator.proxylive.entity.LoginResult;
import com.github.segator.proxylive.helper.JwtHelper;
import com.github.segator.proxylive.service.AuthenticationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuthController {

    private final JwtHelper jwtHelper;
    private final AuthenticationService authenticationService;

    public AuthController(JwtHelper jwtHelper, AuthenticationService authenticationService) {
        this.jwtHelper = jwtHelper;
        this.authenticationService = authenticationService;
    }

    @PostMapping(path = "/login", consumes = { MediaType.APPLICATION_FORM_URLENCODED_VALUE })
    public LoginResult login(
            @RequestParam String username,
            @RequestParam String password) throws Exception {

        if (authenticationService.loginUser(username,password)) {
            String jwt = jwtHelper.createJwtForClaims(username, authenticationService.getUserRoles(username));
            return new LoginResult(username,jwt);
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
    }
}