#include "line_detector.h"
#include <android/log.h>
#include <vector>
#include <algorithm>
#include <cmath>

#define LOG_TAG "LineDetector"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

LineDetector::LineResult LineDetector::detectLine(const uint32_t* pixels, int width, int height,
                                                  const Circle& aimCircle) {
    LineResult result;

    std::vector<Point> whitePixels = findWhitePixelsInRing(pixels, width, height, aimCircle);

    if (whitePixels.empty()) {
        LOGD("No white pixels found in ring");
        return result;
    }

    Point avgPoint = calculateAveragePoint(whitePixels);
    result.endPoint = avgPoint;

    return result;
}

std::vector<LineDetector::Point> LineDetector::findWhitePixelsInRing(
    const uint32_t* pixels, int width, int height, const Circle& circle) {
    std::vector<Point> whitePixels;
    int searchRadius = 150;
    int minDist = (int)(circle.radius * 1.5f);
    int maxDist = searchRadius;

    for (int y = 0; y < height; y += 2) {
        for (int x = 0; x < width; x += 2) {
            float dx = x - circle.center.x;
            float dy = y - circle.center.y;
            float dist = std::sqrt(dx * dx + dy * dy);

            if (dist > minDist && dist < maxDist) {
                uint32_t color = pixels[y * width + x];
                if (isWhitePixel(color)) {
                    whitePixels.push_back({(float)x, (float)y});
                }
            }
        }
    }

    LOGD("Found %d white pixels", (int)whitePixels.size());
    return whitePixels;
}

LineDetector::Point LineDetector::calculateAveragePoint(const std::vector<Point>& pixels) {
    Point avg = {0, 0};
    if (pixels.empty()) return avg;

    std::vector<Point> sorted = pixels;
    std::sort(sorted.begin(), sorted.end(),
        [&](const Point& a, const Point& b) {
            float da = (a.x - 540) * (a.x - 540) + (a.y - 1188) * (a.y - 1188);
            float db = (b.x - 540) * (b.x - 540) + (b.y - 1188) * (b.y - 1188);
            return da > db;
        });

    int topN = std::min(50, (int)sorted.size());
    for (int i = 0; i < topN; i++) {
        avg.x += sorted[i].x;
        avg.y += sorted[i].y;
    }
    avg.x /= topN;
    avg.y /= topN;

    return avg;
}

bool LineDetector::isWhitePixel(uint32_t color) {
    int r = (color >> 16) & 0xFF;
    int g = (color >> 8) & 0xFF;
    int b = color & 0xFF;
    return r > 200 && g > 200 && b > 200 && std::abs(r - g) < 30 && std::abs(g - b) < 30;
}
