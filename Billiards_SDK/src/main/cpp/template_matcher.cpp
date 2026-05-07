#include "template_matcher.h"
#include <android/log.h>
#include <cmath>
#include <algorithm>

#define LOG_TAG "TemplateMatcher"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

float TemplateMatcher::match(const uint32_t* screenPixels, int screenWidth, int screenHeight,
                              int centerX, int centerY) {
    int halfW = templateWidth / 2;
    int halfH = templateHeight / 2;

    if (centerX - halfW < 0 || centerX + halfW >= screenWidth ||
        centerY - halfH < 0 || centerY + halfH >= screenHeight) {
        return 0.0f;
    }

    int matchCount = 0;
    int totalCount = 0;

    for (int ty = 0; ty < templateHeight; ty += 2) {
        for (int tx = 0; tx < templateWidth; tx += 2) {
            int sx = centerX - halfW + tx;
            int sy = centerY - halfH + ty;

            uint32_t tmplColor = templatePixels[ty * templateWidth + tx];
            uint32_t screenColor = screenPixels[sy * screenWidth + sx];

            if (isSimilarColor(tmplColor, screenColor, 60)) {
                matchCount++;
            }
            totalCount++;
        }
    }

    return totalCount > 0 ? (float)matchCount / (float)totalCount : 0.0f;
}

bool TemplateMatcher::isSimilarColor(uint32_t c1, uint32_t c2, int threshold) {
    int r1 = (c1 >> 16) & 0xFF;
    int g1 = (c1 >> 8) & 0xFF;
    int b1 = c1 & 0xFF;
    int r2 = (c2 >> 16) & 0xFF;
    int g2 = (c2 >> 8) & 0xFF;
    int b2 = c2 & 0xFF;

    int diff = std::abs(r1 - r2) + std::abs(g1 - g2) + std::abs(b1 - b2);
    return diff < threshold;
}
