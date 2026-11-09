# Evidências: testes automatizados (JUnit 5)

Gerado em 09/11/2026 11:11 com `./mvnw test` (relatórios do Surefire em `target/surefire-reports`).

**Resultado: 75 de 75 testes aprovados, 0 falhas.**

Os testes não dependem do MongoDB Atlas: regras de domínio e cálculos são testados com dados montados à mão, e a camada web usa `@WebMvcTest` com o Spring Security real e os serviços simulados.

| Classe | Teste | Tempo | Resultado |
|---|---|---|---|
| DashboardApiTest | routingErrorsAreJson | 0.531s | OK |
| DashboardApiTest | basicAuthWorks | 0.798s | OK |
| DashboardApiTest | invalidPeriod | 0.069s | OK |
| DashboardApiTest | invalidFilterValues | 0.123s | OK |
| DashboardApiTest | businessRuleReturns422 | 0.095s | OK |
| DashboardApiTest | dashboardPage | 2.575s | OK |
| DashboardApiTest | anonymousGets401Json | 0.057s | OK |
| DashboardApiTest | userRoleIsForbidden | 0.031s | OK |
| DashboardApiTest | forwardsSpecificParameters | 0.073s | OK |
| DashboardApiTest | wrongBasicCredentials | 0.075s | OK |
| DashboardApiTest | managerGetsSummary | 0.046s | OK |
| DashboardApiTest | createWithBasicAuth | 0.108s | OK |
| DashboardApiTest | listValidatesSort | 0.111s | OK |
| DashboardApiTest | sessionWritesRequireCsrf | 0.024s | OK |
| DashboardApiTest | ticketNotFound | 0.036s | OK |
| DashboardApiTest | createValidation | 0.042s | OK |
| DashboardApiTest | ticketDetail | 0.038s | OK |
| DashboardApiTest | anonymousPageRedirectsToLogin | 0.020s | OK |
| PasswordValidationTest | rejectsWeakPasswords(String)[1] | 0.152s | OK |
| PasswordValidationTest | rejectsWeakPasswords(String)[2] | 0.002s | OK |
| PasswordValidationTest | rejectsWeakPasswords(String)[3] | 0.002s | OK |
| PasswordValidationTest | rejectsWeakPasswords(String)[4] | 0.001s | OK |
| PasswordValidationTest | rejectsWeakPasswords(String)[5] | 0.002s | OK |
| PasswordValidationTest | acceptsStrongMatchingPassword | 0.002s | OK |
| PasswordValidationTest | rejectsMismatchedConfirmation | 0.009s | OK |
| DashboardMetricsTest | typeRankingBySolved | 0.032s | OK |
| DashboardMetricsTest | timelineUsesBusinessTimezone | 0.010s | OK |
| DashboardMetricsTest | change | 0.002s | OK |
| DashboardMetricsTest | totals | 0.002s | OK |
| DashboardMetricsTest | dailyTimeline | 0.005s | OK |
| DashboardMetricsTest | emptyDataUsesNullAverages | 0.003s | OK |
| DashboardMetricsTest | technicianRanking | 0.004s | OK |
| DashboardMetricsTest | rates | 0.002s | OK |
| DashboardMetricsTest | times | 0.001s | OK |
| DashboardMetricsTest | weeklyTimeline | 0.004s | OK |
| DashboardMetricsTest | statusDistribution | 0.007s | OK |
| DashboardMetricsTest | typeRankingByTotalWithLimit | 0.002s | OK |
| DashboardMetricsTest | granularityRules | 0.020s | OK |
| LoginAttemptServiceTest | expiredLockStartsNewCount | 0.164s | OK |
| LoginAttemptServiceTest | locksAccountAfterMaxFailedAttempts | 0.003s | OK |
| LoginAttemptServiceTest | successfulLoginResetsCounter | 0.005s | OK |
| TicketServiceTest | errors | 0.218s | OK |
| TicketServiceTest | resolvingRequiresNote | 0.011s | OK |
| TicketServiceTest | assignmentAddsNote | 0.012s | OK |
| TicketServiceTest | resolvesWithNote | 0.006s | OK |
| TicketServiceTest | createsTicket | 0.005s | OK |
| TicketWorkflowTest | overdueRules | 0.002s | OK |
| TicketWorkflowTest | rejectsSameStatus | 0.002s | OK |
| TicketWorkflowTest | reopeningClearsResolution | 0.002s | OK |
| TicketWorkflowTest | inactiveTechnicianCannotBeAssigned | 0.001s | OK |
| TicketWorkflowTest | cannotStartWithoutTechnician | 0.001s | OK |
| TicketWorkflowTest | rejectsInvalidTransitions | 0.003s | OK |
| TicketWorkflowTest | recordsTimestampsAndHistory | 0.001s | OK |
| TicketWorkflowTest | dueDateFollowsPrioritySla | 0.001s | OK |
| UserServiceTest | changePasswordRequiresCurrentPassword | 0.080s | OK |
| UserServiceTest | registerHashesPasswordNormalizesEmailAndAssignsUserRole | 0.011s | OK |
| UserServiceTest | registerRejectsDuplicatedEmail | 0.004s | OK |
| UserServiceTest | changingRoleEndsTargetSessions | 0.004s | OK |
| UserServiceTest | adminCannotChangeOwnRole | 0.006s | OK |
| AccessControlTest | managerSeesUsersReadOnly | 0.200s | OK |
| AccessControlTest | changingPasswordEndsOtherSessions | 0.109s | OK |
| AccessControlTest | publicPagesAreAccessibleWithoutLogin | 0.159s | OK |
| AccessControlTest | registerRejectsWeakPasswordAndMismatch | 0.105s | OK |
| AccessControlTest | managersAndAdminsGoToDashboardAsHome | 0.028s | OK |
| AccessControlTest | userAccessesOnlyOwnArea | 0.125s | OK |
| AccessControlTest | adminCanChangeRoles | 0.055s | OK |
| AccessControlTest | registerValidUserRedirectsToLogin | 0.019s | OK |
| AccessControlTest | protectedPagesRedirectAnonymousToLogin | 0.025s | OK |
| AccessControlTest | userCannotCallAdminActions | 0.026s | OK |
| AccessControlTest | loggedUserGoesToProfileAsHome | 0.029s | OK |
| AccessControlTest | postsWithoutCsrfTokenAreRejected | 0.028s | OK |
| AccessControlTest | managerCannotCallAdminActions | 0.022s | OK |
| AccessControlTest | unknownUserReturnsNotFound | 0.023s | OK |
| AccessControlTest | adminSeesSameScreenWithActions | 0.142s | OK |
| AccessControlTest | securityHeadersArePresent | 0.070s | OK |
