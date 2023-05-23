import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class UserChat implements IUserChat {
    private String serverAddress;
    private String usrName;
    private IUserChat userStub;
    private IServerChat serverStub;
    private IRoomChat roomStub;
    
    private JButton createButton = new JButton("Create room");
    private JButton leaveButton = new JButton("Leave room");
    private JButton refreshButton = new JButton("Refresh");
    private JPanel generalPane = new JPanel(new BorderLayout());
    private JPanel roomsList = new JPanel();
    private JScrollPane roomsPane = new JScrollPane(roomsList);
    private JPanel leftPane = new JPanel(new BorderLayout());
    private JTextArea messageArea = new JTextArea(16, 48);
    private JTextField textField = new JTextField(48);
    private JPanel rightPane = new JPanel(new BorderLayout());
    private JFrame frame = new JFrame("Chatter");

    public UserChat(String serverAddress) {
        this.serverAddress = serverAddress;
        leaveButton.setEnabled(false);
        messageArea.setEditable(false);
        textField.setEditable(false);
        rightPane.setEnabled(false);

        generalPane.setBorder(BorderFactory.createTitledBorder("General"));
        generalPane.add(createButton, BorderLayout.PAGE_START);
        generalPane.add(leaveButton, BorderLayout.CENTER);
        generalPane.add(refreshButton, BorderLayout.PAGE_END);
        roomsPane.setBorder(BorderFactory.createTitledBorder("Rooms"));
        leftPane.add(generalPane, BorderLayout.PAGE_START);
        leftPane.add(roomsPane, BorderLayout.CENTER);
        rightPane.setBorder(BorderFactory.createTitledBorder("Room"));
        rightPane.add(new JScrollPane(messageArea), BorderLayout.CENTER);
        rightPane.add(textField, BorderLayout.PAGE_END);
        frame.getContentPane().add(leftPane, BorderLayout.LINE_START);
        frame.getContentPane().add(rightPane, BorderLayout.CENTER);
        frame.pack();

        addListeners();
    }

    private void addListeners() {
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent ev) {
                if (roomStub != null) {
                    leaveRoom();
                }
                if (userStub != null) {
                    disconnect();
                }
                frame.setVisible(false);
                frame.dispose();
            }
        });

        createButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String roomName = JOptionPane.showInputDialog(
                    frame, 
                    "Choose a room name:", 
                    "Room name selection",
                    JOptionPane.PLAIN_MESSAGE);
                if (roomName == null || roomName.isEmpty()) {
                    return;
                }
                createRoom(roomName);
                refresh();
            }
        });

        leaveButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                leaveRoom();
            }
        });

        refreshButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                refresh();
            }
        });

        textField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try { 
                    roomStub.sendMsg(usrName, textField.getText());
                } catch (Exception exception) {
                    System.err.println("Client exception: " + exception.toString());
                    exception.printStackTrace();
                }
                textField.setText("");
            }
        });
    }

    private void connect() {
        try {
            userStub = (IUserChat) UnicastRemoteObject.exportObject(this, 0);
            Registry registry = LocateRegistry.getRegistry(serverAddress, 2020);
            serverStub = (IServerChat) registry.lookup("Servidor");
        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }

    private void disconnect() {
        try {
            UnicastRemoteObject.unexportObject(this, false);
            userStub = null;
        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        } 
    }

    private void refresh() {
        try {
            ArrayList<String> rooms = serverStub.getRooms();

            roomsList.removeAll();
            roomsList.setLayout(new BoxLayout(roomsList, BoxLayout.PAGE_AXIS));
            
            for (String room : rooms) {
                JButton button = new JButton(room);
                button.setMaximumSize(new Dimension(Short.MAX_VALUE, (int)button.getPreferredSize().getHeight()));
                button.setAlignmentX(Component.CENTER_ALIGNMENT);
                button.setHorizontalAlignment(AbstractButton.LEFT);
                button.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        if (roomStub != null) {
                            leaveRoom();
                        }
                        roomStub = getRoomStub(room);
                        if (roomStub != null) {
                            joinRoom();
                        }
                    }
                });
                roomsList.add(button);
            }

            frame.revalidate();
            frame.repaint();
        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }

    private void login() {
        usrName = JOptionPane.showInputDialog(
            frame, 
            "Choose a screen name:", 
            "Screen name selection",
            JOptionPane.PLAIN_MESSAGE);
        if (usrName == null || usrName.isEmpty()) {
            System.exit(0);
        }
        frame.setTitle("Chatter - " + usrName);
    }

    private IRoomChat getRoomStub(String room) {
        try {
            Registry registry = LocateRegistry.getRegistry(serverAddress, 2020);
            return (IRoomChat) registry.lookup(room);
        } catch (Exception exception) {
            System.err.println("Client exception: " + exception.toString());
            exception.printStackTrace();
            return null;
        }
    }

    private void createRoom(String roomName) {
        try { 
            serverStub.createRoom(roomName);
        } catch (Exception exception) {
            System.err.println("Client exception: " + exception.toString());
            exception.printStackTrace();
        }
    }

    private void joinRoom() {
        synchronized (messageArea) {
            try {
                roomStub.joinRoom(usrName, userStub);
                leaveButton.setEnabled(true);
                textField.setEditable(true);
                messageArea.setText("");
                rightPane.setEnabled(true);
                rightPane.setBorder(BorderFactory.createTitledBorder(roomStub.getRoomName()));
            } catch (Exception e) {
                System.err.println("Client exception: " + e.toString());
                e.printStackTrace();
            }
        }
    }

    private void leaveRoom() {
        synchronized (messageArea) {
            try {
                roomStub.leaveRoom(usrName);
                roomStub = null;
                leaveButton.setEnabled(false);
                textField.setEditable(false);
                messageArea.setText("");
                rightPane.setEnabled(false);
                rightPane.setBorder(BorderFactory.createTitledBorder("Room"));
            } catch (Exception e) {
                System.err.println("Client exception: " + e.toString());
                e.printStackTrace();
            }
        }
    }
    
    public void deliverMsg(String senderName, String msg) throws RemoteException {
        if (senderName == null && msg.equals("Sala fechada pelo servidor.")) {
            try {
                String room = roomStub.getRoomName();
                for (int i = roomsList.getComponents().length - 1; i >= 0; i--) {
                    JButton button = (JButton) roomsList.getComponent(i);
                    if (button.getText().equals(room)) {
                        roomsList.remove(i);
                    }
                }
                
                roomStub = null;
                leaveButton.setEnabled(false);
                textField.setEditable(false);
                messageArea.setText("");
                rightPane.setEnabled(false);
                rightPane.setBorder(BorderFactory.createTitledBorder("Room"));
                
                frame.revalidate();
                frame.repaint();
            } catch (Exception e) {
                System.err.println("Client exception: " + e.toString());
                e.printStackTrace();
            }
            return;
        }
        synchronized (messageArea) {
            messageArea.append(senderName + ": " + msg + "\n");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Pass the server IP as the sole command line argument");
            return;
        }
        var user = new UserChat(args[0]);
        user.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        user.frame.setVisible(true);
        user.connect();
        user.refresh();
        user.login();
    }
}