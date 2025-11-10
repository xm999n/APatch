#include <jni.h>
#include <string>
#include <unistd.h>
#include <sys/ptrace.h>
#include <pthread.h>
#include <android/log.h>
#include <sys/stat.h>
#include <dirent.h>
#include <dlfcn.h>
#include <cstring>
#include <cstdlib>
#include <fcntl.h>

#define L1 "APatchSecurity"
#define L2(...) __android_log_print(ANDROID_LOG_ERROR, L1, __VA_ARGS__)
#define L3(...) __android_log_print(ANDROID_LOG_DEBUG, L1, __VA_ARGS__)

// 混淆的全局变量
static volatile bool g_x7f = false;
static volatile bool g_x8e = false;
static pthread_mutex_t g_m9a = PTHREAD_MUTEX_INITIALIZER;

// 混淆的常量
static const char* H1 = "5c8a8c7e4b3f2a1d9e6c5b4a8d7c6e5f4d3c2b1a0f9e8d7c6b5a4e3d2c1b0a9f";

// ==================== 干扰函数 1 ====================
static int x1_dummy(int a, int b) {
    volatile int c = a ^ b;
    for (int i = 0; i < 10; i++) {
        c = (c << 1) | (c >> 31);
    }
    return c & 0xFF;
}

// ==================== 签名片段获取 (乱序) ====================
static const char* x3_get_part(int idx) {
    // 打乱顺序的映射
    switch(idx) {
        case 7: return "jXkv";  // 原 index 7
        case 2: return "MUGb";  // 原 index 2
        case 9: return "PLxg";  // 原 index 9
        case 0: return "REH3";  // 原 index 0
        case 4: return "H8F1";  // 原 index 4
        case 10: return "B+Y="; // 原 index 10
        case 3: return "rtup";  // 原 index 3
        case 8: return "MTga";  // 原 index 8
        case 1: return "eiyy";  // 原 index 1
        case 5: return "0GNb";  // 原 index 5
        case 6: return "TUhc";  // 原 index 6
        default: {
            // 干扰代码
            volatile int dummy = idx * 0x1234;
            return "";
        }
    }
}

// ==================== Frida 文件检测 (提前) ====================
static bool x4_check_files() {
    const char* p[] = {
        "/data/local/tmp/frida-server",
        "/data/local/tmp/frida-agent",
        "/data/local/tmp/re.frida.server",
        "/sdcard/frida-server",
        "/system/bin/frida-server",
        "/system/xbin/frida-server",
        "/data/local/tmp/gum-js-loop",
        "/data/local/tmp/frida-gadget",
        "/data/local/tmp/frida"
    };
    
    for (int i = 0; i < 9; i++) {
        struct stat s;
        if (stat(p[i], &s) == 0) {
            L2("File %d", i);
            return false;
        }
        // 干扰
        x1_dummy(i, s.st_mode);
    }
    
    return true;
}

// ==================== 干扰函数 2 ====================
static void x2_noise() {
    volatile unsigned long long n = 0xDEADBEEFCAFEBABE;
    for (int i = 0; i < 5; i++) {
        n = (n << 13) ^ (n >> 7);
    }
}

// ==================== Xposed 检测 (后置) ====================
static bool x8_check_xposed() {
    struct stat s1, s2;
    
    if (stat("/system/framework/XposedBridge.jar", &s1) == 0) {
        L2("XB detected");
        return false;
    }
    
    x2_noise(); // 干扰
    
    if (stat("/system/framework/edxp.jar", &s2) == 0) {
        L2("EX detected");
        return false;
    }
    
    // 额外检查
    if (stat("/system/lib/libxposed_art.so", &s1) == 0) {
        return false;
    }
    
    return true;
}

// ==================== 签名组装 ====================
static std::string x9_build_sig() {
    std::string r;
    r.reserve(64);
    
    // 正确的顺序: 0,1,2,3,4,5,6,7,8,9,10
    int order[] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    
    for (int i = 0; i < 11; i++) {
        r += x3_get_part(order[i]);
        if (i % 3 == 0) x2_noise(); // 干扰
    }
    
    return r;
}

// ==================== 内存映射检测 ====================
static bool x5_check_maps() {
    FILE* f = fopen("/proc/self/maps", "r");
    if (!f) return true;
    
    char buf[512];
    bool ok = true;
    
    const char* bad[] = {"frida", "gum-js", "gum_js", "frida-agent", "linjector"};
    
    while (fgets(buf, sizeof(buf), f)) {
        for (int i = 0; i < 5; i++) {
            if (strcasestr(buf, bad[i])) {
                L2("Map pattern %d", i);
                ok = false;
                break;
            }
        }
        if (!ok) break;
        
        // 干扰
        if (strlen(buf) > 100) x1_dummy(buf[0], buf[10]);
    }
    
    fclose(f);
    return ok;
}

// ==================== TracerPid 检测 (中间插入) ====================
static bool x6_check_tracer() {
    FILE* f = fopen("/proc/self/status", "r");
    if (!f) return true;
    
    char ln[256];
    bool r = true;
    
    while (fgets(ln, sizeof(ln), f)) {
        if (strncmp(ln, "TracerPid:", 10) == 0) {
            int p;
            if (sscanf(ln, "TracerPid: %d", &p) == 1) {
                if (p != 0) {
                    L2("Tracer: %d", p);
                    r = false;
                    break;
                }
            }
        }
        // 干扰
        x2_noise();
    }
    
    fclose(f);
    return r;
}

// ==================== 干扰函数 3 ====================
static bool x10_fake_check() {
    volatile int fake = rand() % 100;
    if (fake < 50) {
        return true;
    }
    x2_noise();
    return fake % 2 == 0;
}

// ==================== Frida 端口检测 ====================
static bool x7_check_ports() {
    const char* nets[] = {"/proc/net/tcp", "/proc/net/tcp6"};
    const char* fps[] = {":6992", ":6993", ":D6F2", ":D6F3"};
    
    for (int n = 0; n < 2; n++) {
        FILE* f = fopen(nets[n], "r");
        if (!f) continue;
        
        char ln[1024];
        while (fgets(ln, sizeof(ln), f)) {
            for (int p = 0; p < 4; p++) {
                if (strstr(ln, fps[p])) {
                    L2("Port %s in %d", fps[p], n);
                    fclose(f);
                    return false;
                }
            }
            // 干扰
            if (strlen(ln) > 50) x1_dummy(n, p);
        }
        
        fclose(f);
    }
    
    x10_fake_check(); // 干扰
    return true;
}

// ==================== ptrace 检测 (后置) ====================
static bool x11_check_ptrace() {
    long r = ptrace(PTRACE_TRACEME, 0, 0, 0);
    if (r == -1) {
        L2("ptrace failed");
        return false;
    }
    
    x2_noise(); // 干扰
    ptrace(PTRACE_DETACH, 0, 0, 0);
    
    return true;
}

// ==================== 综合检测 (乱序调用) ====================
static bool x12_anti_all() {
    // 乱序检测
    if (!x6_check_tracer()) return false;  // TracerPid
    x2_noise();
    
    if (!x4_check_files()) return false;   // Frida 文件
    
    if (!x11_check_ptrace()) return false; // ptrace
    x10_fake_check();
    
    if (!x7_check_ports()) return false;   // Frida 端口
    
    if (!x5_check_maps()) return false;    // 内存映射
    x2_noise();
    
    if (!x8_check_xposed()) return false;  // Xposed
    
    return true;
}

// ==================== 守护线程函数 (提前) ====================
static void* x13_guard_func(void* arg) {
    L3("Guard start");
    
    while (true) {
        unsigned int d = 25 + (rand() % 10);
        sleep(d);
        
        // 干扰
        x10_fake_check();
        
        pthread_mutex_lock(&g_m9a);
        bool v = g_x7f;
        pthread_mutex_unlock(&g_m9a);
        
        if (!v || !x12_anti_all()) {
            L2("Guard fail");
            x2_noise();
            _exit(0);
        }
        
        L3("Guard pass");
    }
    
    return nullptr;
}

// ==================== JNI: 反调试 ====================
extern "C"
JNIEXPORT jboolean JNICALL
Java_me_bmax_apatch_APApplication_nativeAntiDebug(JNIEnv *env, jobject thiz) {
    x2_noise(); // 干扰
    bool r = x12_anti_all();
    x10_fake_check();
    return r ? JNI_TRUE : JNI_FALSE;
}

// ==================== JNI: 获取签名 Hash ====================
extern "C"
JNIEXPORT jstring JNICALL
Java_me_bmax_apatch_APApplication_getExpectedSignatureHash(JNIEnv *env, jobject thiz) {
    x2_noise();
    return env->NewStringUTF(H1);
}

// ==================== 干扰函数 4 ====================
static bool x14_validate_context(JNIEnv *e, jobject o) {
    if (e == nullptr || o == nullptr) return false;
    
    x2_noise();
    
    jclass c = e->GetObjectClass(o);
    if (c == nullptr) return false;
    
    x10_fake_check();
    return true;
}

// ==================== JNI: 启动守护 (后置) ====================
extern "C"
JNIEXPORT void JNICALL
Java_me_bmax_apatch_APApplication_startNativeGuard(JNIEnv *env, jobject thiz) {
    
    pthread_mutex_lock(&g_m9a);
    if (g_x8e) {
        pthread_mutex_unlock(&g_m9a);
        L3("Guard exists");
        return;
    }
    g_x8e = true;
    pthread_mutex_unlock(&g_m9a);
    
    x2_noise(); // 干扰
    
    pthread_t t;
    pthread_attr_t a;
    
    pthread_attr_init(&a);
    pthread_attr_setdetachstate(&a, PTHREAD_CREATE_DETACHED);
    
    if (pthread_create(&t, &a, x13_guard_func, nullptr) != 0) {
        L2("Guard create fail");
        pthread_mutex_lock(&g_m9a);
        g_x8e = false;
        pthread_mutex_unlock(&g_m9a);
    } else {
        L3("Guard created");
    }
    
    pthread_attr_destroy(&a);
    x10_fake_check();
}

// ==================== JNI: 主验证 (中间位置) ====================
extern "C"
JNIEXPORT jboolean JNICALL
Java_me_bmax_apatch_APApplication_nativeVerify(JNIEnv *env, jobject thiz) {
    
    // 干扰
    x2_noise();
    
    // 验证上下文
    if (!x14_validate_context(env, thiz)) {
        L2("Context invalid");
        return JNI_FALSE;
    }
    
    // 反调试
    if (!x12_anti_all()) {
        L2("Anti-debug fail");
        return JNI_FALSE;
    }
    
    x10_fake_check(); // 干扰
    
    // 获取 Java 方法
    jclass cls = env->GetObjectClass(thiz);
    jmethodID mid = env->GetMethodID(cls, "d1", "()Ljava/lang/String;");
    if (mid == nullptr) {
        L2("Method not found");
        return JNI_FALSE;
    }
    
    // 调用 Java 方法
    jstring jsig = (jstring)env->CallObjectMethod(thiz, mid);
    if (jsig == nullptr) {
        L2("Call failed");
        return JNI_FALSE;
    }
    
    x2_noise(); // 干扰
    
    const char *cstr = env->GetStringUTFChars(jsig, nullptr);
    if (cstr == nullptr) {
        L2("String null");
        return JNI_FALSE;
    }
    
    // 获取预期签名
    std::string exp = x9_build_sig();
    
    // 比较
    bool valid = (strcmp(cstr, exp.c_str()) == 0);
    
    if (!valid) {
        L2("Sig mismatch");
        // 不输出实际值,防止泄露
    }
    
    env->ReleaseStringUTFChars(jsig, cstr);
    
    x10_fake_check(); // 干扰
    
    // 更新状态
    pthread_mutex_lock(&g_m9a);
    g_x7f = valid;
    pthread_mutex_unlock(&g_m9a);
    
    return valid ? JNI_TRUE : JNI_FALSE;
}

// ==================== JNI_OnLoad (最后) ====================
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    L3("Library loaded");
    
    // 干扰
    x2_noise();
    x10_fake_check();
    
    // 初始检测
    if (!x12_anti_all()) {
        L2("OnLoad check fail");
        x2_noise();
        _exit(0);
    }
    
    return JNI_VERSION_1_6;
}