package com.javaprophet.javamailserver.sync;

import java.io.IOException;
import java.util.ArrayList;
import com.javaprophet.javamailserver.mailbox.EmailAccount;

public abstract class Sync {
	
	public abstract void save(ArrayList<EmailAccount> accts) throws IOException;
	
	public abstract void load(ArrayList<EmailAccount> accts) throws IOException;
}
