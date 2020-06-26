package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import org.apache.commons.lang3.ArrayUtils;

public class ByteArrayTag extends CollectionTag<ByteTag> {

    public static final TagType<ByteArrayTag> TYPE = new TagType<ByteArrayTag>() {
        @Override
        public ByteArrayTag load(DataInput datainput, int i, NbtAccounter nbtreadlimiter) throws IOException {
            nbtreadlimiter.accountBits(192L);
            int j = datainput.readInt();
            com.google.common.base.Preconditions.checkArgument( j < 1 << 24); // Spigot

            nbtreadlimiter.accountBits(8L * (long) j);
            byte[] abyte = new byte[j];

            datainput.readFully(abyte);
            return new ByteArrayTag(abyte);
        }

        @Override
        public String getName() {
            return "BYTE[]";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Byte_Array";
        }
    };
    private byte[] data;

    public ByteArrayTag(byte[] abyte) {
        this.data = abyte;
    }

    public ByteArrayTag(List<Byte> list) {
        this(toArray(list));
    }

    private static byte[] toArray(List<Byte> list) {
        byte[] abyte = new byte[list.size()];

        for (int i = 0; i < list.size(); ++i) {
            Byte obyte = (Byte) list.get(i);

            abyte[i] = obyte == null ? 0 : obyte;
        }

        return abyte;
    }

    @Override
    public void write(DataOutput dataoutput) throws IOException {
        dataoutput.writeInt(this.data.length);
        dataoutput.write(this.data);
    }

    @Override
    public byte getId() {
        return 7;
    }

    @Override
    public TagType<ByteArrayTag> getType() {
        return ByteArrayTag.TYPE;
    }

    @Override
    public String toString() {
        StringBuilder stringbuilder = new StringBuilder("[B;");

        for (int i = 0; i < this.data.length; ++i) {
            if (i != 0) {
                stringbuilder.append(',');
            }

            stringbuilder.append(this.data[i]).append('B');
        }

        return stringbuilder.append(']').toString();
    }

    @Override
    public Tag copy() {
        byte[] abyte = new byte[this.data.length];

        System.arraycopy(this.data, 0, abyte, 0, this.data.length);
        return new ByteArrayTag(abyte);
    }

    public boolean equals(Object object) {
        return this == object ? true : object instanceof ByteArrayTag && Arrays.equals(this.data, ((ByteArrayTag) object).data);
    }

    public int hashCode() {
        return Arrays.hashCode(this.data);
    }

    @Override
    public Component getPrettyDisplay(String s, int i) {
        MutableComponent ichatmutablecomponent = (new TextComponent("B")).withStyle(ByteArrayTag.SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
        MutableComponent ichatmutablecomponent1 = (new TextComponent("[")).append(ichatmutablecomponent).append(";");

        for (int j = 0; j < this.data.length; ++j) {
            MutableComponent ichatmutablecomponent2 = (new TextComponent(String.valueOf(this.data[j]))).withStyle(ByteArrayTag.SYNTAX_HIGHLIGHTING_NUMBER);

            ichatmutablecomponent1.append(" ").append(ichatmutablecomponent2).append(ichatmutablecomponent);
            if (j != this.data.length - 1) {
                ichatmutablecomponent1.append(",");
            }
        }

        ichatmutablecomponent1.append("]");
        return ichatmutablecomponent1;
    }

    public byte[] getAsByteArray() {
        return this.data;
    }

    public int size() {
        return this.data.length;
    }

    public ByteTag get(int i) {
        return ByteTag.valueOf(this.data[i]);
    }

    public ByteTag set(int i, ByteTag nbttagbyte) {
        byte b0 = this.data[i];

        this.data[i] = nbttagbyte.getAsByte();
        return ByteTag.valueOf(b0);
    }

    public void add(int i, ByteTag nbttagbyte) {
        this.data = ArrayUtils.add(this.data, i, nbttagbyte.getAsByte());
    }

    @Override
    public boolean setTag(int i, Tag nbtbase) {
        if (nbtbase instanceof NumericTag) {
            this.data[i] = ((NumericTag) nbtbase).getAsByte();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addTag(int i, Tag nbtbase) {
        if (nbtbase instanceof NumericTag) {
            this.data = ArrayUtils.add(this.data, i, ((NumericTag) nbtbase).getAsByte());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public ByteTag remove(int i) {
        byte b0 = this.data[i];

        this.data = ArrayUtils.remove(this.data, i);
        return ByteTag.valueOf(b0);
    }

    @Override
    public byte getElementType() {
        return 1;
    }

    public void clear() {
        this.data = new byte[0];
    }
}
