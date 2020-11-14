### 通过声音信号传输文本

#### 处理流程

###### 发送端

- 获取用户输入文本
- 将文本编码为字节
- 将传输数据包装为帧
- 调制帧为声音信号
- 播放声音信号

###### 接收端

- 开始监听声音信号
- 解码声音信号，匹配前导码
- 定位帧位置，获取包头及传输数据
- 校验帧数据
- 解码传输数据，获取传输文本



#### 功能实现

- 编码文本部分：将输入文本使用`utf-8`进行编码成字节（可参考 https://github.com/sunziping2016/SoundMessage/blob/1a64021e57933b2c3a3bcb0ebd200653a5d7754b/app/src/main/java/io/szp/soundmessage/SignalProcessing.java#L210 ）
- 生成帧数据部分：按照帧格式，形成帧数据，包括前导码，校验码等（可参考https://github.com/sunziping2016/SoundMessage/blob/1a64021e57933b2c3a3bcb0ebd200653a5d7754b/app/src/main/java/io/szp/soundmessage/MainActivity.java#L525 ）
- 调制部分：按照调制协议格式，将帧数据调制为帧信号（可参考https://github.com/sunziping2016/SoundMessage/blob/1a64021e57933b2c3a3bcb0ebd200653a5d7754b/app/src/main/java/io/szp/soundmessage/MainActivity.java#L525 ）
- 发送部分：使用`MediaPlayer`或者`AudioTrack`进行播放（可参考https://github.com/sunziping2016/SoundMessage/blob/1a64021e57933b2c3a3bcb0ebd200653a5d7754b/app/src/main/java/io/szp/soundmessage/MainActivity.java#L632 ）
- 接收部分：开始接收声音信号后，解码信号（可参考https://github.com/sunziping2016/SoundMessage/blob/1a64021e57933b2c3a3bcb0ebd200653a5d7754b/app/src/main/java/io/szp/soundmessage/MainActivity.java#L738 ）
- 解码部分：匹配前导码，获取传输数据后，校验，并将数据按照`utf-8`格式解码为文本（可参考https://github.com/sunziping2016/SoundMessage/blob/1a64021e57933b2c3a3bcb0ebd200653a5d7754b/app/src/main/java/io/szp/soundmessage/MainActivity.java#L777 ）



#### 参考资料

- `FFT`实现：https://stackoverflow.com/questions/9272232/fft-library-in-android-sdk
- 整体实现：https://github.com/sunziping2016/SoundMessage

## 

### 通过声音信号进行定位
