package com.javaprophet.javamailserver.mailbox;

import java.util.ArrayList;
import com.javaprophet.javamailserver.JavaMailServer;

public class Mailbox {
	public final EmailAccount owner;
	public String name = "";
	public final ArrayList<Email> emails = new ArrayList<Email>();
	public boolean subscribed = true;
	
	public Mailbox(EmailAccount owner, String name) {
		this.owner = owner;
		this.name = name;
		Email te = new Email("Date: Tue, 6 Jan 2015 21:20:31 -0500" + JavaMailServer.crlf + "From: auth-results@verifier.port25.com" + JavaMailServer.crlf + "To: cock@cock.cock" + JavaMailServer.crlf + "Subject: Authentication Report" + JavaMailServer.crlf + "Message-Id: <1420597228-98083@verifier.port25.com>" + JavaMailServer.crlf + "In-Reply-To: <1d19656b6717e5c85b136d71fa85d913@raidproducts.net>" + JavaMailServer.crlf + JavaMailServer.crlf + "welcome to my mail server, bitches!", emails.size() + 1, "auth-results@verifier.port25.com");
		te.flags.add("\\Unseen");
		te.flags.add("\\Recent");
		emails.add(te);
	}
}
