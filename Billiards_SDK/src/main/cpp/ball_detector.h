#pragma once

#include <vector>

class BallDetector {
public:
    enum class BallColor { WHITE, COLORED };

    struct Point {
        float x, y;
        Point() : x(-1), y(-1) {}
        Point(float _x, float _y) : x(_x), y(_y) {}
        bool valid() const { return x > 0 && y > 0; }
    };

    struct BallResult {
        Point whiteBall;
        Point targetBall;
        bool valid() const { return whiteBall.valid() || targetBall.valid(); }
    };

    BallResult detectBalls(const uint32_t* pixels, int width, int height);

private:
    Point findBallByColor(const uint32_t* pixels, int width, int height, BallColor colorType);
    float calculateColorScore(const uint32_t* pixels, int cx, int cy, int width, int height, BallColor colorType);
    Point refinePosition(const uint32_t* pixels, int width, int height, int cx, int cy, BallColor colorType);
    bool isColorMatch(uint32_t color, BallColor colorType);
};
