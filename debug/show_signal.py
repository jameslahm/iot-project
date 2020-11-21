#/bin/env python
# -*- coding: utf-8 -*-

from matplotlib import pyplot as plt
from scipy import signal,fft
from scipy.io import wavfile
import numpy as np
import sys

def decode_signal(data):
    j = 0;
    for i in range(0,int(len(data)/441)):
        d = data[i*441:(i+1)*441]
        res=fft.fft(d)
        # print(np.argmax(np.abs(res)))
        x = np.argmax(np.abs(res))
        if x==200:
            print("0",end="")
            j+=1
            if j % 8==0 and j!=0:
                print("")
        if x==100:
            print("1",end="")
            j+=1
            if j % 8==0 and j!=0:
                print("")


if __name__ == "__main__":
    filename = "example.wav"
    if len(sys.argv)>=2:
        filename = sys.argv[1]
    sampling_rate,data=wavfile.read(filename)
    print("Sampling_rate: ",sampling_rate)
    data=data/np.max(data)

    decode_signal(data)

    f, t, Zxx = signal.stft(data, sampling_rate,nperseg=441,noverlap=0)
    fig=plt.figure(figsize=(8,4))
    # print(t)
    plt.pcolormesh(t, f, np.abs(Zxx),shading='flat')
    plt.title('STFT Magnitude')
    plt.ylabel('Frequency [Hz]')
    plt.xlabel('Time [sec]')
    # plt.ylim((9000,11000))
    # plt.xlim((0,0.03))
    plt.savefig('{}_res_0.png'.format(filename))
