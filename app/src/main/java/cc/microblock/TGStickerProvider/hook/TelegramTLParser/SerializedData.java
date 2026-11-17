package cc.microblock.TGStickerProvider.hook.TelegramTLParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;


public class SerializedData extends AbstractSerializedData {
    // Application safety limits, not a claim of supporting every Telegram DB size.
    public static final int MAX_INPUT_BYTES = 64 * 1024 * 1024;
    public static final int MAX_FIELD_BYTES = 0x00ffffff;
    protected boolean isOut = true;
    private ByteArrayOutputStream outbuf;
    private DataOutputStream out;
    private ByteArrayInputStream inbuf;
    private DataInputStream in;
    private boolean justCalc = false;
    private int len;

    public SerializedData() {
        outbuf = new ByteArrayOutputStream();
        out = new DataOutputStream(outbuf);
    }

    public SerializedData(boolean calculate) {
        if (!calculate) {
            outbuf = new ByteArrayOutputStream();
            out = new DataOutputStream(outbuf);
        }
        justCalc = calculate;
        len = 0;
    }

    public SerializedData(int size) {
        outbuf = new ByteArrayOutputStream(size);
        out = new DataOutputStream(outbuf);
    }

    public SerializedData(byte[] data) {
        if (data == null || data.length > MAX_INPUT_BYTES) throw new IllegalArgumentException("TL input exceeds limit");
        isOut = false;
        inbuf = new ByteArrayInputStream(data);
        in = new DataInputStream(inbuf);
        len = 0;
    }

    public void cleanup() {
        try {
            if (inbuf != null) {
                inbuf.close();
                inbuf = null;
            }
        } catch (Exception e) {
            System.out.println(e);
        }
        try {
            if (in != null) {
                in.close();
                in = null;
            }
        } catch (Exception e) {
            System.out.println(e);
        }
        try {
            if (outbuf != null) {
                outbuf.close();
                outbuf = null;
            }
        } catch (Exception e) {
            System.out.println(e);
        }
        try {
            if (out != null) {
                out.close();
                out = null;
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public SerializedData(File file) throws Exception {
        this(readFile(file));
    }

    private static byte[] readFile(File file) throws Exception {
        long size = file.length();
        if (size < 0 || size > MAX_INPUT_BYTES) throw new IllegalArgumentException("TL file exceeds limit");
        byte[] data = new byte[(int) size];
        try (DataInputStream input = new DataInputStream(new FileInputStream(file))) {
            input.readFully(data);
            if (input.read() != -1) throw new IllegalArgumentException("TL file changed while reading");
        }
        return data;
    }

    public void writeInt32(int x) {
        if (!justCalc) {
            writeInt32(x, out);
        } else {
            len += 4;
        }
    }

    private void writeInt32(int x, DataOutputStream out) {
        try {
            for (int i = 0; i < 4; i++) {
                out.write(x >> (i * 8));
            }
        } catch (Exception e) {
            if (true) {
                System.out.println("write int32 error");
                System.out.println(e);
            }
        }
    }

    public void writeInt64(long i) {
        if (!justCalc) {
            writeInt64(i, out);
        } else {
            len += 8;
        }
    }

    private void writeInt64(long x, DataOutputStream out) {
        try {
            for (int i = 0; i < 8; i++) {
                out.write((int) (x >> (i * 8)));
            }
        } catch (Exception e) {
            if (true) {
                System.out.println("write int64 error");
                System.out.println(e);
            }
        }
    }

    public void writeBool(boolean value) {
        if (!justCalc) {
            if (value) {
                writeInt32(0x997275b5);
            } else {
                writeInt32(0xbc799737);
            }
        } else {
            len += 4;
        }
    }

    public void writeBytes(byte[] b) {
        try {
            if (!justCalc) {
                out.write(b);
            } else {
                len += b.length;
            }
        } catch (Exception e) {
            if (true) {
                System.out.println("write raw error");
                System.out.println(e);
            }
        }
    }

    public void writeBytes(byte[] b, int offset, int count) {
        try {
            if (!justCalc) {
                out.write(b, offset, count);
            } else {
                len += count;
            }
        } catch (Exception e) {
            if (true) {
                System.out.println("write bytes error");
                System.out.println(e);
            }
        }
    }

    public void writeByte(int i) {
        try {
            if (!justCalc) {
                out.writeByte((byte) i);
            } else {
                len += 1;
            }
        } catch (Exception e) {
            if (true) {
                System.out.println("write byte error");
                System.out.println(e);
            }
        }
    }

    public void writeByte(byte b) {
        try {
            if (!justCalc) {
                out.writeByte(b);
            } else {
                len += 1;
            }
        } catch (Exception e) {
            if (true) {
                System.out.println("write byte error");
                System.out.println(e);
            }
        }
    }

    public void writeByteArray(byte[] b) {
        try {
            if (b.length <= 253) {
                if (!justCalc) {
                    out.write(b.length);
                } else {
                    len += 1;
                }
            } else {
                if (!justCalc) {
                    out.write(254);
                    out.write(b.length);
                    out.write(b.length >> 8);
                    out.write(b.length >> 16);
                } else {
                    len += 4;
                }
            }
            if (!justCalc) {
                out.write(b);
            } else {
                len += b.length;
            }
            int i = b.length <= 253 ? 1 : 4;
            while ((b.length + i) % 4 != 0) {
                if (!justCalc) {
                    out.write(0);
                } else {
                    len += 1;
                }
                i++;
            }
        } catch (Exception e) {
            if (true) {
                System.out.println("write byte array error");
                System.out.println(e);
            }
        }
    }

    public void writeString(String s) {
        try {
            writeByteArray(s.getBytes("UTF-8"));
        } catch (Exception e) {
            if (true) {
                System.out.println("write string error");
                System.out.println(e);
            }
        }
    }

    public void writeByteArray(byte[] b, int offset, int count) {
        try {
            if (count <= 253) {
                if (!justCalc) {
                    out.write(count);
                } else {
                    len += 1;
                }
            } else {
                if (!justCalc) {
                    out.write(254);
                    out.write(count);
                    out.write(count >> 8);
                    out.write(count >> 16);
                } else {
                    len += 4;
                }
            }
            if (!justCalc) {
                out.write(b, offset, count);
            } else {
                len += count;
            }
            int i = count <= 253 ? 1 : 4;
            while ((count + i) % 4 != 0) {
                if (!justCalc) {
                    out.write(0);
                } else {
                    len += 1;
                }
                i++;
            }
        } catch (Exception e) {
            if (true) {
                System.out.println("write byte array error");
                System.out.println(e);
            }
        }
    }

    public void writeDouble(double d) {
        try {
            writeInt64(Double.doubleToRawLongBits(d));
        } catch (Exception e) {
            if (true) {
                System.out.println("write double error");
                System.out.println(e);
            }
        }
    }

    public void writeFloat(float d) {
        try {
            writeInt32(Float.floatToIntBits(d));
        } catch (Exception e) {
            if (true) {
                System.out.println("write float error");
                System.out.println(e);
            }
        }
    }

    public int length() {
        if (!justCalc) {
            return isOut ? outbuf.size() : inbuf.available();
        }
        return len;
    }

    protected void set(byte[] newData) {
        if (newData == null || newData.length > MAX_INPUT_BYTES) throw new IllegalArgumentException("TL input exceeds limit");
        len = 0;
        isOut = false;
        inbuf = new ByteArrayInputStream(newData);
        in = new DataInputStream(inbuf);
    }

    public byte[] toByteArray() {
        return outbuf.toByteArray();
    }

    // This cache reader is deliberately strict even for exception=false: a
    // truncated object has no safe recovery boundary. Callers discard the row.
    private void requireAvailable(int count) {
        if (count < 0 || inbuf == null || count > inbuf.available()) {
            throw new IllegalArgumentException("Truncated or invalid TL field at byte " + len);
        }
    }

    private int unsignedByte() {
        requireAvailable(1);
        len++;
        return inbuf.read();
    }

    public void skip(int count) {
        if (justCalc) {
            if (count < 0 || len > Integer.MAX_VALUE - count) throw new IllegalArgumentException("Invalid skip");
            len += count;
            return;
        }
        requireAvailable(count);
        if (inbuf.skip(count) != count) throw new IllegalArgumentException("Truncated skip");
        len += count;
    }

    public int getPosition() { return len; }

    public boolean readBool(boolean exception) {
        int constructor = readInt32(exception);
        if (constructor == 0x997275b5) return true;
        if (constructor == 0xbc799737) return false;
        throw new IllegalArgumentException("Invalid TL boolean");
    }

    public byte readByte(boolean exception) { return (byte) unsignedByte(); }

    public void readBytes(byte[] bytes, boolean exception) {
        if (bytes == null) throw new IllegalArgumentException("Null byte destination");
        requireAvailable(bytes.length);
        if (bytes.length != 0 && inbuf.read(bytes, 0, bytes.length) != bytes.length) {
            throw new IllegalArgumentException("Truncated bytes");
        }
        len += bytes.length;
    }

    public byte[] readData(int count, boolean exception) {
        if (count < 0 || count > MAX_FIELD_BYTES) throw new IllegalArgumentException("TL byte field exceeds limit");
        requireAvailable(count);
        byte[] bytes = new byte[count];
        readBytes(bytes, exception);
        return bytes;
    }

    public String readString(boolean exception) {
        byte[] bytes = readByteArray(exception);
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            throw new IllegalArgumentException("Invalid TL UTF-8 string", e);
        }
    }

    public byte[] readByteArray(boolean exception) {
        int length = unsignedByte();
        int header = 1;
        if (length == 255) throw new IllegalArgumentException("Invalid TL length marker");
        if (length == 254) {
            requireAvailable(3);
            length = unsignedByte() | (unsignedByte() << 8) | (unsignedByte() << 16);
            header = 4;
        }
        int padding = (4 - ((length + header) & 3)) & 3;
        // Check the entire payload before allocation, including its alignment.
        requireAvailable(length + padding);
        byte[] bytes = readData(length, exception);
        // Official TL specifies zero alignment bytes; reject malformed records.
        for (int i = 0; i < padding; i++) {
            if (unsignedByte() != 0) throw new IllegalArgumentException("Invalid TL padding");
        }
        return bytes;
    }

    public double readDouble(boolean exception) { return Double.longBitsToDouble(readInt64(exception)); }
    public float readFloat(boolean exception) { return Float.intBitsToFloat(readInt32(exception)); }

    public int readInt32(boolean exception) {
        requireAvailable(4);
        int value = 0;
        for (int i = 0; i < 4; i++) value |= unsignedByte() << (8 * i);
        return value;
    }

    public long readInt64(boolean exception) {
        requireAvailable(8);
        long value = 0;
        for (int i = 0; i < 8; i++) value |= (long) unsignedByte() << (8 * i);
        return value;
    }

    @Override
    public int remaining() { return inbuf == null ? 0 : inbuf.available(); }
}
