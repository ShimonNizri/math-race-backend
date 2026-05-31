package com.example.math_race.race.bot_simulation;

import com.example.math_race.entities.UserEntity;
import com.example.math_race.repositories.AuthRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class BotDataSeeder implements CommandLineRunner {

    private final AuthRepository userRepository;
    public static final int TOTAL_BOTS_TO_CREATE = 50;

    public BotDataSeeder(AuthRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) throws Exception {

        long currentBotCount = userRepository.countByUsersBot();

        if (currentBotCount >= TOTAL_BOTS_TO_CREATE) {
            return;
        }

        List<UserEntity> botsToSave = new ArrayList<>();

        for (long i = currentBotCount + 1; i <= TOTAL_BOTS_TO_CREATE; i++) {
            UserEntity botUser = new UserEntity();
            botUser.setUsername("Bot_" + i);
            botUser.setPassword("Bot_" + i);
            botUser.setEmail("Bot_" + i + "@mathrace.local");
            botUser.setRole(UserEntity.UserRole.BOT);
            botUser.setVerified(true);
            botsToSave.add(botUser);
        }

        userRepository.saveAll(botsToSave);
    }
}
