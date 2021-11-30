package eu.bebendorf.bgplog;

public class AddressHelper {

    public static byte[] stringToAddress(String s) {
        String[] sa = s.split("\\.");
        if(sa.length != 4)
            return null;
        return new byte[] {
                (byte) Integer.parseInt(sa[0]),
                (byte) Integer.parseInt(sa[1]),
                (byte) Integer.parseInt(sa[2]),
                (byte) Integer.parseInt(sa[3])
        };
    }

    public static String addressToString(byte[] address) {
        return (address[0] & 0xFF) + "." + (address[1] & 0xFF) + "." +(address[2] & 0xFF) + "." +(address[3] & 0xFF);
    }

    public static long addressToLong(byte[] address) {
        long v = 0;
        for(int i=0; i<address.length; i++) {
            v |= ((long) (address[i] & 0xFF)) << ((address.length - i - 1) * 8);
        }
        return v;
    }

}
