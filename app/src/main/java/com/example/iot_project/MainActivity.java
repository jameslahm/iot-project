package com.example.iot_project;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.SyncFailedException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.TrustAnchor;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    // current File used to save wav file before send text
    File file;
    // current mediaPlayer used to play wav
    MediaPlayer mediaPlayer;

    // Start Stop recv text button
    Button startRecvButton, stopRecvButton;

    // send text button
    Button sendTextButton;

    // send text input
    EditText sendTextInput;

    // show recv text
    EditText recvTextInput;

    // send text using samplingRate and frequency
    int samplingRate = 44100;
    int encodeFrequencyForZero = 6000;
    int encodeFrequencyForOne = 9000;

    int ThresholdDownFrequency = 5000;
    int ThresholdUpFrequency = 10000;

    int OneThresholdDownFrequency = 8000;
    int OneThresholdUpFrequency = 12000;
    int ZeroThresholdUpFrequency = 22000;
    int ZeroThresholdDownFrequency = 18000;

    double windowTime = 0.010;
    int windowWidth = (int) (windowTime * samplingRate);

    // single channel
    int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    // 16Bit
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    // isRecording
    boolean isRecving = false;
    // audio record buffer size
    int bufferSize = 4096;

    // frame args
    int PREAMBLE_BYTE_LENGTH = 1;
    byte[] PREAMBLE_BYTES = new byte[]{(byte) 0xaa};
    byte[] PREAMBLE_BITS = new byte[]{1, 0, 1, 0, 1, 0, 1, 0};

    int HEADER_BYTE_LENGTH = 1;

    FFT fft;

    byte[] preambleBits = new byte[]{-1, -1, -1, -1, -1, -1, -1, -1};
    int FFT_LEN = 512;

    // status
    boolean isPreamble = false;
    int payloadLength = -1;
    byte[] payload;
    int payloadBase = -1;
    byte[] payloadBitsBuffer = new byte[0];

    // FSK
    byte[] encodeSignalForOne;
    byte[] encodeSignalForZero;

    // Locate
    Button startLocateButton;
    Button startRecvLocateButton;

    boolean isLocateRecving = false;
    boolean isLocateSending = false;

    long TB1 = 0;
    long TB3 = 0;

    long TA3 = 0;
    long TA1 = 0;

    boolean isLocateSender = true;

    EditText accuracyTextInput;

    Button generateTextButton;


    // get permission
    private void GetPermission() {
        /*在此处插入运行时权限获取的代码*/
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
        }
    }

    void initFSK() {
        encodeSignalForOne = new byte[windowWidth * 2];
        encodeSignalForZero = new byte[windowWidth * 2];

        for (int j = 0; j < windowWidth; j++) {
            double normalizedWaveValue = Math.sin(2 * Math.PI * encodeFrequencyForOne * ((double) j / (samplingRate)));
            short waveValue = (short) (Short.MAX_VALUE * normalizedWaveValue);
            encodeSignalForOne[j * 2] = (byte) (waveValue & 0xFF);
            encodeSignalForOne[j * 2 + 1] = (byte) ((waveValue >> 8) & 0xFF);
        }
        for (int j = 0; j < windowWidth; j++) {
            double normalizedWaveValue = Math.sin(2 * Math.PI * encodeFrequencyForZero * ((double) j / (samplingRate)));
            short waveValue = (short) (Short.MAX_VALUE * normalizedWaveValue);
            encodeSignalForZero[j * 2] = (byte) (waveValue & 0xFF);
            encodeSignalForZero[j * 2 + 1] = (byte) ((waveValue >> 8) & 0xFF);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        GetPermission();

        // init FSK
        initFSK();

//        FFT_LEN = getMin2Pow((int) (1.0/2 * windowTime * samplingRate));
        FFT_LEN = getMin2Pow((int) (windowTime * samplingRate));
        fft = new FFT(FFT_LEN);

        // init button and input
        accuracyTextInput = (EditText) findViewById(R.id.show_accuracy);
        generateTextButton = (Button) findViewById(R.id.generate_text);

        startRecvButton = (Button) findViewById(R.id.start_recv_button);
        stopRecvButton = (Button) findViewById(R.id.stop_recv_button);

        sendTextButton = (Button) findViewById(R.id.send_text_button);
        sendTextInput = (EditText) findViewById(R.id.send_text_input);
        recvTextInput = (EditText) findViewById(R.id.recv_text);

        startLocateButton = (Button) findViewById(R.id.start_locate);
        startRecvLocateButton = (Button) findViewById(R.id.start_recv_locate);

        generateTextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text="askcbhdahckvdsajkcvdakbcyhdskbckadcb kacdhbahk dcbakcbsd kcahdvcbk玩法买了房卡的进来拿手机哪里都是比较卡上不上课那";
                sendTextInput.setText(text);
            }
        });

        startLocateButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void onClick(View v) {
                startLocateButton.setEnabled(false);
                // send input text
                try {
                    sendText("");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

                isLocateSender = true;
                startRecvButton.setEnabled(false);
                isLocateRecving = true;
                startLocate();
            }
        });

        startRecvLocateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startRecvButton.setEnabled(false);
                isLocateRecving = true;
                startLocate();
            }
        });

        // disable stopRecord
        stopRecvButton.setEnabled(false);

        // setup onClick
        startRecvButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // enable stop record
                stopRecvButton.setEnabled(true);
                startRecvButton.setEnabled(false);

                Thread thread = new Thread(new Runnable() {
                    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                    @Override
                    public void run() {

                        String name = getExternalFilesDir("").getAbsolutePath() + "/raw_recv.wav";
                        System.out.println(name);

                        // start recv text
                        startRecv(name);

                        // debug
                        Date now = Calendar.getInstance().getTime();
                        System.out.println(now);
                        // using time as file name
                        String filepath = getExternalFilesDir("").getAbsolutePath() + "/" + now.toString() + ".wav";
                        // write to .wav
                        copyWaveFile(name, filepath, samplingRate, bufferSize);
                    }
                });
                // start run
                thread.start();
            }
        });

        stopRecvButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // stop record
                isRecving = false;
                // enable start record
                stopRecvButton.setEnabled(false);
                startRecvButton.setEnabled(true);
            }
        });

        sendTextButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void onClick(View v) {
                // get input text
                String text = sendTextInput.getText().toString();

                // send input text
                try {
                    sendText(text);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

            }
        });
    }

    public void startLocate() {
        Thread thread = new Thread(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void run() {

                String name = getExternalFilesDir("").getAbsolutePath() + "/raw_recv.wav";
                System.out.println(name);
                // start recv text
                startRecv(name);

                // debug
                Date now = Calendar.getInstance().getTime();
                System.out.println(now);
                // using time as file name
                String filepath = getExternalFilesDir("").getAbsolutePath() + "/" + now.toString() + ".wav";
                // write to .wav
                copyWaveFile(name, filepath, samplingRate, bufferSize);
            }
        });
        // start run
        thread.start();
    }

    int getMin2Pow(int n) {
        int i = 0;
        while (true) {
            int temp = (int) Math.pow(2, i);
            if (temp >= n) {
                break;
            }
            i++;
        }
        System.out.println("Min2Pow " + Math.pow(2, i));
        return (int) Math.pow(2, i);
//        return 512;
    }

    void debugBytes(byte[] buffer) {
        for (int i = 0; i < buffer.length; i++) {
            System.out.println(i + " " + String.format("%8s", Integer.toBinaryString(buffer[i] & 0xFF)).replace(' ', '0'));
        }
    }

    void debugBits(byte[] bits) {
        for (int i = 0; i < bits.length; i++) {
            System.out.print(bits[i] + " ");
        }
        System.out.println();
    }

    // TODO: support text segment
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void sendText(String text) throws UnsupportedEncodingException {
        // first get utf-8 bytes
        byte[] textBytes = text.getBytes("UTF8");

        // add preamble and payload length
        byte[] dataBytes = new byte[textBytes.length + PREAMBLE_BYTE_LENGTH + HEADER_BYTE_LENGTH];

        // copy payload to dataBytes
        System.arraycopy(textBytes, 0, dataBytes, PREAMBLE_BYTE_LENGTH + HEADER_BYTE_LENGTH, textBytes.length);
        // add preamble
        for (int i = 0; i < PREAMBLE_BYTE_LENGTH; i++) {
            dataBytes[i] = PREAMBLE_BYTES[i];
        }
        // add header
        for (int i = PREAMBLE_BYTE_LENGTH; i < HEADER_BYTE_LENGTH + PREAMBLE_BYTE_LENGTH; i++) {
            dataBytes[i] = (byte) textBytes.length;
        }

        debugBytes(dataBytes);

        // construct bit data
        byte[] dataBits = byte2bits(dataBytes);

        // save bits to wav file
        generateWav(dataBits);

        // start play (send data)
        mediaPlayer.start();
        sendTextButton.setEnabled(false);
    }

    public byte[] byte2bits(byte[] dataBytes){
        byte[] dataBits = new byte[dataBytes.length * 8];
        for (int i = 0; i < dataBytes.length * 8; i++) {
            if ((dataBytes[i / 8] & (1 << (7 - (i % 8)))) > 0) {
                dataBits[i] = 1;
            } else {
                dataBits[i] = 0;
            }
        }
        return dataBits;
    }

    // save bits to wav file
    public void generateWav(byte[] dataBits) {
        // FSK
        int totalLength = (int) (dataBits.length * windowWidth);
        byte[] wave = new byte[totalLength * 2];
        for (int i = 0; i < dataBits.length; i++) {
            int base = (i * windowWidth) * 2;
            if (dataBits[i] == 1) {
                System.arraycopy(encodeSignalForOne, 0, wave, base, windowWidth * 2);
            } else {
                System.arraycopy(encodeSignalForZero, 0, wave, base, windowWidth * 2);
            }
        }

        // save temp file
        String filename = getExternalFilesDir("").getAbsolutePath() + "/raw.wav";
        System.out.println(filename);
        file = new File(filename);
        if (file.exists())
            file.delete();
        try {
            file.createNewFile();
        } catch (IOException e) {
            System.out.println(e);
            throw new IllegalStateException("未能创建" + file.toString());
        }
        try {
            OutputStream os = new FileOutputStream(file);
            BufferedOutputStream bos = new BufferedOutputStream(os);
            DataOutputStream dos = new DataOutputStream(bos);
            System.out.println(wave.length);
            for (int i = 0; i < wave.length; i++) {
                dos.write(wave[i]);
            }
            dos.close();
        } catch (Throwable t) {
            Log.e("MainActivity", "保存失败");
        }
        Date now = Calendar.getInstance().getTime();
        System.out.println(now);
        // using time as file name
        String filepath = getExternalFilesDir("").getAbsolutePath() + "/" + now.toString() + ".wav";
        // write to .wav
        copyWaveFile(filename, filepath, samplingRate, bufferSize);
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        // setup mediaPlayer
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                sendTextButton.setEnabled(true);
            }

        });
        try {
            File file = new File(filepath);
            FileInputStream inputStream = new FileInputStream(file);
            mediaPlayer.setDataSource(inputStream.getFD());
            mediaPlayer.prepare();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // start record
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void startRecv(String name) {
        file = new File(name);
        if (file.exists())
            file.delete();
        try {
            file.createNewFile();
        } catch (IOException e) {
            throw new IllegalStateException("未能创建" + file.toString());
        }

        try {
            OutputStream os = new FileOutputStream(file);
            BufferedOutputStream bos = new BufferedOutputStream(os);
            DataOutputStream dos = new DataOutputStream(bos);

            // get buffer size
            bufferSize = AudioRecord.getMinBufferSize(samplingRate, channelConfiguration, audioEncoding);
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, samplingRate, channelConfiguration, audioEncoding, bufferSize);

            byte[] buffer = new byte[bufferSize];
            int base = 0;

            // start record
            audioRecord.startRecording();

            isRecving = true;
            int totalRecv = 0;

            // continue reading data
            while (isRecving) {
                int bufferReadResult = audioRecord.read(buffer, base, bufferSize - base);
                for (int i = base; i < base + bufferReadResult; i++) {
                    dos.write(buffer[i]);
                    totalRecv++;
                }

                base += bufferReadResult;

                if (isPreamble) {
                    byte[] temp = new byte[payloadBitsBuffer.length + base];

                    System.arraycopy(payloadBitsBuffer, 0, temp, 0, payloadBitsBuffer.length);
                    System.arraycopy(buffer, 0, temp, payloadBitsBuffer.length, base);

                    int checkIndex = 0;
                    if (!isLocateRecving && !isLocateSending) {
                        checkIndex = processSignal(temp);
                    } else {
                        processLocate();
                    }

                    if (isPreamble) {
                        payloadBitsBuffer = new byte[temp.length - checkIndex];
//                        System.out.println("CheckIndex: "+(double)checkIndex / (windowWidth * 8 *2));
                        System.arraycopy(temp, checkIndex, payloadBitsBuffer, 0, temp.length - checkIndex);

                        base = 0;
                    } else {
                        payloadBitsBuffer = new byte[0];
                        // TODO: fix buffer size bug
                        System.arraycopy(temp, checkIndex, buffer, 0, temp.length - checkIndex);
                        base = temp.length - checkIndex;
                    }
                    continue;

                } else {
                    byte[] temp = Arrays.copyOfRange(buffer, 0, base);
                    // check if preamble
                    int checkIndex = checkPreamble(temp);

                    byte[] totalBuffer = new byte[bufferSize];
                    System.arraycopy(buffer, checkIndex, totalBuffer, 0, base - checkIndex);
                    base -= checkIndex;

//                    System.out.println("Base: "+base);

                    buffer = totalBuffer;
                }
            }
            audioRecord.stop();
            dos.close();
            System.out.println(totalRecv);
        } catch (Throwable t) {
            System.out.println(t.getMessage());
            Log.e("MainActivity", "录音失败");
        }
    }

    public void processLocate() {
        if (isLocateRecving) {
            isPreamble = false;
            isLocateRecving = false;
            if (isLocateSender) {
                TA1 = System.currentTimeMillis();
                isLocateSending = true;
            } else {
                isLocateSending = true;
                startLocate();

                TB1 = System.currentTimeMillis();
            }
        } else {
            isLocateSending = false;
            isPreamble = false;
            isRecving = false;

            if (isLocateSender) {
                TA3 = System.currentTimeMillis();
                reportLocateToServer();
            } else {
                TB3 = System.currentTimeMillis();
                reportLocateToServer();
            }
        }
    }

    void reportLocateToServer() {
        try {
            JSONObject body = new JSONObject();
            String urlPath = "http://10.0.2.2:5000/locate/";
            if(isLocateSender){
                urlPath += "A";
                body.put("TA1", TA1);
                body.put("TA3", TA3);
            }
            else{
                urlPath +="B";
                body.put("TB1", TB1);
                body.put("TB3", TB3);
            }
            URL url = new URL(urlPath);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            // 设置允许输出
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestMethod("POST");
            // 设置contentType
            conn.setRequestProperty("Content-Type", "application/json");
            DataOutputStream os = new DataOutputStream(conn.getOutputStream());
            String content = String.valueOf(body);
            os.writeBytes(content);
            os.flush();
            os.close();
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                conn.disconnect();
            }
        } catch (JSONException e) {
            Log.e("Json Error", e.getMessage());
        } catch (IOException io) {
            Log.e("Url Error", io.getMessage());
        }
    }


    byte[] signal2DataBits(byte[] buffer) {
        // transfer buffer to signal data
        double[] signalData = new double[buffer.length / 2];
        for (int i = 0; i < buffer.length / 2; i++) {
            short temp = (short) ((((short) buffer[2 * i + 1]) << 8) + buffer[2 * i]);
            signalData[i] = (double) temp / Short.MAX_VALUE;
//            System.out.println(signalData[i]);
        }


        int bitsLength = signalData.length / windowWidth;
        byte[] dataBits = new byte[bitsLength];

        for (int i = 0; i < bitsLength; i++) {
            double[] x = new double[FFT_LEN];
//            int start = (int)((i+1.0/4)*windowWidth);
//            int end = (int)((i+3.0/4)*windowWidth);
//            System.arraycopy(signalData, start, x, (int)((1.0/4) * windowWidth), end-start);
//
////          Only process 50% middle
//            for(int j=0;j<(int)((1.0/4) * windowWidth);j++){
//                x[j]=0;
//            }
////            System.out.println(x[0]);
//            for (int j = (end-start) + (int)((1.0/4) * windowWidth); j < FFT_LEN; j++) {
//                x[j] = 0;
//            }
            System.arraycopy(signalData,i * windowWidth,x,0,windowWidth);
            for(int j=windowWidth;j<FFT_LEN;j++){
                x[j]=0;
            }
            double[] y = new double[FFT_LEN];
            for (int j = 0; j < FFT_LEN; j++) {
                y[j] = 0;
            }
            fft.fft(x, y);

            int indexForOne = (int) ((double) encodeFrequencyForOne / samplingRate * FFT_LEN);
            int indexForZero = (int) ((double) encodeFrequencyForZero / samplingRate * FFT_LEN);

            double[] z = new double[FFT_LEN];

            for (int j = 0; j < FFT_LEN; j++) {
                z[j] = Math.pow(x[j], 2) + Math.pow(y[j], 2);
            }


            int argMaxIndex = argMax(z, (int) ((double) ThresholdDownFrequency / samplingRate * FFT_LEN), (int) ((double) ThresholdUpFrequency / samplingRate * FFT_LEN));

//            int argMaxZeroIndex = argMax(z, (int) ((double) ZeroThresholdDownFrequency / samplingRate * FFT_LEN), (int) ((double) ZeroThresholdUpFrequency / samplingRate * FFT_LEN));
//
//            int argMaxOneIndex = argMax(z, (int) ((double) OneThresholdDownFrequency / samplingRate * FFT_LEN), (int) ((double) OneThresholdUpFrequency / samplingRate * FFT_LEN));

//            boolean isOneMax = Math.abs(argMaxOneIndex - indexForOne) <= 2;
//            boolean isZeroMax = Math.abs(argMaxZeroIndex - indexForZero) <= 2;

//            System.out.println("One " + isOneMax);
//            System.out.println("Zero " + isZeroMax);

//            if ((isOneMax && isZeroMax) || (!isOneMax && !isZeroMax)) {
//                if (isPreamble) {
//                    if (z[indexForOne] > z[indexForZero]) {
//                        dataBits[i] = 1;
//                    } else {
//                        dataBits[i] = 0;
//                    }
//                } else {
//                    dataBits[i] = (byte) 0xff;
//                }
//            } else if (isOneMax) {
//                dataBits[i] = 1;
//                System.out.println(dataBits[i]);
//            } else {
//                dataBits[i] = 0;
//                System.out.println(dataBits[i]);
//            }

            if (Math.abs(argMaxIndex - indexForOne) <= 4 && z[argMaxIndex] >= 200) {
                dataBits[i] = 1;
            } else if (Math.abs(argMaxIndex - indexForZero) <= 4 && z[argMaxIndex] >= 200) {
                dataBits[i] = 0;
            } else {
                if (isPreamble) {
                    if (z[indexForOne] > z[indexForZero]) {
                        dataBits[i] = 1;
                    } else {
                        dataBits[i] = 0;
                    }
                    System.out.println("Attention " + i + " " + dataBits[i]);
                } else {
                    dataBits[i] = (byte) 0xff;
                }
            }
        }

        return dataBits;
    }

    byte[] bits2Byte(byte[] bits) {
        byte[] dataBytes = new byte[bits.length / 8];
        int byteValue = 0;
        for (int index = 0; index < bits.length; index++) {

            byteValue = (byteValue << 1) | bits[index];

            if (index % 8 == 7) {
                dataBytes[index / 8] = (byte) byteValue;
                byteValue = 0;
            }
        }
        return dataBytes;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    int processSignal(byte[] buffer) {
        if (!(buffer.length / (windowWidth * 2) >= 8)) {
            return 0;
        }

        byte[] dataBits = signal2DataBits(buffer);
//        int bitsLength = dataBits.length;

        // init payloadLength and payloadBase
        byte[] dataBytes = bits2Byte(dataBits);
        debugBytes(dataBytes);

        if (payloadBase == -1) {
            payloadLength = (int) dataBytes[0] & 0xff;
            System.out.println("PayloadLength: " + payloadLength);
            if (payloadLength == 0) {
                isPreamble = false;
                return 8 * windowWidth * 2;
            }
            payload = new byte[payloadLength];
            payloadBase = 0;
            if (dataBytes.length - 1 < payloadLength) {
                System.arraycopy(dataBytes, 1, payload, payloadBase, dataBytes.length - 1);
                payloadBase += dataBytes.length - 1;
                return dataBytes.length * 8 * windowWidth * 2;
            } else {
                System.arraycopy(dataBytes, 1, payload, payloadBase, payloadLength);
                payloadBase = -1;
                isPreamble = false;
                preambleBits = new byte[]{-1, -1, -1, -1, -1, -1, -1, -1};
                showRecvText();
                return (payloadLength + 1) * 8 * windowWidth * 2;
            }
        } else {
            if (dataBytes.length < payloadLength - payloadBase) {
                System.arraycopy(dataBytes, 0, payload, payloadBase, dataBytes.length);
                payloadBase += dataBytes.length;
                return dataBytes.length * 8 * windowWidth * 2;
            } else {
                System.arraycopy(dataBytes, 0, payload, payloadBase, payloadLength - payloadBase);
                int res = (payloadLength - payloadBase) * 8 * windowWidth * 2;
                payloadBase = -1;
                isPreamble = false;
                preambleBits = new byte[]{-1, -1, -1, -1, -1, -1, -1, -1};
                showRecvText();
                return res;
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    void showRecvText() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Stuff that updates the UI
                String text = new String(payload, StandardCharsets.UTF_8);
                System.out.println(text);
                recvTextInput.setText(text);

                byte[] recvBits = new byte[0];
                recvBits = byte2bits(payload);

                // show accuracy
                text = sendTextInput.getText().toString();
                // first get utf-8 bytes
                byte[] textBytes = new byte[0];
                try {
                    textBytes = text.getBytes("UTF8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

                byte[] textBits = byte2bits(textBytes);

                int accuracyBitsNum=0;
                for(int i=0;i<textBits.length && i<recvBits.length;i++){
                    if(recvBits[i]==textBits[i]){
                        accuracyBitsNum++;
                    }
                }

                System.out.println(accuracyBitsNum);

                double accuracy = (double)accuracyBitsNum / textBits.length;

                @SuppressLint("DefaultLocale") String result = String.format("%.2f",accuracy * 100);

                result += "%";

                accuracyTextInput.setText(result);

            }
        });
    }

    int argMax(double[] arr, int j, int k) {
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

    public double corr(byte[] a, byte[] b) {
        double res = 0;
        double aMod = 0;
        double bMod = 0;
        for (int i = 0; i < a.length; i++) {
            int aValue = (int) a[i];
            int bValue = (int) b[i];
            res += aValue * bValue;
            aMod += Math.pow(aValue, 2);
            bMod += Math.pow(bValue, 2);
        }

        aMod = Math.sqrt(aMod);
        bMod = Math.sqrt(bMod);

        return res / (aMod * bMod);
    }

    public int checkPreamble(byte[] buffer) {
        byte[] dataBits = signal2DataBits(buffer);
        int bitsLength = dataBits.length;


//        int currentDataBitsIndex = 0;
        for (int i = 0; i < bitsLength; i++) {
            for (int j = 0; j < preambleBits.length - 1; j++) {
                preambleBits[j] = preambleBits[j + 1];
            }
            preambleBits[preambleBits.length - 1] = dataBits[i];
            double res = corr(preambleBits, PREAMBLE_BITS);
            if (res >= 0.2 && res < 0.85) {
                debugBits(preambleBits);
            }
            if (res >= 0.9) {
                debugBits(preambleBits);
                isPreamble = true;
                return (i + 1) * windowWidth * 2;
            }
        }

        return bitsLength * windowWidth * 2;

//        System.out.println((bitsLength-currentPreambleBitsIndex)*windowWidth*2);
    }

    private void copyWaveFile(String inFileName, String outFileName, int samplingRate, int bufferSize) {
        System.out.println(inFileName);
        System.out.println(outFileName);
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;

        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = samplingRate;
        int channels = 1;
        // bytes per minute
        long byteRate = 16 * samplingRate * channels / 8;

        byte[] data = new byte[bufferSize];
        try {
            in = new FileInputStream(inFileName);
            out = new FileOutputStream(outFileName);
            // get real data length
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;
            // write .wav header
            writeWaveFileHeader(out, totalAudioLen, totalDataLen, longSampleRate, channels, byteRate);
            // write raw data to .wav
            while (in.read(data) != -1) {
                out.write(data);
            }
            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void writeWaveFileHeader(FileOutputStream out, long totalAudioLen,
                                     long totalDataLen, long longSampleRate, int channels, long byteRate)
            throws IOException {
        byte[] header = new byte[44];
        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // WAV type format = 1
        header[21] = 0;
        header[22] = (byte) channels; //single channel
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff); //sampling rate
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff); //bytes per minute
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8); // block align
        header[33] = 0;
        header[34] = 16; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff); //real data length
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }


}