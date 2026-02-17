package com.ampliapps.amplisync;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.JWTVerifier;

import com.auth0.jwt.exceptions.JWTVerificationException;


public class JwtTokenValidator {

    private final JWTVerifier verifier;

    public JwtTokenValidator() {
        // MUST be identical to Django's SECRET_KEY
        String secret = System.getenv("JWT_SHARED_SECRET");
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException("JWT_SHARED_SECRET env var is not set");
        }
        Algorithm algorithm = Algorithm.HMAC256(secret);
        verifier = JWT.require(algorithm).build();
    }

    private DecodedJWT verify(String rawToken) throws JWTVerificationException {
        return verifier.verify(rawToken);
    }

    public boolean isTokenValid(String authorizationHeader) {
        try {
            String token = authorizationHeader.substring("Bearer ".length());
            DecodedJWT jwt = this.verify(token);

            String tokenType = jwt.getClaim("token_type").asString();
            if (!"access".equals(tokenType)) {
                throw new JWTVerificationException("Invalid token_type");
            }
            return true;
        } catch (JWTVerificationException e) {
            Logs.write(Logs.Level.DEBUG, e.getMessage());
            return false;
        }
    }

    public String getUserId(String authorizationHeader) {
        try {
            String token = authorizationHeader.substring("Bearer ".length());
            DecodedJWT jwt = this.verify(token);
            return jwt.getClaim("user_id").asString();
        } catch (Exception e) {
            Logs.write(Logs.Level.DEBUG, e.getMessage());
            return null;
        }
    }
}
