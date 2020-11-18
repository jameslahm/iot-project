#!/bin/env python
# -*- coding: utf-8 -*-

import numpy as np
from scipy.io import wavfile

def generate_signal(time,frequency,phase,sample_frequency):
    """
    Generate signal according sample_frequency,time,frequency,phase

    :param time: total time
    :param frequency: signal frequency
    :phase: initial phase
    :param sample_frequency: sample frequency

    :return signal: signal array
    """

    t=np.linspace(0,time,sample_frequency*time);
    amplitude = np.iinfo(np.int16).max
    signal=amplitude*np.sin(2*np.pi*frequency*t+phase);
    signal=signal.astype(np.int16)

    return signal

def save_signal(signal,sample_frequency,filename):
    """
    Save signal to .wav 

    :param signal: signal data
    :param filename: save filename
    
    """
    wavfile.write(filename,sample_frequency,signal)

if __name__ == "__main__":
    save_signal(generate_signal(1,20000,0,44100),44100,"example2.wav");