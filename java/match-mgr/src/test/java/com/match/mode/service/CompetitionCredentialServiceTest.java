package com.match.mode.service;

import com.match.entity.User;
import com.match.mapper.UserMapper;
import com.match.mode.persistence.CompetitionCredentialMapper;
import com.match.mode.persistence.CompetitionCredentialRecord;
import com.match.security.PasswordCodec;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.security.SecureRandom;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CompetitionCredentialServiceTest {
    private UserMapper users;private CompetitionCredentialMapper credentials;private CompetitionPasswordCipher cipher;private CompetitionCredentialService service;
    @Before public void setUp(){users=mock(UserMapper.class);credentials=mock(CompetitionCredentialMapper.class);cipher=mock(CompetitionPasswordCipher.class);service=new CompetitionCredentialService(users,credentials,new PasswordCodec(),cipher,new SecureRandom());}

    @Test public void activateStoresOriginalAndEightCharacterPassword(){User user=user(3,"original");when(users.selectList(any())).thenReturn(Collections.singletonList(user));when(cipher.encrypt(any())).thenAnswer(invocation->"enc:"+invocation.getArgument(0));service.activate(12L,1);ArgumentCaptor<CompetitionCredentialRecord> record=ArgumentCaptor.forClass(CompetitionCredentialRecord.class);verify(credentials).insert(record.capture());assertEquals("original",record.getValue().getOriginalPasswordValue());String plain=record.getValue().getEncryptedPlainPassword().substring(4);assertEquals(8,plain.length());assertTrue(plain.matches("[23456789A-HJ-NP-Za-km-z]{8}"));assertTrue(user.getPassword().startsWith("{bcrypt}"));verify(users).updateById(user);}

    @Test public void restoreUsesOriginalAndDeletesGeneration(){CompetitionCredentialRecord record=new CompetitionCredentialRecord();record.setUserId(3);record.setOriginalPasswordValue("original");when(credentials.selectByGeneration(12L)).thenReturn(Collections.singletonList(record));when(users.selectById(3)).thenReturn(user(3,"competition"));service.restore(12L,1);assertEquals("original",users.selectById(3).getPassword());verify(credentials).deleteGeneration(12L);}

    private User user(int id,String password){User value=new User();value.setUserId(id);value.setUserName("user"+id);value.setPassword(password);value.setRole("USER");value.setIsAdmin(false);value.setEnabled(true);return value;}
}
