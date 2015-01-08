package com.javaprophet.javamailserver.mailbox;

import java.util.ArrayList;

public class Mailbox {
	public final EmailAccount owner;
	public String name = "";
	public final ArrayList<Email> emails = new ArrayList<Email>();
	public boolean subscribed = true;
	
	public Mailbox(EmailAccount owner, String name) {
		this.owner = owner;
		this.name = name;
		Email te = new Email("lololol", emails.size() + 1);
		te.flags.add("\\Unseen");
		te.flags.add("\\Recent");
		emails.add(te);
	}
}
