package net.minecraft.world.level.chunk.storage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.world.level.ChunkPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RegionFile implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final ByteBuffer PADDING_BUFFER = ByteBuffer.allocateDirect(1);
    private final FileChannel file;
    private final java.nio.file.Path externalFileDir;
    private final RegionFileVersion version;
    private final ByteBuffer header;
    private final IntBuffer offsets;
    private final IntBuffer timestamps;
    private final RegionBitmap usedSectors;

    public RegionFile(File file, File file1, boolean flag) throws IOException {
        this(file.toPath(), file1.toPath(), RegionFileVersion.VERSION_DEFLATE, flag);
    }

    public RegionFile(java.nio.file.Path java_nio_file_path, java.nio.file.Path java_nio_file_path1, RegionFileVersion regionfilecompression, boolean flag) throws IOException {
        this.header = ByteBuffer.allocateDirect(8192);
        this.usedSectors = new RegionBitmap();
        this.version = regionfilecompression;
        if (!Files.isDirectory(java_nio_file_path1, new LinkOption[0])) {
            throw new IllegalArgumentException("Expected directory, got " + java_nio_file_path1.toAbsolutePath());
        } else {
            this.externalFileDir = java_nio_file_path1;
            this.offsets = this.header.asIntBuffer();
            ((java.nio.Buffer) this.offsets).limit(1024);
            ((java.nio.Buffer) this.header).position(4096);
            this.timestamps = this.header.asIntBuffer();
            if (flag) {
                this.file = FileChannel.open(java_nio_file_path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
            } else {
                this.file = FileChannel.open(java_nio_file_path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            }

            this.usedSectors.force(0, 2);
            ((java.nio.Buffer) this.header).position(0);
            int i = this.file.read(this.header, 0L);

            if (i != -1) {
                if (i != 8192) {
                    RegionFile.LOGGER.warn("Region file {} has truncated header: {}", java_nio_file_path, i);
                }

                for (int j = 0; j < 1024; ++j) {
                    int k = this.offsets.get(j);

                    if (k != 0) {
                        int l = getSectorNumber(k);
                        int i1 = getNumSectors(k);
                        // Spigot start
                        if (i1 == 255) {
                            // We're maxed out, so we need to read the proper length from the section
                            ByteBuffer realLen = ByteBuffer.allocate(4);
                            this.file.read(realLen, l * 4096);
                            i1 = (realLen.getInt(0) + 4) / 4096 + 1;
                        }
                        // Spigot end

                        this.usedSectors.force(l, i1);
                    }
                }
            }

        }
    }

    private java.nio.file.Path getExternalChunkPath(ChunkPos chunkcoordintpair) {
        String s = "c." + chunkcoordintpair.x + "." + chunkcoordintpair.z + ".mcc";

        return this.externalFileDir.resolve(s);
    }

    @Nullable
    public synchronized DataInputStream getChunkDataInputStream(ChunkPos chunkcoordintpair) throws IOException {
        int i = this.getOffset(chunkcoordintpair);

        if (i == 0) {
            return null;
        } else {
            int j = getSectorNumber(i);
            int k = getNumSectors(i);
            // Spigot start
            if (k == 255) {
                ByteBuffer realLen = ByteBuffer.allocate(4);
                this.file.read(realLen, j * 4096);
                k = (realLen.getInt(0) + 4) / 4096 + 1;
            }
            // Spigot end
            int l = k * 4096;
            ByteBuffer bytebuffer = ByteBuffer.allocate(l);

            this.file.read(bytebuffer, (long) (j * 4096));
            ((java.nio.Buffer) bytebuffer).flip();
            if (bytebuffer.remaining() < 5) {
                RegionFile.LOGGER.error("Chunk {} header is truncated: expected {} but read {}", chunkcoordintpair, l, bytebuffer.remaining());
                return null;
            } else {
                int i1 = bytebuffer.getInt();
                byte b0 = bytebuffer.get();

                if (i1 == 0) {
                    RegionFile.LOGGER.warn("Chunk {} is allocated, but stream is missing", chunkcoordintpair);
                    return null;
                } else {
                    int j1 = i1 - 1;

                    if (isExternalStreamChunk(b0)) {
                        if (j1 != 0) {
                            RegionFile.LOGGER.warn("Chunk has both internal and external streams");
                        }

                        return this.createExternalChunkInputStream(chunkcoordintpair, getExternalChunkVersion(b0));
                    } else if (j1 > bytebuffer.remaining()) {
                        RegionFile.LOGGER.error("Chunk {} stream is truncated: expected {} but read {}", chunkcoordintpair, j1, bytebuffer.remaining());
                        return null;
                    } else if (j1 < 0) {
                        RegionFile.LOGGER.error("Declared size {} of chunk {} is negative", i1, chunkcoordintpair);
                        return null;
                    } else {
                        return this.createChunkInputStream(chunkcoordintpair, b0, createStream(bytebuffer, j1));
                    }
                }
            }
        }
    }

    private static boolean isExternalStreamChunk(byte b0) {
        return (b0 & 128) != 0;
    }

    private static byte getExternalChunkVersion(byte b0) {
        return (byte) (b0 & -129);
    }

    @Nullable
    private DataInputStream createChunkInputStream(ChunkPos chunkcoordintpair, byte b0, InputStream inputstream) throws IOException {
        RegionFileVersion regionfilecompression = RegionFileVersion.fromId(b0);

        if (regionfilecompression == null) {
            RegionFile.LOGGER.error("Chunk {} has invalid chunk stream version {}", chunkcoordintpair, b0);
            return null;
        } else {
            return new DataInputStream(new BufferedInputStream(regionfilecompression.wrap(inputstream)));
        }
    }

    @Nullable
    private DataInputStream createExternalChunkInputStream(ChunkPos chunkcoordintpair, byte b0) throws IOException {
        java.nio.file.Path java_nio_file_path = this.getExternalChunkPath(chunkcoordintpair);

        if (!Files.isRegularFile(java_nio_file_path, new LinkOption[0])) {
            RegionFile.LOGGER.error("External chunk path {} is not file", java_nio_file_path);
            return null;
        } else {
            return this.createChunkInputStream(chunkcoordintpair, b0, Files.newInputStream(java_nio_file_path));
        }
    }

    private static ByteArrayInputStream createStream(ByteBuffer bytebuffer, int i) {
        return new ByteArrayInputStream(bytebuffer.array(), bytebuffer.position(), i);
    }

    private int packSectorOffset(int i, int j) {
        return i << 8 | j;
    }

    private static int getNumSectors(int i) {
        return i & 255;
    }

    private static int getSectorNumber(int i) {
        return i >> 8;
    }

    private static int sizeToSectors(int i) {
        return (i + 4096 - 1) / 4096;
    }

    public boolean doesChunkExist(ChunkPos chunkcoordintpair) {
        int i = this.getOffset(chunkcoordintpair);

        if (i == 0) {
            return false;
        } else {
            int j = getSectorNumber(i);
            int k = getNumSectors(i);
            ByteBuffer bytebuffer = ByteBuffer.allocate(5);

            try {
                this.file.read(bytebuffer, (long) (j * 4096));
                ((java.nio.Buffer) bytebuffer).flip();
                if (bytebuffer.remaining() != 5) {
                    return false;
                } else {
                    int l = bytebuffer.getInt();
                    byte b0 = bytebuffer.get();

                    if (isExternalStreamChunk(b0)) {
                        if (!RegionFileVersion.isValidVersion(getExternalChunkVersion(b0))) {
                            return false;
                        }

                        if (!Files.isRegularFile(this.getExternalChunkPath(chunkcoordintpair), new LinkOption[0])) {
                            return false;
                        }
                    } else {
                        if (!RegionFileVersion.isValidVersion(b0)) {
                            return false;
                        }

                        if (l == 0) {
                            return false;
                        }

                        int i1 = l - 1;

                        if (i1 < 0 || i1 > 4096 * k) {
                            return false;
                        }
                    }

                    return true;
                }
            } catch (IOException ioexception) {
                return false;
            }
        }
    }

    public DataOutputStream getChunkDataOutputStream(ChunkPos chunkcoordintpair) throws IOException {
        return new DataOutputStream(new BufferedOutputStream(this.version.wrap((OutputStream) (new RegionFile.ChunkBuffer(chunkcoordintpair)))));
    }

    public void flush() throws IOException {
        this.file.force(true);
    }

    protected synchronized void write(ChunkPos chunkcoordintpair, ByteBuffer bytebuffer) throws IOException {
        int i = getOffsetIndex(chunkcoordintpair);
        int j = this.offsets.get(i);
        int k = getSectorNumber(j);
        int l = getNumSectors(j);
        int i1 = bytebuffer.remaining();
        int j1 = sizeToSectors(i1);
        int k1;
        RegionFile.CommitOp regionfile_b;

        if (j1 >= 256) {
            java.nio.file.Path java_nio_file_path = this.getExternalChunkPath(chunkcoordintpair);

            RegionFile.LOGGER.warn("Saving oversized chunk {} ({} bytes} to external file {}", chunkcoordintpair, i1, java_nio_file_path);
            j1 = 1;
            k1 = this.usedSectors.allocate(j1);
            regionfile_b = this.writeToExternalFile(java_nio_file_path, bytebuffer);
            ByteBuffer bytebuffer1 = this.createExternalStub();

            this.file.write(bytebuffer1, (long) (k1 * 4096));
        } else {
            k1 = this.usedSectors.allocate(j1);
            regionfile_b = () -> {
                Files.deleteIfExists(this.getExternalChunkPath(chunkcoordintpair));
            };
            this.file.write(bytebuffer, (long) (k1 * 4096));
        }

        int l1 = (int) (Util.getEpochMillis() / 1000L);

        this.offsets.put(i, this.packSectorOffset(k1, j1));
        this.timestamps.put(i, l1);
        this.writeHeader();
        regionfile_b.run();
        if (k != 0) {
            this.usedSectors.free(k, l);
        }

    }

    private ByteBuffer createExternalStub() {
        ByteBuffer bytebuffer = ByteBuffer.allocate(5);

        bytebuffer.putInt(1);
        bytebuffer.put((byte) (this.version.getId() | 128));
        ((java.nio.Buffer) bytebuffer).flip();
        return bytebuffer;
    }

    private RegionFile.CommitOp writeToExternalFile(java.nio.file.Path java_nio_file_path, ByteBuffer bytebuffer) throws IOException {
        java.nio.file.Path java_nio_file_path1 = Files.createTempFile(this.externalFileDir, "tmp", (String) null);
        FileChannel filechannel = FileChannel.open(java_nio_file_path1, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        Throwable throwable = null;

        try {
            ((java.nio.Buffer) bytebuffer).position(5);
            filechannel.write(bytebuffer);
        } catch (Throwable throwable1) {
            throwable = throwable1;
            throw throwable1;
        } finally {
            if (filechannel != null) {
                if (throwable != null) {
                    try {
                        filechannel.close();
                    } catch (Throwable throwable2) {
                        throwable.addSuppressed(throwable2);
                    }
                } else {
                    filechannel.close();
                }
            }

        }

        return () -> {
            Files.move(java_nio_file_path1, java_nio_file_path, StandardCopyOption.REPLACE_EXISTING);
        };
    }

    private void writeHeader() throws IOException {
        ((java.nio.Buffer) this.header).position(0);
        this.file.write(this.header, 0L);
    }

    private int getOffset(ChunkPos chunkcoordintpair) {
        return this.offsets.get(getOffsetIndex(chunkcoordintpair));
    }

    public boolean hasChunk(ChunkPos chunkcoordintpair) {
        return this.getOffset(chunkcoordintpair) != 0;
    }

    private static int getOffsetIndex(ChunkPos chunkcoordintpair) {
        return chunkcoordintpair.getRegionLocalX() + chunkcoordintpair.getRegionLocalZ() * 32;
    }

    public void close() throws IOException {
        try {
            this.padToFullSector();
        } finally {
            try {
                this.file.force(true);
            } finally {
                this.file.close();
            }
        }

    }

    private void padToFullSector() throws IOException {
        int i = (int) this.file.size();
        int j = sizeToSectors(i) * 4096;

        if (i != j) {
            ByteBuffer bytebuffer = RegionFile.PADDING_BUFFER.duplicate();

            ((java.nio.Buffer) bytebuffer).position(0);
            this.file.write(bytebuffer, (long) (j - 1));
        }

    }

    interface CommitOp {

        void run() throws IOException;
    }

    class ChunkBuffer extends ByteArrayOutputStream {

        private final ChunkPos b;

        public ChunkBuffer(ChunkPos chunkcoordintpair) {
            super(8096);
            super.write(0);
            super.write(0);
            super.write(0);
            super.write(0);
            super.write(RegionFile.this.version.getId());
            this.b = chunkcoordintpair;
        }

        public void close() throws IOException {
            ByteBuffer bytebuffer = ByteBuffer.wrap(this.buf, 0, this.count);

            bytebuffer.putInt(0, this.count - 5 + 1);
            RegionFile.this.write(this.b, bytebuffer);
        }
    }
}
