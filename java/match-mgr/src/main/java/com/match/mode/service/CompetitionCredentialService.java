package com.match.mode.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.match.entity.User;
import com.match.mapper.UserMapper;
import com.match.mode.persistence.CompetitionCredentialMapper;
import com.match.mode.persistence.CompetitionCredentialRecord;
import com.match.security.PasswordCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CompetitionCredentialService {
    private static final char[] CHARACTERS="23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
    private final UserMapper users;private final CompetitionCredentialMapper credentials;private final PasswordCodec passwords;private final CompetitionPasswordCipher cipher;private final SecureRandom random;
    @org.springframework.beans.factory.annotation.Autowired public CompetitionCredentialService(UserMapper users,CompetitionCredentialMapper credentials,PasswordCodec passwords,CompetitionPasswordCipher cipher){this(users,credentials,passwords,cipher,new SecureRandom());}
    CompetitionCredentialService(UserMapper users,CompetitionCredentialMapper credentials,PasswordCodec passwords,CompetitionPasswordCipher cipher,SecureRandom random){this.users=users;this.credentials=credentials;this.passwords=passwords;this.cipher=cipher;this.random=random;}
    @Transactional public List<CompetitionCredentialRecord> activate(Long generation,int actorId){List<CompetitionCredentialRecord> result=new ArrayList<>();for(User user:users.selectList(Wrappers.<User>lambdaQuery().eq(User::getRole,"USER").eq(User::getEnabled,true))){CompetitionCredentialRecord existing=credentials.selectOne(generation,user.getUserId());if(existing!=null){result.add(existing);continue;}String plain=generate();LocalDateTime now=LocalDateTime.now();CompetitionCredentialRecord record=new CompetitionCredentialRecord();record.setCredentialId(UUID.randomUUID().toString());record.setUserId(user.getUserId());record.setModeGeneration(generation);record.setOriginalPasswordValue(user.getPassword());record.setCompetitionPasswordValue(passwords.encode(plain));record.setEncryptedPlainPassword(cipher.encrypt(plain));record.setCreatedAt(now);record.setUpdatedAt(now);credentials.insert(record);user.setPassword(record.getCompetitionPasswordValue());users.updateById(user);result.add(record);}return result;}
    @Transactional public void restore(Long generation,int actorId){for(CompetitionCredentialRecord record:credentials.selectByGeneration(generation)){User user=users.selectById(record.getUserId());if(user!=null){user.setPassword(record.getOriginalPasswordValue());users.updateById(user);}}credentials.deleteGeneration(generation);}
    @Transactional public CompetitionCredentialRecord regenerate(Long generation,Integer userId,String manual){CompetitionCredentialRecord record=credentials.selectOne(generation,userId);if(record==null)throw new IllegalArgumentException("比赛临时密码不存在");String plain=manual==null||manual.trim().isEmpty()?generate():manual.trim();if(plain.length()<6)throw new IllegalArgumentException("比赛密码至少 6 位");record.setCompetitionPasswordValue(passwords.encode(plain));record.setEncryptedPlainPassword(cipher.encrypt(plain));record.setUpdatedAt(LocalDateTime.now());credentials.updateById(record);User user=users.selectById(userId);if(user!=null){user.setPassword(record.getCompetitionPasswordValue());users.updateById(user);}return record;}
    public String plain(CompetitionCredentialRecord record){return cipher.decrypt(record.getEncryptedPlainPassword());}
    public List<CompetitionCredentialRecord> list(Long generation){return credentials.selectByGeneration(generation);}
    private String generate(){StringBuilder value=new StringBuilder(8);while(value.length()<8)value.append(CHARACTERS[random.nextInt(CHARACTERS.length)]);return value.toString();}
}
