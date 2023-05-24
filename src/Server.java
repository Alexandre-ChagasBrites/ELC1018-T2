import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    public static void main(String[] args) {
        System.out.println("The chat server is running...");

        try {
            int port = 2020;
            Registry registry = LocateRegistry.createRegistry(port);

            System.out.println("RMI registry created on port: " + port);

            ServerChat serverChat = new ServerChat(registry);
            IServerChat stub = (IServerChat) UnicastRemoteObject.exportObject(serverChat, 0);

            registry.bind("Servidor", stub);

            System.out.println("Server is ready!");

            while(true);
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
    }

    public static class ServerChat implements  IServerChat {
        private ArrayList<String> roomList;
        private ExecutorService pool;
        private Registry registry;

        ServerChat(Registry registry) {
            this.roomList = new ArrayList<String>();
            this.pool = Executors.newCachedThreadPool();
            this.registry = registry;
        }
        @Override
        public ArrayList<String> getRooms() {
            return this.roomList;
        }

        @Override
        public void createRoom(String roomName) {
            try {
                RoomChat room = new RoomChat(roomName);
                IRoomChat roomStub = (IRoomChat) UnicastRemoteObject.exportObject(room, 0);
                registry.bind(roomName, roomStub);
                this.roomList.add(roomName);
                pool.execute(room);
            } catch (Exception e) {
                System.err.println("Error while creating room: " + e.toString());
                e.printStackTrace();
            }
        }
    }

    private static class RoomChat implements Runnable, IRoomChat {
        private HashMap<String, IUserChat> userList;
        private String roomName;
        private LinkedList<Pair<String, String>> messageFifo;
        private boolean shouldCloseRoom = false;
        RoomChat(String roomName) {
            this.roomName = roomName;
            this.userList = new HashMap<String, IUserChat>();
            this.messageFifo = new LinkedList<Pair<String, String>>();
        }

        @Override
        public void run() {
            System.out.println("Running Room Chat: " + this.roomName);
            try {
                while (!this.shouldCloseRoom) {
                    Pair<String, String> messagePair = this.messageFifo.poll();
                    if (messagePair != null) {
                        System.out.println("Propagating message pair: " + messagePair.toString());

                        // We guarantee that we deliver messages in order because we remove it from our FIFO queue in order
                        for (IUserChat user: this.userList.values()) {
                            user.deliverMsg(messagePair.first, messagePair.second);
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Error:" + e.toString());
                e.printStackTrace();
            }
        }

        @Override
        public void sendMsg(String usrName, String msg) {
            // Add the message to our FIFO queue so it can be delivered to uers
            this.messageFifo.add(new Pair<String, String>(usrName, msg));
        }

        @Override
        public void joinRoom(String usrName, IUserChat user) {
            this.userList.put(usrName, user);
        }

        @Override
        public void leaveRoom(String usrName) {
            this.userList.remove(usrName);
            System.out.printf("Removed user: " + usrName);
            // Notify all users that someone has left
            this.messageFifo.add(new Pair<String, String>(usrName, "Has left the room"));
        }

        @Override
        public void closeRoom() {
            System.out.println("Close room!");
            this.shouldCloseRoom = true;
        }

        @Override
        public String getRoomName() {
            return this.roomName;
        }
    }
}