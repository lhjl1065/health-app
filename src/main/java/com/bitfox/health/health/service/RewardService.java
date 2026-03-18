package com.bitfox.health.health.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
public class RewardService {

    @Value("${health.reward.file:src/main/resources/rewards-consumed.txt}")
    private String rewardFile;

    public int getConsumedRewards() {
        try {
            if (Files.exists(Paths.get(rewardFile))) {
                String content = Files.readString(Paths.get(rewardFile)).trim();
                return Integer.parseInt(content);
            }
        } catch (Exception e) {
            // 如果文件不存在或读取失败，返回0
        }
        return 0;
    }

    public void consumeReward() throws IOException {
        int consumed = getConsumedRewards();
        consumed += 60;
        Files.writeString(Paths.get(rewardFile), String.valueOf(consumed));
    }

    public int getAvailablePoints(double totalPoints) {
        int consumed = getConsumedRewards();
        return (int) (totalPoints - consumed);
    }

    public double getRewardProgress(double totalPoints) {
        int available = getAvailablePoints(totalPoints);
        return (available % 60) / 60.0 * 100;
    }

    public int getCompletedRewards(double totalPoints) {
        int available = getAvailablePoints(totalPoints);
        return available / 60;
    }
}
