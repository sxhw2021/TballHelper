#include "ball_detector.h"
#include <android/log.h>
#include <vector>
#include <cmath>
#include <algorithm>

#define LOG_TAG "BallDetector"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

BallDetector::BallResult BallDetector::detectBalls(const uint32_t* pixels, int width, int height) {
    BallResult result;

    result.whiteBall = findBallByColor(pixels, width, height, BallColor::WHITE);
    result.targetBall = findBallByColor(pixels, width, height, BallColor::COLORED);

    return result;
}

BallDetector::Point BallDetector::findBallByColor(const uint32_t* pixels, int width, int height, BallColor colorType) {
    Point bestPoint;
    float minScore = 1e9f;

    int gridSize = 30;
    for (int y = gridSize; y < height - gridSize; y += gridSize) {
        for (int x = gridSize; x < width - gridSize; x += gridSize) {
            float score = calculateColorScore(pixels, x, y, width, height, colorType);
            if (score < minScore && score < 0.3f) {
                minScore = score;
                bestPoint = refinePosition(pixels, width, height, x, y, colorType);
            }
        }
    }

    return bestPoint;
}

float BallDetector::calculateColorScore(const uint32_t* pixels, int cx, int cy, int width, int height, BallColor colorType) {
    int sampleRadius = 20;
    int count = 0;
    int colorMatchCount = 0;

    for (int dy = -sampleRadius; dy <= sampleRadius; dy += 4) {
        for (int dx = -sampleRadius; dx <= sampleRadius; dx += 4) {
            int x = cx + dx;
            int y = cy + dy;
            if (x < 0 || x >= width || y < 0 || y >= height) continue;

            uint32_t color = pixels[y * width + x];
            if (isColorMatch(color, colorType)) {
                colorMatchCount++;
            }
            count++;
        }
    }

    return count > 0 ? 1.0f - (float)colorMatchCount / (float)count : 1.0f;
}

BallDetector::Point BallDetector::refinePosition(const uint32_t* pixels, int width, int height,
                                                 int cx, int cy, BallColor colorType) {
    float bestScore = 1e9f;
    Point best = {(float)cx, (float)cy};

    for (int dy = -20; dy <= 20; dy += 5) {
        for (int dx = -20; dx <= 20; dx += 5) {
            int x = cx + dx;
            int y = cy + dy;
            float score = calculateColorScore(pixels, x, y, width, height, colorType);
            if (score < bestScore) {
                bestScore = score;
                best = {(float)x, (float)y};
            }
        }
    }

    return best;
}

bool BallDetector::isColorMatch(uint32_t color, BallColor colorType) {
    int r = (color >> 16) & 0xFF;
    int g = (color >> 8) & 0xFF;
    int b = color & 0xFF;

    switch (colorType) {
        case BallColor::WHITE:
            return r > 200 && g > 200 && b > 200 && std::abs(r - g) < 30 && std::abs(g - b) < 30;
        case BallColor::COLORED:
            return (r > 150 || g > 150 || b > 150) && !(r > 200 && g > 200 && b > 200);
        default:
            return false;
    }
}
