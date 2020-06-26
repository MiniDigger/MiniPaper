package net.minecraft.nbt;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;

public class NbtIo {

    public static CompoundTag readCompressed(InputStream inputstream) throws IOException {
        DataInputStream datainputstream = new DataInputStream(new BufferedInputStream(new GZIPInputStream(inputstream)));
        Throwable throwable = null;

        CompoundTag nbttagcompound;

        try {
            nbttagcompound = read((DataInput) datainputstream, NbtAccounter.UNLIMITED);
        } catch (Throwable throwable1) {
            throwable = throwable1;
            throw throwable1;
        } finally {
            if (datainputstream != null) {
                if (throwable != null) {
                    try {
                        datainputstream.close();
                    } catch (Throwable throwable2) {
                        throwable.addSuppressed(throwable2);
                    }
                } else {
                    datainputstream.close();
                }
            }

        }

        return nbttagcompound;
    }

    public static void writeCompressed(CompoundTag nbttagcompound, OutputStream outputstream) throws IOException {
        DataOutputStream dataoutputstream = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(outputstream)));
        Throwable throwable = null;

        try {
            write(nbttagcompound, (DataOutput) dataoutputstream);
        } catch (Throwable throwable1) {
            throwable = throwable1;
            throw throwable1;
        } finally {
            if (dataoutputstream != null) {
                if (throwable != null) {
                    try {
                        dataoutputstream.close();
                    } catch (Throwable throwable2) {
                        throwable.addSuppressed(throwable2);
                    }
                } else {
                    dataoutputstream.close();
                }
            }

        }

    }

    public static CompoundTag read(DataInputStream datainputstream) throws IOException {
        return read((DataInput) datainputstream, NbtAccounter.UNLIMITED);
    }

    public static CompoundTag read(DataInput datainput, NbtAccounter nbtreadlimiter) throws IOException {
        // Spigot start
//        if ( datainput instanceof io.netty.buffer.ByteBufInputStream )
//        {
//            datainput = new DataInputStream(new org.spigotmc.LimitStream((InputStream) datainput, nbtreadlimiter));
//        }
        // Spigot end
        Tag nbtbase = readUnnamedTag(datainput, 0, nbtreadlimiter);

        if (nbtbase instanceof CompoundTag) {
            return (CompoundTag) nbtbase;
        } else {
            throw new IOException("Root tag must be a named compound tag");
        }
    }

    public static void write(CompoundTag nbttagcompound, DataOutput dataoutput) throws IOException {
        writeUnnamedTag((Tag) nbttagcompound, dataoutput);
    }

    private static void writeUnnamedTag(Tag nbtbase, DataOutput dataoutput) throws IOException {
        dataoutput.writeByte(nbtbase.getId());
        if (nbtbase.getId() != 0) {
            dataoutput.writeUTF("");
            nbtbase.write(dataoutput);
        }
    }

    private static Tag readUnnamedTag(DataInput datainput, int i, NbtAccounter nbtreadlimiter) throws IOException {
        byte b0 = datainput.readByte();

        if (b0 == 0) {
            return EndTag.INSTANCE;
        } else {
            datainput.readUTF();

            try {
                return TagTypes.getType(b0).load(datainput, i, nbtreadlimiter);
            } catch (IOException ioexception) {
                CrashReport crashreport = CrashReport.forThrowable(ioexception, "Loading NBT data");
                CrashReportCategory crashreportsystemdetails = crashreport.addCategory("NBT Tag");

                crashreportsystemdetails.setDetail("Tag type", (Object) b0);
                throw new ReportedException(crashreport);
            }
        }
    }
}
