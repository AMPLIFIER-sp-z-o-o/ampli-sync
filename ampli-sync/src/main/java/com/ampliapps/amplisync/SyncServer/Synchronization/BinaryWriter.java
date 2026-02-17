package com.ampliapps.amplisync.SyncServer.Synchronization;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

public class BinaryWriter {

    public void writeToBinary(String filename, Object obj) {
        File file = new File(filename);
        ObjectOutputStream out = null;
        Boolean append = false;

        try {
            if (!file.exists() || !append) out = new ObjectOutputStream(new FileOutputStream(filename));
            else out = new AppendableObjectOutputStream(new FileOutputStream(filename, append));
            out.writeObject(obj);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) out.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public Object readFromBinaryFile(String filename) {
        File file = new File(filename);

        if (file.exists()) {
            ObjectInputStream ois = null;
            try {
                ois = new ObjectInputStream(new FileInputStream(filename));
                while (true) {
                    return ois.readObject();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (ois != null) ois.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private class AppendableObjectOutputStream extends ObjectOutputStream {
        public AppendableObjectOutputStream(OutputStream out) throws IOException {
            super(out);
        }

        @Override
        protected void writeStreamHeader() throws IOException {
        }
    }
}
