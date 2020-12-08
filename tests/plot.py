import numpy as np
import matplotlib.pyplot as plt

if __name__ == "__main__":
    fig = plt.figure(figsize=(16, 8))
    ax = plt.gca()

    bits_error_list = []

    with open('res.txt','r') as f:
        for line in f:
            num = float(line) * 100
            bits_error_list.append(num)
    bits_error_list.pop(-1)

    # print(bits_error_list)

    x = np.arange(len(bits_error_list))

    plt.plot(x,bits_error_list,'r-')
    # rects=plt.bar(x, bits_error_list, width=0.5)
    # for rect in rects:
    #     height = rect.get_height()
    #     ax.annotate('{:.2f}%'.format(height),
    #                 xy=(rect.get_x() + rect.get_width() / 2, height),
    #                 xytext=(0, 0.1),  # 3 points vertical offset
    #                 textcoords="offset points",
    #                 ha='center', va='bottom')
    # plt.xticks(x, [])
    plt.ylabel("Bits Error Ratio")
    plt.ylim(0,100)
    plt.title("Bits Error Ratio - Packet Index")
    plt.savefig("res.png")
    plt.show()