package br.com.danielcardoso.thindesk.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import br.com.danielcardoso.thindesk.common.validation.StrongPasswordValidator;
import br.com.danielcardoso.thindesk.config.AppProperties;
import br.com.danielcardoso.thindesk.user.domain.Role;
import br.com.danielcardoso.thindesk.user.service.UserService;

@Component
public class AdminSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

	private final UserService userService;
	private final AppProperties.Admin admin;

	public AdminSeeder(UserService userService, AppProperties properties) {
		this.userService = userService;
		this.admin = properties.admin();
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!StringUtils.hasText(admin.email()) || !StringUtils.hasText(admin.password())) {
			log.warn("ADMIN_EMAIL/ADMIN_PASSWORD não definidos: nenhum administrador inicial será criado");
			return;
		}
		if (!new StrongPasswordValidator().isValid(admin.password(), null)) {
			log.error("ADMIN_PASSWORD fraca: use 8+ caracteres com maiúscula, minúscula, número e símbolo");
			return;
		}
		String email = UserService.normalizeEmail(admin.email());
		if (userService.createAdminIfMissing(admin.name(), email, admin.password())) {
			log.info("Administrador inicial criado: {}", email);
		}
		else if (userService.getByEmail(email).getRole() != Role.ADMIN) {
			log.warn("ADMIN_EMAIL {} já está cadastrado sem perfil ADMIN; promova-o pela gestão de usuários", email);
		}
	}
}
