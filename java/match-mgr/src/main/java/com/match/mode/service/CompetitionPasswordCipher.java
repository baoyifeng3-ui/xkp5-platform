package com.match.mode.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class CompetitionPasswordCipher {
    private final byte[] key;private final SecureRandom random=new SecureRandom();
    public CompetitionPasswordCipher(@Value("${match.competition-password-key}")String encoded){try{key=Base64.getDecoder().decode(encoded);}catch(Exception error){throw new IllegalArgumentException("比赛密码加密密钥无效",error);}if(key.length!=32)throw new IllegalArgumentException("比赛密码加密密钥必须为 256 位");}
    public String encrypt(String plain){try{byte[] iv=new byte[12];random.nextBytes(iv);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));byte[] encrypted=cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));byte[] value=new byte[iv.length+encrypted.length];System.arraycopy(iv,0,value,0,iv.length);System.arraycopy(encrypted,0,value,iv.length,encrypted.length);return Base64.getEncoder().encodeToString(value);}catch(Exception error){throw new IllegalStateException("比赛密码加密失败",error);}}
    public String decrypt(String value){try{byte[] all=Base64.getDecoder().decode(value);byte[] iv=Arrays.copyOfRange(all,0,12),encrypted=Arrays.copyOfRange(all,12,all.length);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);}catch(Exception error){throw new IllegalStateException("比赛密码解密失败",error);}}
}
