import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class FFT {

    int n, m;
    static int samplingRate = 48000;
    static int encodeFrequencyForZero = 4000;
    static int encodeFrequencyForOne = 6000;
    static double windowTime = 0.025;
    static int windowWidth = (int) (windowTime * samplingRate);
    static int FFT_LEN = 2048;

    static int BUF_SIZE = windowWidth * 2;
    static int ThresholdDownFrequency = 3000;
    static int ThresholdUpFrequency = 7000;

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

    static int argMax(double[] arr, int j, int k) {
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

    public static double max(double[] data, int start, int end) {
        double res = data[start];
        for (int i = start; i < end; i++) {
            if (data[i] > res) {
                res = data[i];
            }
        }
        return res;
    }

    public static void main(String[] args) throws IOException {

        FFT fft = new FFT(FFT_LEN);

        FileInputStream in;
        try {
            in = new FileInputStream("res.wav");
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

        BufferedReader contentReader = new BufferedReader(
                new InputStreamReader(new FileInputStream("content.csv"), StandardCharsets.UTF_8));

        BufferedWriter resWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("res.txt"),StandardCharsets.UTF_8));

        int base = 0;
        int errorPacketsNum = 0;
        int totalPacketsNum = 0;

        while (true) {
            String res = contentReader.readLine();
            if(res==null){
                break;
            }
            String[] resList= res.replace(",", " ").strip().split(" ");
            totalPacketsNum ++;

            System.out.println("Currenct Pakcet: "+totalPacketsNum);


            // int id = Integer.parseInt(resList[0]);
            // int snr = Integer.parseInt(resList[1]);
            int payLoadLength = Integer.parseInt(resList[2]);
            int startIndex = Integer.parseInt(resList[3]) *2 + 28 *windowWidth *2;

            System.out.println("StartIndex: "+startIndex);
            
            int[] correctPayLoad = new int[payLoadLength];
            for(int i=0;i<payLoadLength;i++){
                correctPayLoad[i] = Integer.parseInt(resList[4+i]);
            }
            
            // locate to startIndex
            while(true){
                int bufferRead = in.read(buffer,0,Math.min(startIndex - base, BUF_SIZE));
                base += bufferRead;
                if(base == startIndex){
                    break;
                }
            }
            
            int[] payload = new int[payLoadLength];

            for(int j=0;j<payLoadLength;j++){
                try {
                    in.read(buffer,0,BUF_SIZE);
                    base += BUF_SIZE;
                } catch (IOException e) {
                    e.printStackTrace();
                }
    
                // transfer buffer to signal data
                double[] signalData = new double[buffer.length / 2];
                for (int i = 0; i < buffer.length / 2; i++) {
                    short temp2 = (short) (((short) (buffer[2 * i + 1]) << 8) + buffer[2 * i]);
                    signalData[i] = (double) temp2 / Short.MAX_VALUE;
                    // System.out.println(signalData[i]);
                }
    
                int bitsLength = signalData.length / windowWidth;
                byte[] dataBits = new byte[bitsLength];
    
                for (int i = 0; i < bitsLength; i++) {
                    double[] x = new double[FFT_LEN];
                    System.arraycopy(signalData, i * windowWidth, x, 0, windowWidth);
                    // System.out.println(windowWidth);
                    // System.out.println(FFT_LEN);
                    for (int k = windowWidth; k < FFT_LEN; k++) {
                        x[k] = 0;
                    }
                    double[] y = new double[FFT_LEN];
                    for (int k = 0; k < FFT_LEN; k++) {
                        y[k] = 0;
                    }
                    fft.fft(x, y);
    
                    int indexForOne = (int) ((double) encodeFrequencyForOne / samplingRate * FFT_LEN);
                    int indexForZero = (int) ((double) encodeFrequencyForZero / samplingRate * FFT_LEN);
    
                    // System.out.println(indexForOne);
                    // System.out.println(indexForZero);
    
                    double[] z = new double[FFT_LEN];
    
                    for (int k = 0; k < FFT_LEN; k++) {
                        z[k] = Math.pow(x[k], 2) + Math.pow(y[k], 2);
                        // System.out.println(z[j]);
                    }
    
                    int argMaxIndex = argMax(z, (int) ((double) ThresholdDownFrequency / samplingRate * FFT_LEN),
                            (int) ((double) ThresholdUpFrequency / samplingRate * FFT_LEN));
                    // int argMaxIndex = argMax(z, (int) ((double) 0 / samplingRate * FFT_LEN),
                    // (int) ((double) samplingRate / samplingRate * FFT_LEN));
                    // System.out.println(argMaxIndex + " " + z[argMaxIndex]);
    
                    // System.out.println("Hello");
                    
                    // System.out.println(z[argMaxIndex]);
                    // System.out.println(argMaxIndex);

                    if (Math.abs(argMaxIndex - indexForOne) <= 2 && z[argMaxIndex] >= 200) {
                        // System.out.println(time);
                        dataBits[i] = 1;
                        System.out.print(dataBits[i]);
                    } else if (Math.abs(argMaxIndex - indexForZero) <= 2 && z[argMaxIndex] >= 100) {
                        // System.out.println(time);
                        dataBits[i] = 0;
                        System.out.print(dataBits[i]);
                    } else {
                        System.out.println("Attention");
                        dataBits[i] = (byte) 0xff;
                    }
                }

                payload[j] = dataBits[0];
            }

            int correctBitsNum = 0;
            for(int m=0;m<payLoadLength;m++){
                if(payload[m]==correctPayLoad[m]){
                    correctBitsNum++;
                }
            }

            System.out.println("");

            System.out.println("Bit Error: "+String.format("%.2f",1-(double)correctBitsNum/payLoadLength));
            resWriter.write(String.format("%.2f\n",1-(double)correctBitsNum/payLoadLength));
            
            if(correctBitsNum != payLoadLength){
                errorPacketsNum++;
            }

            // break;
        }

        System.out.println("Packet Error: "+String.format("%.2f",(double)errorPacketsNum/totalPacketsNum));
        resWriter.write(String.format("%.2f\n",(double)errorPacketsNum/totalPacketsNum));

        try {
            in.close();
            contentReader.close();
            resWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}