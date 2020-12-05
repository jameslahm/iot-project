#!/bin/env python
# -*- coding:utf-8 -*-

C = 331 * 10**(-9)
dAA = 0.6
dBB = 0.1

if __name__ == "__main__":
    a=int(input("Please input TA3-TA1:\n"))
    b=int(input("Please input TB3-TB1:\n"))
    dis = C/2*(a-b)
    print(dis)
