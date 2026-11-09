package br.com.danielcardoso.thindesk.ticket.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("technicians")
public class Technician {

	@Id
	private String id;

	private String name;

	@Indexed(unique = true)
	private String email;

	private TechnicianLevel level;

	private boolean active = true;

	public Technician() {
	}

	public Technician(String name, String email, TechnicianLevel level) {
		this.name = name;
		this.email = email;
		this.level = level;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public TechnicianLevel getLevel() {
		return level;
	}

	public void setLevel(TechnicianLevel level) {
		this.level = level;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}
}
