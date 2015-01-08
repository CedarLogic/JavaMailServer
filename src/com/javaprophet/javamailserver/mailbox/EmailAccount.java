package com.javaprophet.javamailserver.mailbox;

import java.util.ArrayList;

public class EmailAccount {
	public final String email;
	public final String password;
	public final int dbID;
	public final ArrayList<Mailbox> mailboxes = new ArrayList<Mailbox>();
	
	public EmailAccount(String email, String password, int dbID) {
		this.email = email;
		this.password = password;
		this.dbID = dbID;
		mailboxes.add(new Mailbox(this, "INBOX"));
		mailboxes.add(new Mailbox(this, "Trash"));
	}
	
	public Mailbox getMailbox(String name) {
		return getMailbox(name, false);
	}
	
	public Mailbox getMailbox(String name, boolean regex) {
		for (Mailbox m : mailboxes) {
			if (regex ? m.name.matches(name) : m.name.equals(name)) {
				return m;
			}
		}
		return null;
	}
}
