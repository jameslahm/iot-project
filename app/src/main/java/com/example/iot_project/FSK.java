package com.example.iot_project;

public class FSK {
    int fs = 44100;
    int encodeFrequencyForZero = 20000;
    int encodeFrequencyForOne = 40000;
    double windowTime = 0.010;
    static double[] encodePhaseForZero;
    static double[] encodePhaseForOne;
    int onePulseLen = 0;

    public FSK(int fs, int f0, int f1, double time){
        this.fs = fs;
        this.encodeFrequencyForZero = f0;
        this.encodeFrequencyForOne = f1;
        this.windowTime = time;
        double base = 1.0/fs;
        int total = (int)(fs * time);
        this.onePulseLen = total;
        // zero pulse
        encodePhaseForZero = new double[total];
        for(int i = 0; i < total; ++i){
            encodePhaseForZero[i] = Math.sin(2 * Math.PI * f0 * ((i + 1)*base));
        }
        // one pulse
        encodePhaseForOne = new double[total];
        for(int i = 0; i < total; ++i) {
            encodePhaseForOne[i] = Math.sin(2 * Math.PI * f1 * ((i + 1) * base));
        }
    }


    public double[] encoding(int [] data){
        int len = data.length;
        double[] res = new double[len*this.onePulseLen];
        for(int i = 0; i < len; ++i){
            if(data[i] == 0){
                System.arraycopy(encodePhaseForZero,0, res ,
                        i*this.onePulseLen, this.onePulseLen);
            }
            else if(data[i] == 1){
                System.arraycopy(encodePhaseForOne,0, res ,
                        i*this.onePulseLen, this.onePulseLen);
            }
        }
        return res;
    }

}
