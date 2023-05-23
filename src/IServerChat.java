import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface IServerChat extends Remote {
    public ArrayList<String> getRooms() throws RemoteException;
    public void createRoom(String roomName) throws RemoteException;
}
