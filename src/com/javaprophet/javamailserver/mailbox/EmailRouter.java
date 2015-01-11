package com.javaprophet.javamailserver.mailbox;

import com.javaprophet.javamailserver.JavaMailServer;

public class EmailRouter {
	public static void route(Email email) {
		for (String to : email.to) {
			if (to.contains("<") && to.contains(">")) {
				to = to.substring(to.indexOf("<") + 1, to.indexOf(">"));
			}
			String dom = to.substring(to.indexOf("@") + 1);
			if (dom.equals(JavaMailServer.mainConfig.get("domain"))) {
				for (EmailAccount acct : JavaMailServer.accounts) {
					if (acct.email.equals(to)) {
						acct.deliver(email);
					}
				}
			}else {
				System.out.println("outgoing: " + email);
			}
		}
	}
}
