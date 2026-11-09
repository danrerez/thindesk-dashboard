package br.com.danielcardoso.thindesk.bootstrap;

import java.text.Normalizer;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import br.com.danielcardoso.thindesk.config.AppProperties;
import br.com.danielcardoso.thindesk.ticket.domain.Department;
import br.com.danielcardoso.thindesk.ticket.domain.TechnicalNote;
import br.com.danielcardoso.thindesk.ticket.domain.Technician;
import br.com.danielcardoso.thindesk.ticket.domain.TechnicianLevel;
import br.com.danielcardoso.thindesk.ticket.domain.Ticket;
import br.com.danielcardoso.thindesk.ticket.domain.TicketPriority;
import br.com.danielcardoso.thindesk.ticket.domain.TicketStatus;
import br.com.danielcardoso.thindesk.ticket.domain.TicketType;
import br.com.danielcardoso.thindesk.ticket.repository.TechnicianRepository;
import br.com.danielcardoso.thindesk.ticket.repository.TicketRepository;

/**
 * Gera uma massa de chamados de demonstração com comportamento realista: mais volume em dias úteis, prazos que
 * variam por prioridade, chamados ainda em aberto nos dias mais recentes e alguns fora do SLA. Usa semente fixa,
 * então a mesma configuração gera sempre o mesmo cenário (relativo à data em que roda). As transições passam pelas
 * mesmas regras de domínio usadas pela API.
 */
@Component
@Order(10)
public class TicketDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(TicketDataSeeder.class);
	private static final String AUTO_CLOSE = "Sistema (fechamento automático)";
	private static final long SEED = 20261109L;
	private static final String FORMER_TECHNICIAN = "Eduardo Nunes";

	private final TicketRepository tickets;
	private final TechnicianRepository technicians;
	private final AppProperties.Seed settings;
	private final Clock clock;
	private final ZoneId zone;

	public TicketDataSeeder(TicketRepository tickets, TechnicianRepository technicians, AppProperties properties,
			Clock clock) {
		this.tickets = tickets;
		this.technicians = technicians;
		this.settings = properties.seed();
		this.clock = clock;
		this.zone = properties.dashboard().zone();
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!settings.enabled()) {
			return;
		}
		if (settings.reset()) {
			log.warn("app.seed.reset=true: apagando chamados e técnicos para gerar a massa de demonstração de novo");
			tickets.deleteAll();
			technicians.deleteAll();
		}
		else if (tickets.count() > 0) {
			return;
		}
		boolean newTeam = technicians.count() == 0;
		List<Technician> team = newTeam ? technicians.saveAll(team()) : technicians.findAll();
		List<Ticket> generated = generate(team, settings.tickets(), settings.days());
		tickets.saveAll(generated);
		if (newTeam) {
			// Técnico que saiu da equipe: atendeu chamados antigos e hoje está inativo.
			team.stream().filter(t -> FORMER_TECHNICIAN.equals(t.getName())).forEach(t -> {
				t.setActive(false);
				technicians.save(t);
			});
		}
		tickets.resetProtocolSequence(generated.size());
		log.info("Massa de demonstração criada: {} chamados em {} dias e {} técnicos", generated.size(),
				settings.days(), team.size());
	}

	List<Technician> team() {
		List<Technician> team = new ArrayList<>();
		team.add(technician("Ana Beatriz Souza", TechnicianLevel.N1, true));
		team.add(technician("Lucas Ferreira", TechnicianLevel.N1, true));
		team.add(technician("Juliana Martins", TechnicianLevel.N1, true));
		team.add(technician(FORMER_TECHNICIAN, TechnicianLevel.N1, true));
		team.add(technician("Rafael Oliveira", TechnicianLevel.N2, true));
		team.add(technician("Camila Rocha", TechnicianLevel.N2, true));
		team.add(technician("Thiago Almeida", TechnicianLevel.N2, true));
		team.add(technician("Marcos Vinícius Lima", TechnicianLevel.N3, true));
		team.add(technician("Patrícia Gomes", TechnicianLevel.N3, true));
		return team;
	}

	List<Ticket> generate(List<Technician> team, int count, int days) {
		Random random = new Random(SEED);
		Instant now = clock.instant();
		LocalDate today = now.atZone(zone).toLocalDate();
		List<Ticket> result = new ArrayList<>();

		while (result.size() < count) {
			LocalDate day = today.minusDays(random.nextInt(days));
			boolean weekend = day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
			if (weekend && random.nextDouble() > 0.15) {
				continue;
			}
			ZonedDateTime openedAt = day.atTime(8 + random.nextInt(10), random.nextInt(60)).atZone(zone);
			if (openedAt.toInstant().isAfter(now)) {
				continue;
			}
			result.add(build(random, team, openedAt.toInstant(), now));
		}

		result.sort(Comparator.comparing(Ticket::getOpenedAt));
		for (int i = 0; i < result.size(); i++) {
			Ticket t = result.get(i);
			t.setProtocol("TD-%d-%06d".formatted(t.getOpenedAt().atZone(zone).getYear(), i + 1));
		}
		return result;
	}

	private Ticket build(Random random, List<Technician> team, Instant openedAt, Instant now) {
		TicketType type = pick(random, TYPE_WEIGHTS);
		TicketPriority priority = pick(random, PRIORITY_WEIGHTS);
		String[] scenario = SCENARIOS.get(type)[random.nextInt(SCENARIOS.get(type).length)];
		String requester = REQUESTERS[random.nextInt(REQUESTERS.length)];
		Department department = pick(random, DEPARTMENT_WEIGHTS);

		Ticket ticket = new Ticket(null, scenario[0], scenario[1], type, priority, requester, emailOf(requester),
				department, openedAt);

		// Uma parte pequena fica parada na triagem (fila sem técnico).
		double[] responseRange = random.nextDouble() < 0.05 ? new double[] { 18, 60 }
				: FIRST_RESPONSE_HOURS.get(priority);
		Instant firstResponse = openedAt.plus(hours(random, responseRange));
		if (firstResponse.isAfter(now)) {
			return ticket;
		}

		boolean old = Duration.between(openedAt, now).toDays() > 30;
		Technician technician = chooseTechnician(random, team, priority, old);
		ticket.assignTo(technician);
		ticket.addNote(new TechnicalNote(technician.getName(), DIAGNOSIS[random.nextInt(DIAGNOSIS.length)],
				firstResponse));
		ticket.changeStatus(TicketStatus.IN_PROGRESS, technician.getName(), firstResponse);

		if (random.nextDouble() < 0.04) {
			Instant canceledAt = firstResponse.plus(hours(random, new double[] { 0.5, 12 }));
			if (canceledAt.isBefore(now)) {
				ticket.addNote(new TechnicalNote(technician.getName(), CANCEL_REASONS[random.nextInt(
						CANCEL_REASONS.length)], canceledAt));
				ticket.changeStatus(TicketStatus.CANCELED, technician.getName(), canceledAt);
			}
			return ticket;
		}

		// A maioria resolve dentro do SLA; cerca de 12% escala ou trava e estoura o prazo várias vezes.
		boolean recent = Duration.between(openedAt, now).toDays() < 12;
		double factor = random.nextDouble() < (recent ? 0.35 : 0.12)
				? 1.5 + random.nextDouble() * (recent ? 14 : 8)
				: 0.15 + Math.pow(random.nextDouble(), 1.6) * 1.3;
		Duration resolution = Duration.ofMinutes(Math.max(20,
				(long) (priority.getSlaHours() * 60 * factor * TYPE_EFFORT.get(type))));
		Instant resolvedAt = openedAt.plus(resolution);

		boolean waits = random.nextDouble() < 0.22;
		Instant waitingAt = firstResponse.plus(Duration.between(firstResponse, resolvedAt).dividedBy(3));
		if (waits && waitingAt.isBefore(now)) {
			ticket.addNote(new TechnicalNote(technician.getName(), WAITING[random.nextInt(WAITING.length)], waitingAt));
			ticket.changeStatus(TicketStatus.WAITING_USER, technician.getName(), waitingAt);
			Instant back = firstResponse.plus(Duration.between(firstResponse, resolvedAt).multipliedBy(2).dividedBy(3));
			if (back.isAfter(now)) {
				return ticket;
			}
			ticket.changeStatus(TicketStatus.IN_PROGRESS, requesterNote(ticket), back);
		}

		if (resolvedAt.isAfter(now)) {
			return ticket;
		}
		ticket.addNote(new TechnicalNote(technician.getName(), scenario[2], resolvedAt));
		ticket.changeStatus(TicketStatus.RESOLVED, technician.getName(), resolvedAt);

		Instant closedAt = resolvedAt.plus(hours(random, new double[] { 24, 72 }));
		if (closedAt.isBefore(now)) {
			ticket.changeStatus(TicketStatus.CLOSED, AUTO_CLOSE, closedAt);
		}
		return ticket;
	}

	private static String requesterNote(Ticket ticket) {
		return ticket.getRequesterName();
	}

	private static Technician chooseTechnician(Random random, List<Technician> team, TicketPriority priority,
			boolean old) {
		TechnicianLevel level;
		double roll = random.nextDouble();
		if (priority == TicketPriority.CRITICAL) {
			level = roll < 0.6 ? TechnicianLevel.N3 : TechnicianLevel.N2;
		}
		else if (priority == TicketPriority.HIGH) {
			level = roll < 0.6 ? TechnicianLevel.N2 : (roll < 0.85 ? TechnicianLevel.N3 : TechnicianLevel.N1);
		}
		else {
			level = roll < 0.7 ? TechnicianLevel.N1 : TechnicianLevel.N2;
		}
		List<Technician> candidates = team.stream()
				.filter(t -> t.getLevel() == level)
				.filter(t -> old || !FORMER_TECHNICIAN.equals(t.getName()))
				.toList();
		return candidates.get(random.nextInt(candidates.size()));
	}

	private static Duration hours(Random random, double[] range) {
		double value = range[0] + random.nextDouble() * (range[1] - range[0]);
		return Duration.ofMinutes(Math.max(1, Math.round(value * 60)));
	}

	private static <T> T pick(Random random, Map<T, Integer> weights) {
		int total = weights.values().stream().mapToInt(Integer::intValue).sum();
		int roll = random.nextInt(total);
		for (Map.Entry<T, Integer> entry : weights.entrySet()) {
			roll -= entry.getValue();
			if (roll < 0) {
				return entry.getKey();
			}
		}
		throw new IllegalStateException();
	}

	private static Technician technician(String name, TechnicianLevel level, boolean active) {
		Technician technician = new Technician(name, emailOf(name).replace("@exemplo", "@ti.exemplo"), level);
		technician.setActive(active);
		return technician;
	}

	static String emailOf(String name) {
		String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT);
		String[] parts = ascii.split(" ");
		return parts[0] + "." + parts[parts.length - 1] + "@exemplo.com.br";
	}

	private static final Map<TicketType, Integer> TYPE_WEIGHTS = ordered(
			TicketType.ACCESS, 18, TicketType.SOFTWARE, 17, TicketType.HARDWARE, 14, TicketType.NETWORK, 13,
			TicketType.ERP, 12, TicketType.EMAIL, 11, TicketType.PRINTER, 10, TicketType.PHONE, 5);

	private static final Map<TicketPriority, Integer> PRIORITY_WEIGHTS = ordered(
			TicketPriority.LOW, 30, TicketPriority.MEDIUM, 42, TicketPriority.HIGH, 20, TicketPriority.CRITICAL, 8);

	private static final Map<Department, Integer> DEPARTMENT_WEIGHTS = ordered(
			Department.OPERATIONS, 22, Department.SALES, 20, Department.FINANCE, 15, Department.LOGISTICS, 13,
			Department.MARKETING, 10, Department.HR, 9, Department.LEGAL, 6, Department.BOARD, 5);

	private static final Map<TicketType, Double> TYPE_EFFORT = Map.of(
			TicketType.HARDWARE, 1.3, TicketType.SOFTWARE, 0.9, TicketType.NETWORK, 1.1, TicketType.ACCESS, 0.5,
			TicketType.EMAIL, 0.7, TicketType.PRINTER, 0.8, TicketType.ERP, 1.2, TicketType.PHONE, 0.9);

	private static final Map<TicketPriority, double[]> FIRST_RESPONSE_HOURS = Map.of(
			TicketPriority.CRITICAL, new double[] { 0.1, 0.6 },
			TicketPriority.HIGH, new double[] { 0.3, 2 },
			TicketPriority.MEDIUM, new double[] { 0.5, 6 },
			TicketPriority.LOW, new double[] { 1, 16 });

	@SuppressWarnings("unchecked")
	private static <K> Map<K, Integer> ordered(Object... pairs) {
		Map<K, Integer> map = new java.util.LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((K) pairs[i], (Integer) pairs[i + 1]);
		}
		return map;
	}

	private static final String[] REQUESTERS = {
			"Mariana Costa", "João Pedro Santos", "Fernanda Ribeiro", "Carlos Eduardo Pereira", "Beatriz Carvalho",
			"Gustavo Henrique Dias", "Larissa Moreira", "Felipe Barbosa", "Aline Cardoso", "Rodrigo Teixeira",
			"Vanessa Lopes", "Bruno Araújo", "Daniela Mendes", "Leonardo Castro", "Priscila Freitas",
			"André Luiz Ramos", "Tatiane Pinto", "Ricardo Monteiro", "Gabriela Cunha", "Diego Correia",
			"Isabela Duarte", "Vinícius Batista", "Renata Farias", "Fábio Nogueira"
	};

	private static final String[] DIAGNOSIS = {
			"Contato feito com o solicitante. Iniciando diagnóstico remoto.",
			"Chamado triado. Coletando logs e evidências para análise.",
			"Reproduzido o problema em ambiente de teste. Seguindo com a investigação.",
			"Acesso remoto realizado. Verificando configurações da estação.",
			"Verificado histórico do equipamento e chamados anteriores do mesmo usuário."
	};

	private static final String[] WAITING = {
			"Aguardando o usuário confirmar horário para acesso remoto.",
			"Solicitado print da mensagem de erro ao usuário.",
			"Aguardando aprovação do gestor do setor para liberar o acesso.",
			"Aguardando o usuário testar a solução proposta."
	};

	private static final String[] CANCEL_REASONS = {
			"Chamado duplicado: o mesmo problema já está sendo tratado em outro protocolo.",
			"Solicitante informou que o problema foi resolvido sem intervenção.",
			"Solicitação fora do escopo do suporte técnico. Usuário orientado a procurar o setor responsável."
	};

	/** Cenários por tipo: título, descrição e consideração técnica de solução. */
	private static final Map<TicketType, String[][]> SCENARIOS = Map.of(
			TicketType.HARDWARE, new String[][] {
					{ "Notebook não liga", "O notebook não liga mesmo conectado na tomada.",
							"Fonte de alimentação com defeito substituída. Equipamento testado e funcionando." },
					{ "Monitor piscando", "O monitor secundário fica piscando e às vezes apaga.",
							"Cabo HDMI danificado trocado. Monitor estável após teste de 30 minutos." },
					{ "Computador muito lento", "A máquina demora vários minutos para abrir qualquer programa.",
							"HD substituído por SSD e sistema restaurado a partir da imagem padrão." },
					{ "Teclado com teclas falhando", "Algumas teclas do teclado não respondem.",
							"Teclado substituído por um novo do estoque. Patrimônio atualizado." } },
			TicketType.SOFTWARE, new String[][] {
					{ "Instalação do pacote Office", "Preciso do Office instalado na máquina nova.",
							"Pacote Office instalado e ativado com a licença corporativa." },
					{ "Programa fecha sozinho", "O leitor de PDF fecha ao abrir arquivos grandes.",
							"Aplicativo reinstalado na versão homologada e cache limpo." },
					{ "Atualização do Windows travada", "A atualização está parada em 35% há horas.",
							"Componentes do Windows Update reiniciados e atualização concluída." },
					{ "Solicitação de software de edição", "Preciso de um editor de imagens para o setor.",
							"Software homologado instalado após aprovação do gestor." } },
			TicketType.NETWORK, new String[][] {
					{ "Sem acesso à internet", "A estação está sem internet desde a manhã.",
							"Porta do switch reconfigurada na VLAN correta. Conectividade restabelecida." },
					{ "Wi-Fi da sala de reunião instável", "A conexão cai a cada poucos minutos na sala 2.",
							"Access point reposicionado e canal alterado para evitar interferência." },
					{ "VPN não conecta", "Não consigo conectar na VPN trabalhando de casa.",
							"Certificado da VPN renovado e cliente atualizado na máquina do usuário." },
					{ "Pasta de rede inacessível", "A unidade compartilhada do setor não abre.",
							"Mapeamento de rede refeito e permissões do grupo corrigidas no servidor de arquivos." } },
			TicketType.ACCESS, new String[][] {
					{ "Senha expirada", "Minha senha expirou e não consigo entrar no computador.",
							"Senha redefinida com troca obrigatória no próximo login. Identidade confirmada." },
					{ "Usuário bloqueado", "Minha conta foi bloqueada após várias tentativas.",
							"Conta desbloqueada no Active Directory e orientado sobre a política de senhas." },
					{ "Acesso a pasta do financeiro", "Preciso de acesso de leitura à pasta de relatórios.",
							"Usuário incluído no grupo de leitura após aprovação do gestor da área." },
					{ "Criação de usuário para novo colaborador", "Novo colaborador começa na segunda-feira.",
							"Conta, e-mail e acessos padrão do setor criados. Credenciais entregues ao gestor." } },
			TicketType.EMAIL, new String[][] {
					{ "Caixa de e-mail cheia", "Não consigo enviar e-mails, aparece aviso de caixa cheia.",
							"Arquivamento automático configurado e cota da caixa ampliada." },
					{ "E-mails indo para spam", "Clientes dizem que nossos e-mails chegam no spam.",
							"Registros SPF e DKIM revisados com o provedor. Entregas normalizadas." },
					{ "Outlook não sincroniza", "O Outlook não recebe mensagens novas desde ontem.",
							"Perfil do Outlook recriado e arquivo OST reconstruído." } },
			TicketType.PRINTER, new String[][] {
					{ "Impressora não imprime", "Os documentos ficam presos na fila de impressão.",
							"Spooler reiniciado e driver atualizado. Fila liberada." },
					{ "Papel atolando", "A impressora do segundo andar atola papel com frequência.",
							"Rolete de tração limpo e substituído. Teste com 50 páginas sem atolamento." },
					{ "Instalar impressora na estação", "Preciso imprimir na impressora da recepção.",
							"Impressora de rede instalada e definida como padrão para o usuário." } },
			TicketType.ERP, new String[][] {
					{ "Erro ao emitir nota fiscal", "O ERP mostra erro de validação ao emitir NF-e.",
							"Cadastro fiscal do produto corrigido (NCM). Nota emitida com sucesso." },
					{ "Relatório do ERP não carrega", "O relatório de vendas mensal fica carregando.",
							"Índice do banco do ERP recriado. Relatório gerado em menos de 10 segundos." },
					{ "Permissão no módulo de compras", "Não tenho acesso ao módulo de pedidos de compra.",
							"Perfil de acesso de compras atribuído conforme solicitação aprovada." },
					{ "Integração de estoque parada", "O estoque não está atualizando com as vendas.",
							"Serviço de integração reiniciado e fila de mensagens reprocessada." } },
			TicketType.PHONE, new String[][] {
					{ "Ramal sem linha", "O telefone do ramal 214 está mudo.",
							"Aparelho reconfigurado no PABX IP e cabo de rede substituído." },
					{ "Configurar softphone", "Preciso atender ligações pelo computador em home office.",
							"Softphone instalado e ramal registrado. Teste de chamada realizado." } });
}
