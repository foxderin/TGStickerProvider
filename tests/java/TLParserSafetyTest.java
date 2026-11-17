package cc.microblock.TGStickerProvider.hook.TelegramTLParser;

import java.util.Arrays;

/** Synthetic wire fixtures, not a Telegram device/database compatibility claim. */
public final class TLParserSafetyTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    private static void rejects(Runnable operation) {
        checks++;
        try { operation.run(); } catch (IllegalArgumentException e) { return; } catch (RuntimeException e) { return; }
        throw new AssertionError("accepted malformed fixture at check " + checks);
    }
    private static void vector(SerializedData out, int size) { out.writeInt32(0x1cb5c415); out.writeInt32(size); }

    private static byte[] document() {
        SerializedData out = new SerializedData();
        out.writeInt32(0x8fd4c4d8); out.writeInt32(2);
        out.writeInt64(123); out.writeInt64(456); out.writeByteArray(new byte[0]);
        out.writeInt32(100); out.writeString("video/webm"); out.writeInt64(999);
        vector(out, 3);
        // videoSize with its optional timestamp.
        out.writeInt32(0xde33b094); out.writeInt32(1); out.writeString("v");
        out.writeInt32(32); out.writeInt32(64); out.writeInt32(12); out.writeDouble(1.25);
        // Emoji markup: bare int vector, not boxed TL constructors.
        out.writeInt32(0xf85c413c); out.writeInt64(88); vector(out, 2); out.writeInt32(1); out.writeInt32(2);
        // Sticker markup with InputStickerSetID.
        out.writeInt32(0x0da082fe); out.writeInt32(0x9de7a269); out.writeInt64(77); out.writeInt64(66);
        out.writeInt64(55); vector(out, 1); out.writeInt32(3);
        out.writeInt32(4); vector(out, 0);
        return out.toByteArray();
    }
    private static TLRPC.Document parseDocument(byte[] bytes, boolean strict) {
        SerializedData in = new SerializedData(bytes);
        TLRPC.Document doc = TLRPC.Document.TLdeserialize(in, in.readInt32(strict), strict);
        check(in.remaining() == 0);
        return doc;
    }
    private static byte[] pack(boolean legacy) {
        SerializedData out = new SerializedData();
        out.writeInt32(legacy ? 0xb60a24a6 : 0x6e153f16);
        out.writeInt32(0x2dd14edc); out.writeInt32(0);
        out.writeInt64(5); out.writeInt64(6); out.writeString("Title"); out.writeString("short_name");
        out.writeInt32(1); out.writeInt32(7);
        vector(out, 0);
        if (!legacy) vector(out, 0);
        vector(out, 1); out.writeBytes(document());
        return out.toByteArray();
    }
    public static void main(String[] args) {
        for (boolean exception : new boolean[]{true,false}) {
            rejects(() -> new SerializedData(new byte[0]).readInt32(exception));
            rejects(() -> new SerializedData(new byte[7]).readInt64(exception));
            rejects(() -> new SerializedData(new byte[]{4,65}).readByteArray(exception));
            rejects(() -> new SerializedData(new byte[]{(byte)255,0,0,0}).readByteArray(exception));
            rejects(() -> new SerializedData(new byte[]{1,65,0}).readByteArray(exception));
            rejects(() -> new SerializedData(new byte[]{1,65,1,0}).readByteArray(exception));
            rejects(() -> new SerializedData(new byte[]{1,(byte)0xff,0,0}).readString(exception));
            rejects(() -> new SerializedData(new byte[1]).readData(Integer.MAX_VALUE,exception));
            rejects(() -> new SerializedData(new byte[1]).readData(-1,exception));
            rejects(() -> TLRPC.Document.TLdeserialize(new SerializedData(new byte[0]),0x12345678,exception));
            rejects(() -> TLRPC.VideoSize.TLdeserialize(new SerializedData(new byte[0]),0x12345678,exception));
            rejects(() -> TLRPC.vectorDeserialize(new SerializedData(new byte[8]),TLRPC.Document::TLdeserialize,exception));
            SerializedData str = new SerializedData(new byte[]{3,65,66,67,0,0,0,0});
            check("ABC".equals(str.readString(exception))); check(str.getPosition()==4);
            str.skip(4); check(str.remaining()==0 && str.getPosition()==8);
            rejects(() -> str.skip(1)); rejects(() -> str.skip(-1));
            byte[] docBytes = document();
            TLRPC.Document doc = parseDocument(docBytes, exception);
            check(doc.dc_id==4 && doc.id==123 && doc.video_thumbs.size()==3 && doc.attributes.isEmpty());
            check(doc.video_thumbs.get(0).video_start_ts==1.25);
            check(doc.video_thumbs.get(1).background_colors.equals(Arrays.asList(1,2)));
            check(doc.video_thumbs.get(2).stickerset.id==77);
            SerializedData serialized = new SerializedData(); doc.serializeToStream(serialized);
            check(Arrays.equals(docBytes, serialized.toByteArray()));
            for (int length=0; length<docBytes.length; length++) {
                byte[] truncated=Arrays.copyOf(docBytes,length);
                rejects(() -> parseDocument(truncated,exception));
            }
            for (boolean legacy : new boolean[]{true,false}) {
                byte[] bytes=pack(legacy); SerializedData in=new SerializedData(bytes);
                TLRPC.TL_messages_stickerSet set=TLRPC.messages_StickerSet.TLdeserialize(in,in.readInt32(exception),exception);
                check(set.set.id==5 && set.documents.size()==1 && in.remaining()==0);
            }
            for (int count : new int[]{-1,Integer.MAX_VALUE,100001,1}) {
                SerializedData out=new SerializedData(); vector(out,count);
                rejects(() -> TLRPC.vectorDeserialize(new SerializedData(out.toByteArray()),TLRPC.Document::TLdeserialize,exception));
            }
        }
        for (int length : new int[]{0,1,253,254,255,1024}) {
            byte[] bytes=new byte[length]; Arrays.fill(bytes,(byte)42);
            SerializedData out=new SerializedData();out.writeByteArray(bytes);
            SerializedData in=new SerializedData(out.toByteArray());
            check(Arrays.equals(bytes,in.readByteArray(true)));check(in.remaining()==0);check(in.getPosition()==out.toByteArray().length);
        }
        System.out.println("TL parser safety checks passed: " + checks);
    }
}
