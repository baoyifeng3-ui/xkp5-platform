package com.match.environment.service;

import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;

import static org.junit.Assert.assertNotEquals;

public class TrainingEnvironmentBeanNameTest {
    @Test
    public void lifecycleServiceDoesNotCollideWithLegacyTrainingService() {
        AnnotationBeanNameGenerator generator = new AnnotationBeanNameGenerator();
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();

        String lifecycleName = generator.generateBeanName(
                new AnnotatedGenericBeanDefinition(TrainingEnvironmentService.class), registry);
        String legacyName = generator.generateBeanName(
                new AnnotatedGenericBeanDefinition(com.match.service.impl.TrainingEnvironmentService.class),
                registry);

        assertNotEquals(legacyName, lifecycleName);
    }
}
