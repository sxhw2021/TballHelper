#include "template_matcher.h"
#include <vector>
#include <cmath>

class TemplateMatcher {
public:
    std::vector<uint32_t> templatePixels;
    int templateWidth;
    int templateHeight;

    TemplateMatcher() : templateWidth(0), templateHeight(0) {}

    void setTemplate(const uint32_t* pixels, int width, int height) {
        templatePixels.assign(pixels, pixels + width * height);
        templateWidth = width;
        templateHeight = height;
    }

    float match(const uint32_t* screenPixels, int screenWidth, int screenHeight,
                int centerX, int centerY);

private:
    bool isSimilarColor(uint32_t c1, uint32_t c2, int threshold = 60);
};
