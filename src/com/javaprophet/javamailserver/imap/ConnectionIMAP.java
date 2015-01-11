package com.javaprophet.javamailserver.imap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import com.javaprophet.javamailserver.JavaMailServer;
import com.javaprophet.javamailserver.networking.Connection;

/**
 * Handles a single connection.
 */
public class ConnectionIMAP extends Connection {
	
	public ConnectionIMAP(Socket s, DataInputStream in, DataOutputStream out, boolean ssl) {
		super(s, in, out, ssl);
	}
	
	private static ArrayList<ThreadWorkerIMAP> workers = new ArrayList<ThreadWorkerIMAP>();
	
	public static void init() {
		for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
			ThreadWorkerIMAP worker = new ThreadWorkerIMAP();
			workers.add(worker);
			worker.start();
		}
	}
	
	public void handleConnection() {
		ThreadWorkerIMAP.addWork(s, in, out, ssl);
		JavaMailServer.runningThreads.remove(this);
	}
}
