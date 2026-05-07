#pragma once

#include <vector>

class LineDetector {
public:
    struct Point {
        float x, y;
        Point() : x(0), y(0) {}
        Point(float _x, float _y) : x(_x), y(_y) {}
    };

    struct Circle {
        Point center;
        float radius;
        Circle() : center(), radius(0) {}
        Circle(Point c, float r) : center(c), radius(r) {}
    };

    struct LineResult {
        Point endPoint;
        bool valid;
        LineResult() : endPoint(), valid(false) {}
    };

    LineResult detectLine(const uint32_t* pixels, int width, int height, const Circle& aimCircle);

private:
    std::vector<Point> findWhitePixelsInRing(const uint32_t* pixels, int width, int height, const Circle& circle);
    Point calculateAveragePoint(const std::vector<Point>& pixels);
    bool isWhitePixel(uint32_t color);
};
