package net.minecraft.world.level.chunk;

import javax.annotation.Nullable;
import net.minecraft.Util;

public class DataLayer {

    @Nullable
    protected byte[] data;

    public DataLayer() {}

    public DataLayer(byte[] abyte) {
        this.data = abyte;
        if (abyte.length != 2048) {
            throw (IllegalArgumentException) Util.pauseInIde(new IllegalArgumentException("ChunkNibbleArrays should be 2048 bytes not: " + abyte.length));
        }
    }

    protected DataLayer(int i) {
        this.data = new byte[i];
    }

    public int get(int i, int j, int k) {
        return this.get(this.getIndex(i, j, k));
    }

    public void set(int i, int j, int k, int l) {
        this.set(this.getIndex(i, j, k), l);
    }

    protected int getIndex(int i, int j, int k) {
        return j << 8 | k << 4 | i;
    }

    public int get(int i) { // PAIL: private -> public
        if (this.data == null) {
            return 0;
        } else {
            int j = this.getPosition(i);

            return this.data[j] >> ((i & 1) << 2) & 15; // Spigot
        }
    }

    public void set(int i, int j) { // PAIL: private -> public
        if (this.data == null) {
            this.data = new byte[2048];
        }

        int k = this.getPosition(i);

        // Spigot start
        int shift = (i & 1) << 2;
        this.data[k] = (byte) (this.data[k] & ~(15 << shift) | (j & 15) << shift);
        // Spigot end
    }

    private boolean isFirst(int i) {
        return (i & 1) == 0;
    }

    private int getPosition(int i) {
        return i >> 1;
    }

    public byte[] getData() {
        if (this.data == null) {
            this.data = new byte[2048];
        }

        return this.data;
    }

    public DataLayer copy() {
        return this.data == null ? new DataLayer() : new DataLayer((byte[]) this.data.clone());
    }

    public String toString() {
        StringBuilder stringbuilder = new StringBuilder();

        for (int i = 0; i < 4096; ++i) {
            stringbuilder.append(Integer.toHexString(this.get(i)));
            if ((i & 15) == 15) {
                stringbuilder.append("\n");
            }

            if ((i & 255) == 255) {
                stringbuilder.append("\n");
            }
        }

        return stringbuilder.toString();
    }

    public boolean isEmpty() {
        return this.data == null;
    }
}
