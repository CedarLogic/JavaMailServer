package com.javaprophet.javamailserver.smtp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import com.javaprophet.javamailserver.JavaMailServer;
import com.javaprophet.javamailserver.networking.Connection;

/**
 * Handles a single connection.
 */
public class ConnectionSMTP extends Connection {
	
	public ConnectionSMTP(Socket s, DataInputStream in, DataOutputStream out, boolean ssl) {
		super(s, in, out, ssl);
	}
	
	private static ArrayList<ThreadWorkerSMTP> workers = new ArrayList<ThreadWorkerSMTP>();
	
	public static void init() {
		for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
			ThreadWorkerSMTP worker = new ThreadWorkerSMTP();
			workers.add(worker);
			worker.start();
		}
	}
	
	public void handleConnection() {
		ThreadWorkerSMTP.addWork(s, in, out, ssl);
		JavaMailServer.runningThreads.remove(this);
	}
}
