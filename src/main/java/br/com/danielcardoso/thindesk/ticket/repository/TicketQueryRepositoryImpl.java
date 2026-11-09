package br.com.danielcardoso.thindesk.ticket.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import br.com.danielcardoso.thindesk.ticket.domain.Ticket;
import br.com.danielcardoso.thindesk.ticket.domain.TicketStatus;
import br.com.danielcardoso.thindesk.ticket.service.TicketFilter;

class TicketQueryRepositoryImpl implements TicketQueryRepository {

	static final String COUNTERS = "counters";
	static final String TICKET_COUNTER = "ticket";

	private final MongoTemplate mongo;

	TicketQueryRepositoryImpl(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	@Override
	public List<Ticket> findForMetrics(TicketFilter filter, Instant now) {
		Query query = new Query(build(filter, now, true));
		excludeLongFields(query);
		return mongo.find(query, Ticket.class);
	}

	@Override
	public List<Ticket> findOpenedOrResolvedIn(TicketFilter filter, Instant now) {
		Criteria criteria = build(filter, now, false);
		if (filter.from() != null || filter.to() != null) {
			criteria = new Criteria().andOperator(criteria,
					new Criteria().orOperator(range("openedAt", filter), range("resolvedAt", filter)));
		}
		Query query = new Query(criteria);
		excludeLongFields(query);
		return mongo.find(query, Ticket.class);
	}

	@Override
	public Page<Ticket> search(TicketFilter filter, Pageable pageable, Instant now) {
		Criteria criteria = build(filter, now, true);
		Query query = new Query(criteria).with(pageable);
		excludeLongFields(query);
		List<Ticket> content = mongo.find(query, Ticket.class);
		return PageableExecutionUtils.getPage(content, pageable,
				() -> mongo.count(new Query(criteria), Ticket.class));
	}

	@Override
	public Optional<Instant[]> openedAtRange() {
		Query first = new Query().with(Sort.by(Sort.Direction.ASC, "openedAt")).limit(1);
		first.fields().include("openedAt");
		Ticket oldest = mongo.findOne(first, Ticket.class);
		if (oldest == null) {
			return Optional.empty();
		}
		Query last = new Query().with(Sort.by(Sort.Direction.DESC, "openedAt")).limit(1);
		last.fields().include("openedAt");
		Ticket newest = mongo.findOne(last, Ticket.class);
		return Optional.of(new Instant[] { oldest.getOpenedAt(), newest.getOpenedAt() });
	}

	@Override
	public long nextProtocolSequence() {
		Document counter = mongo.findAndModify(
				Query.query(Criteria.where("_id").is(TICKET_COUNTER)),
				new Update().inc("seq", 1L),
				FindAndModifyOptions.options().returnNew(true).upsert(true),
				Document.class, COUNTERS);
		return ((Number) counter.get("seq")).longValue();
	}

	@Override
	public void resetProtocolSequence(long value) {
		mongo.upsert(Query.query(Criteria.where("_id").is(TICKET_COUNTER)), new Update().set("seq", value),
				COUNTERS);
	}

	static Criteria build(TicketFilter filter, Instant now, boolean includePeriod) {
		List<Criteria> parts = new ArrayList<>();
		if (includePeriod && (filter.from() != null || filter.to() != null)) {
			parts.add(range("openedAt", filter));
		}
		if (!filter.statuses().isEmpty()) {
			parts.add(Criteria.where("status").in(filter.statuses()));
		}
		if (!filter.types().isEmpty()) {
			parts.add(Criteria.where("type").in(filter.types()));
		}
		if (!filter.priorities().isEmpty()) {
			parts.add(Criteria.where("priority").in(filter.priorities()));
		}
		if (!filter.departments().isEmpty()) {
			parts.add(Criteria.where("department").in(filter.departments()));
		}
		if (StringUtils.hasText(filter.technicianId())) {
			parts.add(TicketFilter.UNASSIGNED.equals(filter.technicianId())
					? Criteria.where("technicianId").isNull()
					: Criteria.where("technicianId").is(filter.technicianId()));
		}
		if (StringUtils.hasText(filter.text())) {
			Pattern pattern = Pattern.compile(Pattern.quote(filter.text().trim()), Pattern.CASE_INSENSITIVE);
			parts.add(new Criteria().orOperator(
					Criteria.where("protocol").regex(pattern),
					Criteria.where("title").regex(pattern),
					Criteria.where("requesterName").regex(pattern),
					Criteria.where("technicianName").regex(pattern)));
		}
		if (filter.overdueOnly()) {
			parts.add(Criteria.where("status").in(TicketStatus.ACTIVE));
			parts.add(Criteria.where("dueAt").lt(now));
		}
		return parts.isEmpty() ? new Criteria() : new Criteria().andOperator(parts);
	}

	private static Criteria range(String field, TicketFilter filter) {
		Criteria criteria = Criteria.where(field);
		if (filter.from() != null) {
			criteria = criteria.gte(filter.from());
		}
		if (filter.to() != null) {
			criteria = criteria.lt(filter.to());
		}
		return criteria;
	}

	private static void excludeLongFields(Query query) {
		query.fields().exclude("notes", "history", "description");
	}
}
