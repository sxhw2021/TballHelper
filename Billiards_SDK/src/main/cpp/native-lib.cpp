#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <cmath>
#include <vector>
#include <queue>
#include <algorithm>
#include <string>

#define LOG_TAG "BilliardsSDK"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct Point2f {
    float x, y;
    Point2f() : x(0), y(0) {}
    Point2f(float _x, float _y) : x(_x), y(_y) {}
};

struct Circle {
    Point2f center;
    float radius;
    Circle() : center(), radius(0) {}
    Circle(Point2f c, float r) : center(c), radius(r) {}
};

struct ProcessingResult {
    Point2f templateCenter;
    Point2f aimPoint;
    Point2f whiteBall;
    Point2f targetBall;
    float confidence;
};

class BilliardsEngine {
private:
    std::vector<uint32_t> templatePixels;
    int templateWidth;
    int templateHeight;
    int screenWidth;
    int screenHeight;

public:
    BilliardsEngine() : templateWidth(0), templateHeight(0), screenWidth(0), screenHeight(0) {}

    void setTemplate(const uint32_t* pixels, int width, int height) {
        templatePixels.assign(pixels, pixels + width * height);
        templateWidth = width;
        templateHeight = height;
        LOGD("Template set: %dx%d", width, height);
    }

    ProcessingResult processFrame(const uint32_t* pixels, int width, int height) {
        ProcessingResult result;
        screenWidth = width;
        screenHeight = height;

        if (templatePixels.empty()) {
            LOGD("No template set, skipping");
            return result;
        }

        Circle aimCircle = findAimingCircle(pixels, width, height);
        if (aimCircle.radius > 0) {
            result.templateCenter = aimCircle.center;
            result.confidence = 0.8f;

            Point2f aimPoint = findAimLineEnd(pixels, width, height, aimCircle);
            if (aimPoint.x > 0 && aimPoint.y > 0) {
                result.aimPoint = aimPoint;
            }
        }

        return result;
    }

private:
    Circle findAimingCircle(const uint32_t* pixels, int width, int height) {
        Circle bestCircle;
        float bestMatch = 0.85f;
        int step = 20;

        for (int y = templateHeight / 2; y < height - templateHeight / 2; y += step) {
            for (int x = templateWidth / 2; x < width - templateWidth / 2; x += step) {
                float match = templateMatch(pixels, width, height, x, y);
                if (match > bestMatch) {
                    bestMatch = match;
                    bestCircle.center = Point2f((float)x, (float)y);
                    bestCircle.radius = (float)templateWidth / 2.f;
                }
            }
        }

        if (bestCircle.radius > 0) {
            for (int dy = -30; dy <= 30; dy += 10) {
                for (int dx = -30; dx <= 30; dx += 10) {
                    int nx = (int)bestCircle.center.x + dx;
                    int ny = (int)bestCircle.center.y + dy;
                    float match = templateMatch(pixels, width, height, nx, ny);
                    if (match > bestMatch) {
                        bestMatch = match;
                        bestCircle.center = Point2f((float)nx, (float)ny);
                    }
                }
            }
        }

        LOGD("Best match: %.2f at (%.0f, %.0f)", bestMatch, bestCircle.center.x, bestCircle.center.y);
        return bestCircle;
    }

    float templateMatch(const uint32_t* pixels, int width, int height, int centerX, int centerY) {
        int halfW = templateWidth / 2;
        int halfH = templateHeight / 2;

        if (centerX - halfW < 0 || centerX + halfW >= width ||
            centerY - halfH < 0 || centerY + halfH >= height) {
            return 0.0f;
        }

        int matchCount = 0;
        int totalCount = 0;

        for (int ty = 0; ty < templateHeight; ty += 2) {
            for (int tx = 0; tx < templateWidth; tx += 2) {
                int sx = centerX - halfW + tx;
                int sy = centerY - halfH + ty;

                uint32_t tmplColor = templatePixels[ty * templateWidth + tx];
                uint32_t screenColor = pixels[sy * width + sx];

                if (isSimilarColor(tmplColor, screenColor)) {
                    matchCount++;
                }
                totalCount++;
            }
        }

        return totalCount > 0 ? (float)matchCount / (float)totalCount : 0.0f;
    }

    bool isSimilarColor(uint32_t c1, uint32_t c2) {
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;

        int diff = abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2);
        return diff < 60;
    }

    Point2f findAimLineEnd(const uint32_t* pixels, int width, int height, const Circle& circle) {
        Point2f avgPoint;
        int count = 0;

        int searchRadius = 150;
        int minX = (int)circle.center.x - searchRadius;
        int maxX = (int)circle.center.x + searchRadius;
        int minY = (int)circle.center.y - searchRadius;
        int maxY = (int)circle.center.y + searchRadius;

        minX = std::max(0, minX);
        maxX = std::min(width - 1, maxX);
        minY = std::max(0, minY);
        maxY = std::min(height - 1, maxY);

        std::vector<Point2f> whitePixels;

        for (int y = minY; y < maxY; y += 2) {
            for (int x = minX; x < maxX; x += 2) {
                uint32_t color = pixels[y * width + x];
                if (isWhitePixel(color)) {
                    float dist = std::sqrt(
                        (x - circle.center.x) * (x - circle.center.x) +
                        (y - circle.center.y) * (y - circle.center.y)
                    );
                    if (dist > circle.radius * 1.5f && dist < searchRadius) {
                        whitePixels.push_back(Point2f((float)x, (float)y));
                    }
                }
            }
        }

        if (whitePixels.empty()) {
            return avgPoint;
        }

        std::sort(whitePixels.begin(), whitePixels.end(),
            [&circle](const Point2f& a, const Point2f& b) {
                float da = (a.x - circle.center.x) * (a.x - circle.center.x) +
                          (a.y - circle.center.y) * (a.y - circle.center.y);
                float db = (b.x - circle.center.x) * (b.x - circle.center.x) +
                          (b.y - circle.center.y) * (b.y - circle.center.y);
                return da > db;
            });

        float sumX = 0, sumY = 0;
        int topN = std::min(50, (int)whitePixels.size());
        for (int i = 0; i < topN; i++) {
            sumX += whitePixels[i].x;
            sumY += whitePixels[i].y;
            count++;
        }

        if (count > 0) {
            avgPoint.x = sumX / count;
            avgPoint.y = sumY / count;
        }

        return avgPoint;
    }

    bool isWhitePixel(uint32_t color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return r > 200 && g > 200 && b > 200 && (r - g) < 30 && (g - b) < 30;
    }
};

static BilliardsEngine* g_engine = nullptr;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_billiards_sdk_BilliardsSDK_nativeInit(JNIEnv* env, jobject thiz) {
    if (g_engine == nullptr) {
        g_engine = new BilliardsEngine();
        LOGD("BilliardsEngine initialized");
    }
    return (jlong)g_engine;
}

JNIEXPORT void JNICALL
Java_com_billiards_sdk_BilliardsSDK_nativeRelease(JNIEnv* env, jobject thiz, jlong handle) {
    if (handle != 0) {
        delete (BilliardsEngine*)handle;
    }
    g_engine = nullptr;
}

JNIEXPORT void JNICALL
Java_com_billiards_sdk_BilliardsSDK_nativeSetTemplate(JNIEnv* env, jobject thiz,
        jlong handle, jintArray pixels, jint width, jint height) {
    if (handle == 0) return;
    BilliardsEngine* engine = (BilliardsEngine*)handle;

    jint* pixelData = env->GetIntArrayElements(pixels, nullptr);
    if (pixelData) {
        engine->setTemplate((const uint32_t*)pixelData, width, height);
        env->ReleaseIntArrayElements(pixels, pixelData, 0);
    }
}

JNIEXPORT jobject JNICALL
Java_com_billiards_sdk_BilliardsSDK_nativeProcessFrame(JNIEnv* env, jobject thiz,
        jlong handle, jintArray pixels, jint width, jint height) {
    ProcessingResult result;

    if (handle != 0) {
        jint* pixelData = env->GetIntArrayElements(pixels, nullptr);
        if (pixelData) {
            BilliardsEngine* engine = (BilliardsEngine*)handle;
            result = engine->processFrame((const uint32_t*)pixelData, width, height);
            env->ReleaseIntArrayElements(pixels, pixelData, 0);
        }
    }

    jclass clazz = env->FindClass("com/billiards/sdk/BilliardsSDK$ProcessingResult");
    jmethodID constructor = env->GetMethodID(clazz, "<init>", "()V");
    jobject resultObj = env->NewObject(clazz, constructor);

    if (result.templateCenter.x > 0 && result.templateCenter.y > 0) {
        jclass pointFClass = env->FindClass("android/graphics/PointF");
        jmethodID pointFConstructor = env->GetMethodID(pointFClass, "<init>", "(FF)V");

        if (result.templateCenter.x > 0 && result.templateCenter.y > 0) {
            jobject centerPoint = env->NewObject(pointFClass, pointFConstructor,
                result.templateCenter.x, result.templateCenter.y);
            env->SetObjectField(resultObj, env->GetFieldID(clazz, "templateCenter", "Landroid/graphics/PointF;"), centerPoint);
        }

        if (result.aimPoint.x > 0 && result.aimPoint.y > 0) {
            jobject aimPointObj = env->NewObject(pointFClass, pointFConstructor,
                result.aimPoint.x, result.aimPoint.y);
            env->SetObjectField(resultObj, env->GetFieldID(clazz, "aimPoint", "Landroid/graphics/PointF;"), aimPointObj);
        }
    }

    jfieldID confidenceField = env->GetFieldID(clazz, "confidence", "F");
    env->SetFloatField(resultObj, confidenceField, result.confidence);

    return resultObj;
}

}
