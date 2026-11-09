package br.com.danielcardoso.thindesk.security;

import java.io.IOException;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

	public static final String USERNAME_PARAMETER = "email";

	private final LoginAttemptService loginAttemptService;

	public LoginFailureHandler(LoginAttemptService loginAttemptService) {
		super("/login?error");
		this.loginAttemptService = loginAttemptService;
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException, ServletException {
		String email = request.getParameter(USERNAME_PARAMETER);
		if (exception instanceof BadCredentialsException && email != null) {
			loginAttemptService.recordFailure(email);
		}
		super.onAuthenticationFailure(request, response, exception);
	}
}
