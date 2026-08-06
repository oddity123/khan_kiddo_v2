package com.khankiddo.learning.knowledge;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PointDictionaryConfig {

    private static final String DICTIONARY_CLASSPATH = "knowledge/point-dictionary-v1.json";

    @Bean
    PointDictionary pointDictionary() {
        return PointDictionary.loadFromClasspath(DICTIONARY_CLASSPATH);
    }

    @Bean
    HabitCardScorer habitCardScorer(PointDictionary pointDictionary) {
        return new HabitCardScorer(pointDictionary);
    }
}
