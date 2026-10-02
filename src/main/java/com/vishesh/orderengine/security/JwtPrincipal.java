package com.vishesh.orderengine.security;

import java.util.Set;

public record JwtPrincipal(String username, Set<String> roles) {

}
