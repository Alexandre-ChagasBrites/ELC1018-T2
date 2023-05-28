import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

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
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
    }

    public static class ServerChat implements  IServerChat {
        private HashMap<String, RoomChat> roomList;
        private ExecutorService pool;
        private Registry registry;

        private JFrame frame = new JFrame("Controller");
        private JPanel jPaneRoomList = new JPanel();
        private JScrollPane roomsPane = new JScrollPane(jPaneRoomList);
        private JButton closeRoomButton = new JButton("Close room");
        private DefaultListModel<String> listModel;
        private JList<String> stringList;
        private String selectedRoomName;

        ServerChat(Registry registry) {
            this.roomList = new HashMap<String, RoomChat>();
            this.pool = Executors.newCachedThreadPool();
            this.registry = registry;
            this.roomsPane.setBorder(BorderFactory.createTitledBorder("Rooms"));
            this.frame.getContentPane().add(roomsPane, BorderLayout.CENTER);
            this.frame.getContentPane().add(closeRoomButton, BorderLayout.PAGE_END);
            this.frame.setMinimumSize(new Dimension(500, 500));
            this.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            this.stringList = new JList<String>();
            this.listModel = new DefaultListModel<>();
            stringList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            
            this.addListeners();
            this.refreshRooms();
        }

        private void closeRoom(String roomName) {
            if (!this.roomList.containsKey(roomName)) {
                return;
            }

            RoomChat room = this.roomList.get(roomName);
            room.closeRoom();
            this.roomList.remove(roomName);
            refreshRooms();
            try {
                this.registry.unbind(roomName);
            } catch (Exception e) {
                System.err.println("Error while closing room \"" + roomName + "\": " + e.toString());
                e.printStackTrace();
            }
        }

        private void addListeners() {
            this.frame.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent ev) {
                    for (Map.Entry<String, RoomChat> entry: roomList.entrySet()) {
                        closeRoom(entry.getKey());
                    }
                    frame.setVisible(false);
                    frame.dispose();
                }
            });

            closeRoomButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    closeRoom(selectedRoomName);
                }
            });

            stringList.setModel(this.listModel);
            stringList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int selectedIndex = stringList.locationToIndex(e.getPoint());
                    stringList.setSelectedIndex(selectedIndex);
                    String roomName = stringList.getSelectedValue();
                    System.out.println("Room selected: " + roomName);
                    selectedRoomName = roomName;
                }
            });

            JScrollPane scrollPane = new JScrollPane(stringList);

            jPaneRoomList.add(scrollPane);
            jPaneRoomList.setLayout(new BoxLayout(jPaneRoomList, BoxLayout.PAGE_AXIS));

            this.frame.setVisible(true);
    }

        private void refreshRooms() {
            this.listModel.clear();
            if (this.roomList.size() > 0) {
                int i = 0;
                for (String roomName : this.roomList.keySet()) {
                    this.listModel.add(i, roomName);
                    i++;
                }
            }

            this.stringList.setModel(listModel);
        }

        @Override
        public ArrayList<String> getRooms() {
            return new ArrayList<String>(this.roomList.keySet());
        }

        @Override
        public void createRoom(String roomName) {
            try {
                RoomChat room = new RoomChat(roomName);
                IRoomChat roomStub = (IRoomChat) UnicastRemoteObject.exportObject(room, 0);
                registry.bind(roomName, roomStub);
                this.roomList.put(roomName, room);
                pool.execute(room);
                refreshRooms();
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
                    Pair<String, String> messagePair;
                    synchronized(this.messageFifo) {
                        messagePair = this.messageFifo.poll(); 
                    }
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
            synchronized(this.messageFifo) {
                this.messageFifo.add(new Pair<String, String>(usrName, msg));
            }
        }

        @Override
        public void joinRoom(String usrName, IUserChat user) {
            this.userList.put(usrName, user);
        }

        @Override
        public void leaveRoom(String usrName) {
            this.userList.remove(usrName);
            System.out.println("Removed user: " + usrName);
            // Notify all users that someone has left
            this.messageFifo.add(new Pair<String, String>(usrName, "Has left the room"));
        }

        @Override
        public void closeRoom() {
            System.out.println("Close room: " + this.roomName);
            this.shouldCloseRoom = true;
            for (IUserChat user: this.userList.values()) {
                try {
                    user.deliverMsg(null, "Sala fechada pelo servidor.");
                } catch (Exception e) {
                    System.err.println("Error while sending close message to user: " + e.toString());
                    e.printStackTrace();
                }
            }
        }

        @Override
        public String getRoomName() {
            return this.roomName;
        }
    }
}