import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

//Owen Neal
//CS 342

public class Server{

	int count = 1; //count of clients threads
	ArrayList<ClientThread> clients = new ArrayList<>();
	private final Object lock = new Object();
	TheServer server;
	private Consumer<Serializable> callback;
	ArrayList<String> users = new ArrayList<>();
	HashMap<String, List<String>> groups = new HashMap<>();

	Server(Consumer<Serializable> call){
		callback = call;
		server = new TheServer();
		server.start();
	}

	public class TheServer extends Thread{

		public void run() {
		
			try(ServerSocket mysocket = new ServerSocket(5555)){
		    	System.out.println("Server is waiting for a client!");

			//while loop to keep server running
			//creates new client thread for each client that connects
		    while(true) {
				ClientThread c = new ClientThread(mysocket.accept(), count);
				callback.accept("Client has connected to server: " + "client #" + count);
				clients.add(c);
				c.start();
				count++;
			    }
			}//end of try
				catch(Exception e) {
					callback.accept("Server socket did not launch");
				}
			}//end of while
		}
	

		class ClientThread extends Thread{
			Socket connection;
			int count;
			ObjectInputStream in;
			ObjectOutputStream out;
			private String username;

			ClientThread(Socket s, int count){
				this.connection = s;
				this.count = count;
			}

			public void setUsername(String name) {
				this.username = name;
			}

			public String getUsername() {
				return this.username;
			}

			public void updateClients(Message message) {
				synchronized (lock) {
					for (ClientThread t : clients) {
						try {
							if (message.getReceiver() == null || message.getReceiver().contains(t.getUsername())) {
									t.out.writeObject(message);
									t.out.flush();
							}
						} catch (Exception nullPointer) {
						}
					}
				}
			}

			public void sendGroupMessage(Message message) {
				System.out.println("sending group message to: " + message.getGroupName());
				synchronized (lock) {
					for (ClientThread t : clients) {
						try {
							List<String> groupMembers = groups.get(message.getGroupName());
							for (String user : groupMembers) {
								if (user.equals(t.getUsername())) {
									t.out.writeObject(message);
									t.out.flush();
								}
							}
						} catch (Exception nullPointer) {
						}
					}
				}
			}

			public void sendGroupList() {
				List<String> groupNames = new ArrayList<>(groups.keySet());
                synchronized (lock) {
					for (ClientThread t : clients) {
						try {
							t.out.writeObject(new Message(null, groupNames, "List of groups", "groupList", null));
							t.out.flush();
						} catch (Exception e) {
							System.out.println("Error sending group list");
						}
					}
				}
			}

			public void sendUsernames() {
				ArrayList<String> usernames = new ArrayList<>();
				synchronized (lock) {
					for (ClientThread t : clients) {
						usernames.add(t.getUsername());
					}
					updateClients(new Message(null, usernames, "users on server" + usernames, "connectedClients", null));
				}
			}
			
			public void run(){
					
				try {
					in = new ObjectInputStream(connection.getInputStream());
					out = new ObjectOutputStream(connection.getOutputStream());
					connection.setTcpNoDelay(true);
				}
				catch(Exception e) {
					System.out.println("Streams not open");
				}

				 while(true) {
					    try {
					    	Message data = (Message) in.readObject();
							if (data.getType().equals("message")) {
								callback.accept(data.getSender() + " sent: " + data.getMessage());
								if (data.getGroupName() != null) {
									sendGroupMessage(data);
								} else {
									updateClients(data);
								}
							} else if (data.getType().equals("joined")) {
								if (!users.contains(data.getSender())) {
									users.add(data.getSender());
									setUsername(data.getSender());
									updateClients(new Message(data.getSender(), null, " has joined the chat", "joined", null));
									sendUsernames(); //update all clients with new user
									callback.accept(data.getSender() + data.getMessage());

									//global group update
									if (!groups.containsKey("Global")) {
										groups.put("Global", new ArrayList<>());
									}
									groups.get("Global").add(data.getSender());
									sendGroupMessage(new Message(null, groups.get("Global"), "Global", "group", "Global")); //update Global group with new user
								}
							} else if (data.getType().equals("group")) {
								if (!groups.containsKey(data.getGroupName())) {
									groups.put(data.getGroupName(), data.getReceiver());
									System.out.println("New group" + groups);
								}
								sendGroupMessage(data);
								sendGroupList();
							} else if (data.getType().equals("usernameCheck")) {
								System.out.println("Checking username: " + users.contains(data.getSender()));
								if (users.contains(data.getSender())) {
									out.writeObject(new Message(null, null, "Username taken", "usernameCheck", null));
									out.flush();
								} else {
									out.writeObject(new Message(null, null, "Username available", "usernameCheck", null));
									out.flush();
								}
							}
						}
					    catch(Exception e) {
					    	callback.accept("OOOOPPs...Something wrong with the socket from client: " + count + "....closing down!");
					    	//updateClients("Client #"+count+" has left the server!");
							updateClients(new Message(this.username, null, " has left the chat", "left", null));
					    	clients.remove(this);
							users.remove(this.username);
							if (groups.containsKey("Global")) {
								groups.get("Global").remove(this.username);
							}
							//groups.get("Global").remove(this.username);
							sendUsernames();
					    	break;
					    }
					}
				}//end of run

		}//end of client thread
}


	
	

	
