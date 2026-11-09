package br.com.danielcardoso.thindesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Metadados da documentação OpenAPI exibida em {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiConfig {

	static final String BASIC = "basicAuth";
	static final String SESSION = "sessionCookie";

	@Bean
	OpenAPI thindeskOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Thindesk Dashboard API")
						.version("v1")
						.description("""
								API do painel administrativo de chamados técnicos do Thindesk.

								**Autenticação:** use HTTP Basic com um usuário de perfil MANAGER ou ADMIN \
								(botão *Authorize*) ou a sessão do dashboard já logado. Requisições com HTTP Basic \
								dispensam o token CSRF.

								**Datas:** filtros `from`/`to` no formato `yyyy-MM-dd`, interpretados no fuso \
								America/Sao_Paulo e inclusivos. Datas das respostas em ISO-8601 (UTC).

								**Erros:** sempre no formato Problem Details (RFC 9457), com `code`, `timestamp` e, \
								em erros de validação, a lista `errors` por campo.""")
						.contact(new Contact().name("Daniel Cardoso Leão")))
				.components(new Components()
						.addSecuritySchemes(BASIC, new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic"))
						.addSecuritySchemes(SESSION, new SecurityScheme().type(SecurityScheme.Type.APIKEY)
								.in(SecurityScheme.In.COOKIE).name(MongoSessionConfig.COOKIE_NAME)))
				.addSecurityItem(new SecurityRequirement().addList(BASIC))
				.addSecurityItem(new SecurityRequirement().addList(SESSION));
	}
}
