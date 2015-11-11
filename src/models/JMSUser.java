package models;

import java.util.UUID;

public class JMSUser {
	public String name;
	public String baseName;
	public String password;
	public UUID id;

	public JMSUser() {

	}

	public JMSUser(String name, String password) {
		this.name = name;
		this.password = password;
		this.id = UUID.randomUUID();
		
		this.baseName = name.replaceAll("[^A-Za-z0-9]", "");
		this.baseName = baseName.toUpperCase();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getBaseName() {
		return baseName;
	}

	public void setBaseName(String baseName) {
		this.baseName = baseName;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof JMSUser) {
			JMSUser otherUser = (JMSUser) o;
			
			if (otherUser.getBaseName().equals(this.getBaseName())
					&& otherUser.getId().equals(this.getId())) {
				return true;
			}
		}

		return false;
	}

}
