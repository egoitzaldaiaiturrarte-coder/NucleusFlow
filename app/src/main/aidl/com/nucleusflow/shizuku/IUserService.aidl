package com.nucleusflow.shizuku;

interface IUserService {
    void destroy() = 16777114;
    void exit() = 1;
    String exec(String cmd) = 2;
    int getUid() = 3;
}
