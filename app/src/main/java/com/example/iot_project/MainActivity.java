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
import android.os.health.SystemHealthManager;
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
import java.util.Random;

import biz.source_code.dsp.filter.FilterPassType;
import biz.source_code.dsp.filter.IirFilter;
import biz.source_code.dsp.filter.IirFilterCoefficients;
import biz.source_code.dsp.filter.IirFilterDesignExstrom;

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
    int encodeFrequencyForZero = 10000;
    int encodeFrequencyForOne = 6000;

    int ThresholdDownFrequency = 5000;
    int ThresholdUpFrequency = 11000;

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
    int bufferSize = 7056;

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
    byte[] payload = new byte[0];
    int payloadBase = -1;
    byte[] payloadBitsBuffer = new byte[0];

    byte[] totalPayload = new byte[0];

    // FSK
    byte[] encodeSignalForOne;
    byte[] encodeSignalForZero;

    // Locate
    Button startLocateButton;
    Button startRecvLocateButton;
    Button listenLocateButton;

    boolean isLocateRecving = false;
    boolean isLocateSending = false;
    EditText timeTextInput;

    long TB1 = 0;
    long TB3 = 0;

    long TA3 = 0;
    long TA1 = 0;

    boolean isLocateSender = false;

    EditText accuracyTextInput;

    Button generateTextButton;

    boolean isCheckAlign = true;

    int currentPreamBitsNum = 0;

    int leftFlushs = 0;
    int FRAME_ID_LENGTH = 1;
    int FRAME_MAX_LENGTH = 255;

    int leftFrames = 0;

    byte[] currentTextBytes;
    int currentFrameId;

    Button stopLocateButton;

    int TA1Align = -1;
    int TA3Align = -1;

    int TB1Align = -1;
    int TB3Align = -1;

    int TA1AbsoluteIndex = 0;
    int TB1AbsoluteIndex =0;

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
        listenLocateButton = (Button) findViewById(R.id.listen_locate);
        stopLocateButton = (Button) findViewById(R.id.stop_locate_button);
        timeTextInput = (EditText) findViewById(R.id.show_time);

        generateTextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
                Random random = new Random(0);
                StringBuffer stringBuffer = new StringBuffer();
                for (int i = 0; i < 305; i++) {
                    int number = random.nextInt(62);
                    stringBuffer.append(str.charAt(number));
                }
                String text = stringBuffer.toString();

                sendTextInput.setText(text);
            }
        });

        startLocateButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void onClick(View v) {
                startLocateButton.setEnabled(false);
                // send input text
                isLocateSender = true;
                startRecvButton.setEnabled(false);

//                try {
//                    sendText("");
//                } catch (UnsupportedEncodingException e) {
//                    e.printStackTrace();
//                }
                sendPreamble();
                startLocateButton.setEnabled(true);
            }
        });

        stopLocateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isLocateSending = false;
                isLocateRecving = false;
                isLocateSender = false;
                isRecving = false;
                TA1Align = -1;
                TA3Align = -1;

                TB1Align = -1;
                TB3Align = -1;
                TA1AbsoluteIndex = 0;
                TB1AbsoluteIndex = 0;
                preambleBits = new byte[]{-1, -1, -1, -1, -1, -1, -1, -1};
                isPreamble = false;
                isCheckAlign = true;
                currentPreamBitsNum = 0;
                startRecvButton.setEnabled(true);
                stopLocateButton.setEnabled(true);
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

        listenLocateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isLocateRecving = true;
                isLocateSender = true;
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
                leftFlushs = 0;
                payloadBase = -1;
                leftFrames = 0;
                totalPayload = new byte[0];
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

    public void sendPreamble() {
        byte[] dataBytes = new byte[1];
        for(int i=0;i<1;i++){
            dataBytes[i] = (byte) 0x55;
        }
//        dataBytes[1] = PREAMBLE_BYTES[0];
        debugBytes(dataBytes);
        byte[] dataBits = byte2bits(dataBytes);
        generateWav(dataBits);
        mediaPlayer.start();
        System.out.println("Send Preamble");
    }

    // TODO: support text segment
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void sendText(String text) throws UnsupportedEncodingException {
        // first get utf-8 bytes
        byte[] totalTextBytes = text.getBytes("UTF8");
        currentTextBytes = totalTextBytes;
        currentFrameId = 0;
        sendCurrentFrame();
    }

    public void sendCurrentFrame() {
        // save bits to wav file
        int totalFrames = (int) Math.ceil((double) currentTextBytes.length / FRAME_MAX_LENGTH);
        // add preamble and payload length
        byte[] textBytes = Arrays.copyOfRange(currentTextBytes, currentFrameId * FRAME_MAX_LENGTH, Math.min((currentFrameId + 1) * FRAME_MAX_LENGTH, currentTextBytes.length));

        byte[] dataBytes = new byte[textBytes.length + PREAMBLE_BYTE_LENGTH + HEADER_BYTE_LENGTH + FRAME_ID_LENGTH];

        // copy payload to dataBytes
        System.arraycopy(textBytes, 0, dataBytes, PREAMBLE_BYTE_LENGTH + HEADER_BYTE_LENGTH + FRAME_ID_LENGTH, textBytes.length);
        // add preamble
        for (int j = 0; j < PREAMBLE_BYTE_LENGTH; j++) {
            dataBytes[j] = PREAMBLE_BYTES[j];
        }
        // add header
        for (int j = PREAMBLE_BYTE_LENGTH; j < HEADER_BYTE_LENGTH + PREAMBLE_BYTE_LENGTH; j++) {
            dataBytes[j] = (byte) textBytes.length;
        }
        for (int j = PREAMBLE_BYTE_LENGTH + HEADER_BYTE_LENGTH; j < PREAMBLE_BYTE_LENGTH + HEADER_BYTE_LENGTH + FRAME_ID_LENGTH; j++) {
            dataBytes[j] = (byte) totalFrames;
        }

        debugBytes(dataBytes);

        // construct bit data
        byte[] dataBits = byte2bits(dataBytes);
        generateWav(dataBits);

        // start play (send data)
        if(currentFrameId==0){
            timeTextInput.setText(String.valueOf(System.currentTimeMillis()));
        }
        mediaPlayer.start();
        System.out.println("The Frame " + currentFrameId);
        currentFrameId++;
        sendTextButton.setEnabled(false);
    }

    public byte[] byte2bits(byte[] dataBytes) {
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

        System.out.println("Wave Length: " + wave.length);
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
                if (!isLocateRecving && !isLocateSending && TA3Align == -1 && TB3Align == -1) {
                    int totalFrames = (int) Math.ceil((double) currentTextBytes.length / FRAME_MAX_LENGTH);
                    if (currentFrameId < totalFrames) {
                        sendCurrentFrame();
                    }
                } else {
                    return;
                }
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
            bufferSize = Math.max(AudioRecord.getMinBufferSize(samplingRate, channelConfiguration, audioEncoding), bufferSize);
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, samplingRate, channelConfiguration, audioEncoding, bufferSize);

            byte[] buffer = new byte[bufferSize];
            int base = 0;

            // start record
            audioRecord.startRecording();

            isRecving = true;
            int totalRecv = 0;

            // continue reading data
            while (isRecving) {
                int bufferReadResult = audioRecord.read(buffer, base, Math.max(bufferSize - base, 0));
                for (int i = base; i < base + bufferReadResult; i++) {
                    dos.write(buffer[i]);
                    totalRecv++;
                }

                if (leftFlushs > 0) {
                    base = 0;
                    leftFlushs--;
                    continue;
                }

                base += bufferReadResult;

                if (isPreamble) {
                    byte[] temp = new byte[payloadBitsBuffer.length + base];

                    System.arraycopy(payloadBitsBuffer, 0, temp, 0, payloadBitsBuffer.length);
                    System.arraycopy(buffer, 0, temp, payloadBitsBuffer.length, base);

                    int checkIndex = 0;
                    if (!isLocateRecving && !isLocateSending) {
                        checkIndex = processSignal(temp);
                        System.out.println("Process Signal");
//                        checkIndex = processSignal(buffer);
                    } else {
                        System.out.println("Process Locate");
                        processLocate();
                    }

                    if (isPreamble) {
                        payloadBitsBuffer = new byte[temp.length - checkIndex];
                        System.out.println("CheckIndex: " + (double) checkIndex / (windowWidth * 8 * 2));
                        System.arraycopy(temp, checkIndex, payloadBitsBuffer, 0, temp.length - checkIndex);
                        base = 0;
                    } else {
                        // flush 2 buffer size
                        payloadBitsBuffer = new byte[0];
                        // TODO: fix buffer size bug
                        byte[] bufferTemp = new byte[Math.max(temp.length - checkIndex, bufferSize)];
                        System.arraycopy(temp, checkIndex, bufferTemp, 0, temp.length - checkIndex);
                        buffer = bufferTemp;
                        base = temp.length - checkIndex;

                        leftFlushs = 0;
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
            isCheckAlign = true;
            preambleBits = new byte[]{-1, -1, -1, -1, -1, -1, -1, -1};
            currentPreamBitsNum = 0;

            if (isLocateSender) {
//                TA1 = System.currentTimeMillis();
                TA1 = System.nanoTime();
                System.out.println("TA1:" + TA1);
                isLocateSending = true;
            } else {
                isLocateSending = true;
//                startLocate();
//                TB1 = System.currentTimeMillis();
                TB1 = System.nanoTime();
                System.out.println("TB1:" + TB1);
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        sendPreamble();
                    }
                });
                thread.start();
            }
        } else {
            isLocateSending = false;
            isPreamble = false;
//            isRecving = false;
            isCheckAlign = true;

            if (isLocateSender) {
//                TA3 = System.currentTimeMillis();
                TA3 = System.nanoTime();
//                System.out.println("TA3:"+ TA3);
                System.out.println("TA3ALIGN-TA1ALIGN: " + (double) (TA3Align - TA1Align) / (2 * windowWidth) * 10 + "ms");
                reportLocateToServer();
//                TA3Align=-1;
                TA1Align = -1;
            } else {
//                TB3 = System.currentTimeMillis();
                TB3 = System.nanoTime();
                System.out.println("TB3ALIGN-TB1ALIGN: " + (double) (TB3Align - TB1Align) / (2 * windowWidth) * 10 + "ms");
                System.out.println("TB3:" + TB3);
                reportLocateToServer();
//                TB3Align = -1;
                TB1Align = -1;
            }
        }
    }

    void reportLocateToServer() {
        if (isLocateSender) {
            System.out.println("TA3-TA1: " + (TA3 - TA1));
        } else {
            System.out.println("TB3-TB1: " + (TB3 - TB1));
        }
        showTime();

//        try {
//            JSONObject body = new JSONObject();
//            String urlPath = "http://10.0.2.2:5000/locate/";
//            if(isLocateSender){
//                urlPath += "A";
//                body.put("TA1", TA1);
//                body.put("TA3", TA3);
//            }
//            else{
//                urlPath +="B";
//                body.put("TB1", TB1);
//                body.put("TB3", TB3);
//            }
//            URL url = new URL(urlPath);
//            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//            conn.setConnectTimeout(5000);
//            // 设置允许输出
//            conn.setDoOutput(true);
//            conn.setDoInput(true);
//            conn.setRequestMethod("POST");
//            // 设置contentType
//            conn.setRequestProperty("Content-Type", "application/json");
//            DataOutputStream os = new DataOutputStream(conn.getOutputStream());
//            String content = String.valueOf(body);
//            os.writeBytes(content);
//            os.flush();
//            os.close();
//            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
//                conn.disconnect();
//            }
//        } catch (JSONException e) {
//            Log.e("Json Error", e.getMessage());
//        } catch (IOException io) {
//            Log.e("Url Error", io.getMessage());
//        }
    }

    public synchronized double[] IIRFilter(double[] signal, double[] a, double[] b) {

        double[] in = new double[b.length];
        double[] out = new double[a.length - 1];

        double[] outData = new double[signal.length];

        for (int i = 0; i < signal.length; i++) {

            System.arraycopy(in, 0, in, 1, in.length - 1);
            in[0] = signal[i];

            //calculate y based on a and b coefficients
            //and in and out.
            float y = 0;
            for (int j = 0; j < b.length; j++) {
                y += b[j] * in[j];

            }

            for (int j = 0; j < a.length - 1; j++) {
                y -= a[j + 1] * out[j];
            }

            //shift the out array
            System.arraycopy(out, 0, out, 1, out.length - 1);
            out[0] = y;

            outData[i] = y;


        }
        return outData;
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
            System.arraycopy(signalData, i * windowWidth, x, 0, windowWidth);
            for (int j = windowWidth; j < FFT_LEN; j++) {
                x[j] = 0;
            }

//            IirFilterCoefficients iirFilterCoefficients;
//            iirFilterCoefficients = IirFilterDesignExstrom.design(FilterPassType.bandpass,10, (double)ThresholdDownFrequency / samplingRate , (double)ThresholdUpFrequency / samplingRate );
//            x = IIRFilter(x,iirFilterCoefficients.a,iirFilterCoefficients.b);


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
            if (!isCheckAlign) {
                System.out.println(argMaxIndex + " " + z[argMaxIndex]);
            }
//            System.out.println(argMaxIndex + " "+z[argMaxIndex]);

            if (Math.abs(argMaxIndex - indexForOne) <= 4 && z[argMaxIndex] >= 100) {
                dataBits[i] = 1;
            } else if (Math.abs(argMaxIndex - indexForZero) <= 4 && z[argMaxIndex] >= 100) {
                dataBits[i] = 0;
            } else {
                if (isPreamble) {
                    if (max(z, indexForOne - 2, indexForOne + 3) > max(z, indexForZero - 2, indexForZero + 3)) {
                        dataBits[i] = 1;
                    } else {
                        dataBits[i] = 0;
                    }
                    System.out.println("Attention " + i + " " + dataBits[i]);
                } else {
                    dataBits[i] = (byte) 0xff;
                }
            }


//            if(isPreamble){
//                if(i % 8 ==0){
//                    dataBits[i] = 0;
//                }
//            }
        }

        if (isLocateSender) {
            if (TA1Align == 0 && TA3Align == -1) {
                TA3Align = buffer.length;
                System.out.println("Warning!!!!!!");
            }
        } else {
            if (TB1Align == 0 && TB3Align == -1) {
                TB3Align = buffer.length;
                System.out.println("Warning!!!!!!");
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
        if (!(buffer.length / (windowWidth * 2) >= 16)) {
            return 0;
        }

        byte[] dataBits = signal2DataBits(buffer);
//        int bitsLength = dataBits.length;

        // init payloadLength and payloadBase
        byte[] dataBytes = bits2Byte(dataBits);
        debugBytes(dataBytes);

        if (payloadBase == -1) {
            payloadLength = (int) dataBytes[0] & 0xff;
//            payloadLength = payloadLength & 0x000f;
            payloadBase = 0;

            if (payloadLength == 0) {
                isPreamble = false;
                return 2 * 8 * windowWidth * 2;
            }

            if (leftFrames == 0) {
                int totalFrames = (int) dataBytes[1] & 0xff;
                leftFrames = totalFrames;
                System.out.println("FrameLength: " + leftFrames);
            }
            leftFrames--;
            if (leftFrames != 0) {
                payloadLength = FRAME_MAX_LENGTH;
            }
            System.out.println("PayloadLength: " + payloadLength);

            if (leftFrames < 0) {
                isPreamble = false;
                leftFrames = 0;
                return 2 * 8 * windowWidth * 2;
            }

            System.out.println("Left Frames: " + leftFrames);

            payload = new byte[payloadLength];

            if (dataBytes.length - 2 < payloadLength) {
                System.arraycopy(dataBytes, 1, payload, payloadBase, dataBytes.length - 2);
                payloadBase += dataBytes.length - 2;
                return dataBytes.length * 8 * windowWidth * 2;
            } else {
                System.arraycopy(dataBytes, 2, payload, payloadBase, payloadLength);
                payloadBase = -1;
                isPreamble = false;
                isCheckAlign = true;
                preambleBits = new byte[]{-1, -1, -1, -1, -1, -1, -1, -1};

                currentPreamBitsNum = 0;

                byte[] temp = new byte[totalPayload.length + payload.length];
                System.arraycopy(totalPayload, 0, temp, 0, totalPayload.length);
                System.arraycopy(payload, 0, temp, totalPayload.length, payload.length);
                totalPayload = temp;

                System.out.println("DEBUG TOTALPAYLOAD");
//                debugBytes(totalPayload);

                if (leftFrames == 0) {
                    showRecvText();
                }

                return (payloadLength + 2) * 8 * windowWidth * 2;
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
                isCheckAlign = true;
                currentPreamBitsNum = 0;
                preambleBits = new byte[]{-1, -1, -1, -1, -1, -1, -1, -1};

                byte[] temp = new byte[totalPayload.length + payload.length];
                System.arraycopy(totalPayload, 0, temp, 0, totalPayload.length);
                System.arraycopy(payload, 0, temp, totalPayload.length, payload.length);

                totalPayload = temp;
                System.out.println("DEBUG TOTALPAYLOAD");
//                debugBytes(totalPayload);

                if (leftFrames == 0) {
                    showRecvText();
                }

                return res;
            }
        }
    }

    void showTime() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Stuff that updates the UI
                if (isLocateSender) {
                    timeTextInput.setText(String.valueOf((double) (TA3Align - TA1Align) / (2 * 441) * 10) + " "+String.valueOf(TA1AbsoluteIndex)+" "+String.valueOf(TA1AbsoluteIndex + TA3Align));
                    System.out.println("TA1AbsoluteIndex: "+TA1AbsoluteIndex);
                } else {
                    timeTextInput.setText(String.valueOf((double) (TB3Align - TB1Align) / (2 * 441) * 10) + " "+String.valueOf(TB1AbsoluteIndex)+" "+String.valueOf(TB1AbsoluteIndex+TB3Align));
                    System.out.println("TB1AbsoluteIndex: "+TB1AbsoluteIndex);
                }

            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    void showRecvText() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Stuff that updates the UI
                String text = new String(totalPayload, StandardCharsets.UTF_8);
                System.out.println(text);
                recvTextInput.setText(text);

                timeTextInput.setText(String.valueOf(System.currentTimeMillis()));

                byte[] recvBits = new byte[0];
                recvBits = byte2bits(totalPayload);

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

                int accuracyBitsNum = 0;
                for (int i = 0; i < textBits.length && i < recvBits.length; i++) {
                    if (recvBits[i] == textBits[i]) {
                        accuracyBitsNum++;
                    } else {
                        System.out.println("ERROR " + i);
                    }
                }

                System.out.println(accuracyBitsNum);

                double accuracy = (double) accuracyBitsNum / textBits.length;

                @SuppressLint("DefaultLocale") String result = String.format("%.2f", accuracy * 100);

                result += "%";

                accuracyTextInput.setText(result);
                System.out.println("Recv: " + recvBits.length);
                System.out.println("Send: " + textBits.length);

                totalPayload = new byte[0];
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

    public double max(double[] data, int start, int end) {
        double res = data[start];
        for (int i = start; i < end; i++) {
            if (data[i] > res) {
                res = data[i];
            }
        }
        return res;
    }

    public int[] checkAlign(byte[] buffer) {
        double[] signalData = new double[buffer.length / 2];
        for (int i = 0; i < buffer.length / 2; i++) {
            short temp = (short) ((((short) buffer[2 * i + 1]) << 8) + buffer[2 * i]);
            signalData[i] = (double) temp / Short.MAX_VALUE;
        }


        int rate = 10;

        double[] fftValues = new double[(signalData.length - windowWidth + 1) / rate];

        for (int i = 0; i < fftValues.length; i++) {
            double[] x = new double[FFT_LEN];
            System.arraycopy(signalData, i * rate, x, 0, windowWidth);
            for (int j = windowWidth; j < FFT_LEN; j++) {
                x[j] = 0;
            }
            double[] y = new double[FFT_LEN];
            for (int j = 0; j < FFT_LEN; j++) {
                y[j] = 0;
            }
            fft.fft(x, y);
            double[] z = new double[FFT_LEN];

            for (int j = 0; j < FFT_LEN; j++) {
                z[j] = Math.pow(x[j], 2) + Math.pow(y[j], 2);
            }

            int indexForZero = (int) ((double) encodeFrequencyForZero / samplingRate * FFT_LEN);


            fftValues[i] = max(z, indexForZero - 4, indexForZero + 4);
        }


        int peakIndex = argMax(fftValues, 0, fftValues.length);

//        System.out.println("PeakIndex "+peakIndex);
//        System.out.println("Value "+fftValues[peakIndex]);

        int[] res = new int[2];
        res[0] = 0;
        res[1] = fftValues.length * rate * 2;


        // Threshold
        int Threshold = isLocateRecving || isLocateSending ? 200 : 1000;
        if (isLocateSending && isLocateSender) {
            Threshold = 10;
        }
        if (isLocateSending && !isLocateSender) {
            Threshold = 100;
        }

        if (fftValues[peakIndex] <= Threshold) {
            res[0] = 0;
            res[1] = fftValues.length * rate * 2;
            if(fftValues[peakIndex]>1){
                System.out.println("Peak Value: "+fftValues[peakIndex]);
            }

            if(isLocateSender){
                if(TA1Align == 0){
                    TA3Align += fftValues.length * rate * 2;
                    System.out.println("TA3Align: "+TA3Align);
                }
                else{
                    TA1AbsoluteIndex += fftValues.length * rate * 2;
                    System.out.println("TA1AbsoluteIndex: "+TA1AbsoluteIndex);
                }
            }
            else{
                if(TB1Align==0){
                    TB3Align += fftValues.length * rate * 2;
                    System.out.println("TB3Align: "+TB3Align);
                }
                else{
                    TB1AbsoluteIndex += fftValues.length * rate * 2;
                    System.out.println("TB1AbsoluteIndex: "+TB1AbsoluteIndex);
                }
            }


//            System.out.println("Too Low");
            return res;
        }

        if(TA1Align == 0 || TB1Align ==0) {
            if (isLocateSender) {
                if (TA3Align <= windowWidth * 2 * 50) {
                    res[0] = 0;
                    res[1] = fftValues.length * rate * 2;
                    if (fftValues[peakIndex] > 1) {
                        System.out.println("Peak Value: " + fftValues[peakIndex]);
                    }
                    if (TA1Align == 0) {
                        TA3Align += fftValues.length * rate * 2;
                        System.out.println("TA3Align: " + TA3Align);
                    }
                    else{
                        TA1AbsoluteIndex += fftValues.length * rate * 2;
                        System.out.println("TA1AbsoluteIndex: "+TA1AbsoluteIndex);
                    }
//            System.out.println("Too Low");
                    return res;
                }
            } else {
                if (TB3Align <= windowWidth * 2 * 50) {
                    res[0] = 0;
                    res[1] = fftValues.length * rate * 2;
                    if (fftValues[peakIndex] > 1) {
                        System.out.println("Peak Value: " + fftValues[peakIndex]);
                    }
                    if (TB1Align == 0) {
                        TB3Align += fftValues.length * rate * 2;
                        System.out.println("TB3Align: " + TB3Align);
                    }
                    else{
                        TB1AbsoluteIndex += fftValues.length * rate * 2;
                        System.out.println("TB1AbsoluteIndex: "+TB1AbsoluteIndex);
                    }
//            System.out.println("Too Low");
                    return res;
                }
            }
        }
        while (peakIndex >= 2 * windowWidth / rate) {
            System.out.println("Left Values: " + fftValues[peakIndex - 2 * windowWidth / rate]);
            if (fftValues[peakIndex - 2 * windowWidth / rate] >= 10) {
                peakIndex = peakIndex - 2 * windowWidth / rate;
                System.out.println("Left Shift!!!");
            } else {
                break;
            }
        }

        if (peakIndex == fftValues.length - 1) {
            System.out.println("Going to Peak!");
            res[0] = 0;
            res[1] = fftValues.length * rate * 2;

            if (isLocateSender) {
                if (TA1Align == 0) {
                    TA3Align += fftValues.length * rate * 2;
                    System.out.println("TA3Align: " + TA3Align);
                }
                else{
                    TA1AbsoluteIndex += fftValues.length * rate * 2;
                    System.out.println("TA1AbsoluteIndex: "+TA1AbsoluteIndex);
                }
            } else {
                if (TB1Align == 0) {
                    TB3Align += fftValues.length * rate * 2;
                    System.out.println("TB3Align: " + TB3Align);
                }
                else{
                    TB1AbsoluteIndex += fftValues.length * rate * 2;
                    System.out.println("TB1AbsoluteIndex: "+TB1AbsoluteIndex);
                }
            }

            return res;
        }

        System.out.println("Peak Value: " + fftValues[peakIndex]);
        res[0] = 1;
        res[1] = peakIndex * 2 * rate;

        if (isLocateSender) {
            if (TA1Align == 0) {
                TA3Align += peakIndex * 2 * rate;
                System.out.println("TA3Align: " + TA3Align);
            }
            else{
                TA1AbsoluteIndex += peakIndex * 2 * rate;
                System.out.println("TA1AbsoluteIndex: "+TA1AbsoluteIndex);
            }
        } else {
            if (TB1Align == 0) {
                TB3Align += peakIndex * 2 * rate;
                System.out.println("TB3Align: " + TB3Align);
            }
            else{
                TB1AbsoluteIndex += peakIndex * 2 * rate;
                System.out.println("TB1AbsoluteIndex: "+TB1AbsoluteIndex);
            }

        }

        return res;

//        if(peakIndex >  (double)fftValues.length *7 /8){
//            res[0]=0;
//            res[1]= (int) ((peakIndex - (double)fftValues.length / 8) * 2 * 10);
//            System.out.println(fftValues.length);
//            System.out.println(Arrays.toString(res));
//            System.out.println("Check Attention!!!");
//            return  res;
//        }

//        System.out.println("Out of Bound");

//        return res;
    }


    public int checkPreamble(byte[] buffer) {
        if (isCheckAlign) {
            int[] res = checkAlign(buffer);
            if (res[0] != 0) {
                isCheckAlign = false;

                if (isLocateSender) {
                    TA1Align = 0;
                } else {
                    TB1Align = 0;
                }

                System.out.println("Align: " + res[1]);
            }
            return res[1];
        }

        byte[] dataBits = signal2DataBits(buffer);
        int bitsLength = dataBits.length;
//        debugBits(dataBits);
//        debugBits(dataBits);
        for (int i = 0; i < bitsLength; i++) {
            for (int j = 0; j < preambleBits.length - 1; j++) {
                preambleBits[j] = preambleBits[j + 1];
            }
            currentPreamBitsNum++;
            preambleBits[preambleBits.length - 1] = dataBits[i];
            double corrValue = corr(preambleBits, PREAMBLE_BITS);
//            if (corrValue >= 0.5) {
//                debugBits(preambleBits);
//            }
            if (corrValue >= 0.85 || currentPreamBitsNum == 8) {
                System.out.println("Check");
                debugBits(preambleBits);

                System.out.println("isLocateSending: " + isLocateSending);
                System.out.println("isLocateRecing: " + isLocateRecving);
                if (isLocateSending || isLocateRecving) {
                    processLocate();
                    return (i + 1) * windowWidth * 2;
                }

                isPreamble = true;
//                isCheckAlign = true;
                return (i + 1) * windowWidth * 2;
            }
        }

//        if(currentPreamBitsNum>=8){
//            isCheckAlign = true;
//            currentPreamBitsNum = 0;
//        }

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