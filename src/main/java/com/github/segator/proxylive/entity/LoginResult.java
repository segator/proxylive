package com.github.segator.proxylive.entity;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class LoginResult {
    @NonNull
    private String username;
    @NonNull
    private String jwt;
}
