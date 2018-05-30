package cn.leo.loggerview;

import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() throws Exception {
        //assertEquals(4, 2 + 2);
        String s = "at android.os.Handler";
        boolean contains = s.contains("android.");
        assertEquals(!contains, false);
    }

    @Test
    public void test() {
        long imei = 123456789L;
        String s1 = GenerateBindNum(imei);
        long l = decodeBindNum(s1);
        System.out.println("imei:" + imei);
        System.out.println("bindNum:" + s1);
        System.out.println("decode:" + l);
    }

    private static int[] mp = new int[]{8, 6, 3, 7, 9, 4, 5, 1, 2, 0};
    private static int[] mm = new int[]{3, 5, 1, 4, 7, 0, 2, 6, 9, 8, 10};

    public String GenerateBindNum(long imei) {
        StringBuilder builder = new StringBuilder();
        long s = imei;
        int k = 0;
        int[] ms = new int[11];
        for (int i = 0; i < 11; i++) {
            k = (int) (s % 10);
            ms[mm[10 - i]] = mp[k];
            s = s / 10;
        }
        for (int i = 0; i < 11; i++) {
            builder.append(ms[i]);
        }
        return builder.toString();
    }

    public long decodeBindNum(String bindNum) {
        StringBuilder builder = new StringBuilder();
        char[] chars = bindNum.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            int k = Integer.parseInt(String.valueOf(chars[mm[i]]));
            for (int j = 0; j < 11; j++) {
                if (k == mp[j]) {
                    builder.append(j);
                    break;
                }
            }
        }
        BigInteger bigInteger = new BigInteger(builder.toString());
        return bigInteger.longValue();
    }
}