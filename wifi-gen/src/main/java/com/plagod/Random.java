package com.plagod;

import java.security.SecureRandom;

public class Random {
    public static void main(String[] args) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder builder = new StringBuilder(64);
        for (int i = 0; i < 64; i++) {
            builder.append(chars.charAt(new SecureRandom().nextInt(chars.length())));
        }
        System.out.println(builder.toString());
    }
}
