import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class FFT {

    int n, m;
    static int samplingRate = 44100;
    static int encodeFrequencyForZero = 20000;
    static int encodeFrequencyForOne = 10000;
    static double windowTime = 0.010;
    static int windowWidth = (int) (windowTime * samplingRate);
    static int FFT_LEN = 512;

    static int BUF_SIZE = windowWidth * 4;
    static int ThresholdDownFrequency = 9000;
    static int ThresholdUpFrequency = 21000;

    // Lookup tables. Only need to recompute when size of FFT changes.
    double[] cos;
    double[] sin;

    public FFT(int n) {
        this.n = n;
        this.m = (int) (Math.log(n) / Math.log(2));

        // Make sure n is a power of 2
        if (n != (1 << m))
            throw new RuntimeException("FFT length must be power of 2");

        // pre compute tables
        cos = new double[n / 2];
        sin = new double[n / 2];

        for (int i = 0; i < n / 2; i++) {
            cos[i] = Math.cos(-2 * Math.PI * i / n);
            sin[i] = Math.sin(-2 * Math.PI * i / n);
        }

    }

    public void fft(double[] x, double[] y) {
        int i, j, k, n1, n2, a;
        double c, s, t1, t2;

        // Bit-reverse
        j = 0;
        n2 = n / 2;
        for (i = 1; i < n - 1; i++) {
            n1 = n2;
            while (j >= n1) {
                j = j - n1;
                n1 = n1 / 2;
            }
            j = j + n1;

            if (i < j) {
                t1 = x[i];
                x[i] = x[j];
                x[j] = t1;
                t1 = y[i];
                y[i] = y[j];
                y[j] = t1;
            }
        }

        // FFT
        n1 = 0;
        n2 = 1;

        for (i = 0; i < m; i++) {
            n1 = n2;
            n2 = n2 + n2;
            a = 0;

            for (j = 0; j < n1; j++) {
                c = cos[a];
                s = sin[a];
                a += 1 << (m - i - 1);

                for (k = j; k < n; k = k + n2) {
                    t1 = c * x[k + n1] - s * y[k + n1];
                    t2 = s * x[k + n1] + c * y[k + n1];
                    x[k + n1] = x[k] - t1;
                    y[k + n1] = y[k] - t2;
                    x[k] = x[k] + t1;
                    y[k] = y[k] + t2;
                }
            }
        }
    }

    static int argMax(double[] arr, int j,int k) {
        double max = arr[j];
        int maxIdx = j;
        for (int i = j; i < k; i++) {
            if (arr[i] > max) {
                max = arr[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    public static void main(String[] args) {
        FFT fft = new FFT(FFT_LEN);

        double time = 0;

        FileInputStream in;
        try {
            in = new FileInputStream("example_recv.wav");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }
        byte[] buffer = new byte[BUF_SIZE];
        try {
            in.read(buffer, 0, 44);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int num=0;
        while (true) {
            try {
                if (in.read(buffer, 0, BUF_SIZE) == -1) {
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            time += 0.02;
            // if (time < 0.7 || time > 1.1) {
            //     continue;
            // }

            // transfer buffer to signal data
            double[] signalData = new double[buffer.length / 2];
            for (int i = 0; i < buffer.length / 2; i++) {
                short temp = (short) (((short) (buffer[2 * i + 1]) << 8) + buffer[2 * i]);
                signalData[i] = (double) temp / Short.MAX_VALUE;
                // System.out.println(signalData[i]);
            }

            int bitsLength = signalData.length / windowWidth;
            byte[] dataBits = new byte[bitsLength];

            for (int i = 0; i < bitsLength; i++) {
                double[] x = new double[FFT_LEN];
                System.arraycopy(signalData, i * windowWidth, x, 0, windowWidth);
                // System.out.println(windowWidth);
                // System.out.println(FFT_LEN);
                for (int j = windowWidth; j < FFT_LEN; j++) {
                    x[j] = 0;
                }
                double[] y = new double[FFT_LEN];
                for (int j = 0; j < FFT_LEN; j++) {
                    y[j] = 0;
                }
                fft.fft(x, y);

                int indexForOne = (int) ((double) encodeFrequencyForOne / samplingRate * FFT_LEN);
                int indexForZero = (int) ((double) encodeFrequencyForZero / samplingRate * FFT_LEN);

                // System.out.println(indexForOne);
                // System.out.println(indexForZero);

                double[] z = new double[FFT_LEN];

                for (int j = 0; j < FFT_LEN; j++) {
                    z[j] = Math.pow(x[j], 2) + Math.pow(y[j], 2);
                    // System.out.println(z[j]);
                }

                int argMaxIndex = argMax(z, (int) ((double) ThresholdDownFrequency / samplingRate * FFT_LEN),(int) ((double) ThresholdUpFrequency / samplingRate * FFT_LEN));
                // System.out.println(argMaxIndex);

                // if(z[indexForOne] > z[indexForZero] && (z[indexForOne]> 2*(z[indexForOne-1] +
                // z[indexForOne+1])) ){
                // System.out.println(time);
                // dataBits[i] = 1;
                // System.out.println(dataBits[i]);
                // }
                // else if(z[indexForOne] < z[indexForZero] && (z[indexForZero]>
                // 2*(z[indexForZero-1] + z[indexForZero+1]))){
                // System.out.println(time);
                // dataBits[i] = 0;
                // System.out.println(dataBits[i]);
                // }

                if (Math.abs(argMaxIndex - indexForOne) <= 2 && z[argMaxIndex] >= 200) {
                    System.out.println(time);
                    dataBits[i] = 1;
                    System.out.println(dataBits[i]);
                    num++;
                } else if (Math.abs(argMaxIndex - indexForZero) <= 2 && z[argMaxIndex] >= 200) {
                    System.out.println(time);
                    dataBits[i] = 0;
                    System.out.println(dataBits[i]);
                    num++;
                } else {
                    System.out.println("Attention");
                    dataBits[i] = (byte) 0xff;
                }
            }
        }
        System.out.println(num);

        try {
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println(time);
        // byte[] dataBytes = new byte[dataBits.length / 8];
        // int byteValue = 0;
        // for (int index = 0; index < dataBits.length; index++) {

        // byteValue = (byteValue << 1) | dataBits[index];

        // if (index % 8 == 7) {
        // dataBytes[index / 8] = (byte) byteValue;
        // byteValue = 0;
        // }
        // }
    }
}