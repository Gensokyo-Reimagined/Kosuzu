package net.gensokyoreimagined.motoori;

public class KosuzuException extends RuntimeException {
    public KosuzuException(String message) {
        super(message);
    }

    public KosuzuException(String message, Throwable cause) {
        super(message, cause);
    }

    public KosuzuException(Throwable cause) {
        super(cause);
    }
}
