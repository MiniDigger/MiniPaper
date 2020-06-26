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

public class IntArrayTag extends CollectionTag<IntTag> {

    public static final TagType<IntArrayTag> TYPE = new TagType<IntArrayTag>() {
        @Override
        public IntArrayTag load(DataInput datainput, int i, NbtAccounter nbtreadlimiter) throws IOException {
            nbtreadlimiter.accountBits(192L);
            int j = datainput.readInt();
            com.google.common.base.Preconditions.checkArgument( j < 1 << 24); // Spigot

            nbtreadlimiter.accountBits(32L * (long) j);
            int[] aint = new int[j];

            for (int k = 0; k < j; ++k) {
                aint[k] = datainput.readInt();
            }

            return new IntArrayTag(aint);
        }

        @Override
        public String getName() {
            return "INT[]";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Int_Array";
        }
    };
    private int[] data;

    public IntArrayTag(int[] aint) {
        this.data = aint;
    }

    public IntArrayTag(List<Integer> list) {
        this(toArray(list));
    }

    private static int[] toArray(List<Integer> list) {
        int[] aint = new int[list.size()];

        for (int i = 0; i < list.size(); ++i) {
            Integer integer = (Integer) list.get(i);

            aint[i] = integer == null ? 0 : integer;
        }

        return aint;
    }

    @Override
    public void write(DataOutput dataoutput) throws IOException {
        dataoutput.writeInt(this.data.length);
        int[] aint = this.data;
        int i = aint.length;

        for (int j = 0; j < i; ++j) {
            int k = aint[j];

            dataoutput.writeInt(k);
        }

    }

    @Override
    public byte getId() {
        return 11;
    }

    @Override
    public TagType<IntArrayTag> getType() {
        return IntArrayTag.TYPE;
    }

    @Override
    public String toString() {
        StringBuilder stringbuilder = new StringBuilder("[I;");

        for (int i = 0; i < this.data.length; ++i) {
            if (i != 0) {
                stringbuilder.append(',');
            }

            stringbuilder.append(this.data[i]);
        }

        return stringbuilder.append(']').toString();
    }

    @Override
    public IntArrayTag copy() {
        int[] aint = new int[this.data.length];

        System.arraycopy(this.data, 0, aint, 0, this.data.length);
        return new IntArrayTag(aint);
    }

    public boolean equals(Object object) {
        return this == object ? true : object instanceof IntArrayTag && Arrays.equals(this.data, ((IntArrayTag) object).data);
    }

    public int hashCode() {
        return Arrays.hashCode(this.data);
    }

    public int[] getAsIntArray() {
        return this.data;
    }

    @Override
    public Component getPrettyDisplay(String s, int i) {
        MutableComponent ichatmutablecomponent = (new TextComponent("I")).withStyle(IntArrayTag.SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
        MutableComponent ichatmutablecomponent1 = (new TextComponent("[")).append(ichatmutablecomponent).append(";");

        for (int j = 0; j < this.data.length; ++j) {
            ichatmutablecomponent1.append(" ").append((new TextComponent(String.valueOf(this.data[j]))).withStyle(IntArrayTag.SYNTAX_HIGHLIGHTING_NUMBER));
            if (j != this.data.length - 1) {
                ichatmutablecomponent1.append(",");
            }
        }

        ichatmutablecomponent1.append("]");
        return ichatmutablecomponent1;
    }

    public int size() {
        return this.data.length;
    }

    public IntTag get(int i) {
        return IntTag.valueOf(this.data[i]);
    }

    public IntTag set(int i, IntTag nbttagint) {
        int j = this.data[i];

        this.data[i] = nbttagint.getAsInt();
        return IntTag.valueOf(j);
    }

    public void add(int i, IntTag nbttagint) {
        this.data = ArrayUtils.add(this.data, i, nbttagint.getAsInt());
    }

    @Override
    public boolean setTag(int i, Tag nbtbase) {
        if (nbtbase instanceof NumericTag) {
            this.data[i] = ((NumericTag) nbtbase).getAsInt();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addTag(int i, Tag nbtbase) {
        if (nbtbase instanceof NumericTag) {
            this.data = ArrayUtils.add(this.data, i, ((NumericTag) nbtbase).getAsInt());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public IntTag remove(int i) {
        int j = this.data[i];

        this.data = ArrayUtils.remove(this.data, i);
        return IntTag.valueOf(j);
    }

    @Override
    public byte getElementType() {
        return 3;
    }

    public void clear() {
        this.data = new int[0];
    }
}
