#/bin/env python
# -*- coding: utf-8 -*-

from matplotlib import pyplot as plt
from scipy import signal
from scipy.io import wavfile
import numpy as np
import sys

if __name__ == "__main__":
    filename = "example.wav"
    if len(sys.argv)>=2:
        filename = sys.argv[1]
    sampling_rate,data=wavfile.read(filename)
    print("Sampling_rate: ",sampling_rate)
    data=data/np.max(data)
    f, t, Zxx = signal.stft(data, sampling_rate,nperseg=441)
    fig=plt.figure(figsize=(8,4))
    plt.pcolormesh(t, f, np.abs(Zxx),shading='gouraud')
    plt.title('STFT Magnitude')
    plt.ylabel('Frequency [Hz]')
    plt.xlabel('Time [sec]')
    plt.ylim((9000,11000))
    plt.savefig('{}_res.png'.format(filename))
