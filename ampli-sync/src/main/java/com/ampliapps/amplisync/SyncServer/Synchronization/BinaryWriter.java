package com.ampliapps.amplisync.SyncServer.Synchronization;

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
            out = new ObjectOutputStream(new FileOutputStream(filename));
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
}
