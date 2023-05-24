public class Pair<T, T1> {
    public T first;
    public T1 second;
    Pair(T first, T1 second){
        this.first = first;
        this.second = second;
    }
    public String toString() {
        return "[" + first.toString() + ":" + second.toString() + "]";
    }
}
