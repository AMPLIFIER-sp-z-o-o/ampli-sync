package com.ampliapps.amplisync;
import jakarta.annotation.Priority;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;


@Priority(value = 2)
public class AuthenticationService {
	public boolean authenticate(HttpServletRequest httpServletRequest) {

		try {
            String authCredentials = httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION);
            JwtTokenValidator jwtTokenValidator = new JwtTokenValidator();
            return jwtTokenValidator.isTokenValid(authCredentials);
		} catch (Exception e) {
			Logs.write(Logs.Level.ERROR, e.getMessage());
			return false;
		}
	}
}