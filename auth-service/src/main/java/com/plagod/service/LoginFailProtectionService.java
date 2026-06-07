package com.plagod.service;


public interface LoginFailProtectionService {

    public void checkLocked(String account, String loginType, String requestIp);

    public void recordFailure(String account, String loginType, String requestIp);

    public void clearFailure(String account, String loginType, String requestIp);
}
