package org.crescendo.fmi;

import io.grpc.ServerImpl;
import io.grpc.netty.NettyServerBuilder;

import java.io.IOException;
import java.util.logging.Logger;

import com.lausdahl.examples.FmuGrpc;

public class GrpcServer {

	private static final Logger logger = Logger.getLogger(GrpcServer.class
			.getName());

	private final int port;

	private ServerImpl grpcServer;

	public GrpcServer(int port) {
		this.port = port;
	}

	/** Start serving requests. */
	public void start() throws IOException {
		grpcServer = NettyServerBuilder.forPort(port)
				.addService(FmuGrpc.bindService(new CrescendoGrpcFmu()))
				.build().start();
		logger.info("Server started, listening on " + port);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				// Use stderr here since the logger may has been reset by its
				// JVM shutdown hook.
				System.err
						.println("*** shutting down gRPC server since JVM is shutting down");
				GrpcServer.this.stop();
				System.err.println("*** server shut down");
			}
		});
	}

	/** Stop serving requests and shutdown resources. */
	public void stop() {
		if (grpcServer != null) {
			grpcServer.shutdown();
		}
	}

	public static void main(String[] args) throws Exception {
		
		int p = 8980;
		
		if(args.length>1)
		{
			if(args[0].equals("-p") || args[1].equals("--port"))
			{
				p = Integer.parseInt(args[1]);
			}
		}
		
		GrpcServer server = new GrpcServer(p);

		try {
			server.start();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}


}
