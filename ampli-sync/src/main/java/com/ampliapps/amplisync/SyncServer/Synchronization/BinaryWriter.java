package com.ampliapps.amplisync.SyncServer.Synchronization;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class BinaryWriter {

    public void writeToBinary(String filename, Object obj) {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filename))) {
            out.writeObject(obj);
            out.flush();
        } catch (Exception e) {
            throw new SyncSessionFileException("Could not write sync session file: " + filename, e);
        }
    }


    public Object readFromBinaryFile(String filename) {
        File file = new File(filename);

        if (!file.exists()) {
            return null;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename))) {
            return ois.readObject();
        } catch (Exception e) {
            throw new SyncSessionFileException("Could not read sync session file: " + filename, e);
        }
    }


}
