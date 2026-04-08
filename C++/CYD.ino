//CYD.ino
#include <WiFi.h>
#include <WebSocketsClient.h>
#include <esp_now.h>

#include <Wire.h>
#include <lvgl.h>
#include <TFT_eSPI.h>
#include <XPT2046_Touchscreen.h>
#include <DHT.h>
#include <ArduinoJson.h>

//Wi‑Fi / WebSocket config 
const char* WIFI_SSID     = "SSID";
const char* WIFI_PASSWORD = "pass";

const char* WS_HOST = "IPv4";
const uint16_t WS_PORT = 8080;
const char* WS_PATH = "/";

WebSocketsClient webSocket;
volatile bool wsConnected = false;

//DHT11 
#define DHTPIN 27
#define DHTTYPE DHT11
DHT dht(DHTPIN, DHTTYPE);

float lastTemp = NAN;
float lastHum  = NAN;

//CYD Touch pins 
#define XPT2046_IRQ  36
#define XPT2046_MOSI 32
#define XPT2046_MISO 39
#define XPT2046_CLK  25
#define XPT2046_CS   33

SPIClass touchscreenSPI = SPIClass(VSPI);
XPT2046_Touchscreen touchscreen(XPT2046_CS, XPT2046_IRQ);

//Display/LVGL 
#define SCREEN_WIDTH  240
#define SCREEN_HEIGHT 320

int x, y, z;

#define DRAW_BUF_SIZE (SCREEN_WIDTH * SCREEN_HEIGHT / 10 * (LV_COLOR_DEPTH / 8))
uint32_t draw_buf[DRAW_BUF_SIZE / 4];

lv_obj_t* temp_label;
lv_obj_t* hum_label;
lv_obj_t* mess_label;

//ESP‑NOW config 
//Center Node ESP32 STATIC MAC
uint8_t CENTER_MAC[] = {0x55, 0x55, 0x55, 0x55, 0x55, 0x55}; //Replace with real center node MAC

enum PacketType : uint8_t {
  PKT_SENSOR    = 1,
  PKT_MOTOR     = 2,
  PKT_ACK       = 3,
  PKT_MODE      = 4,
  PKT_THRESHOLD = 5
};

enum ControlMode : uint8_t {
  MODE_AUTO   = 0,
  MODE_MANUAL = 1
};

typedef struct __attribute__((packed)) {
  uint8_t type;
  float temperatureC;
  float humidity;
  uint32_t seq;
} sensor_packet_t;

typedef struct __attribute__((packed)) {
  uint8_t type;
  uint32_t seq;
  int8_t dir;
  uint8_t speed;
} motor_packet_t;

typedef struct __attribute__((packed)) {
  uint8_t type;
  uint32_t seq;
  uint8_t mode;
} mode_packet_t;

typedef struct __attribute__((packed)) {
  uint8_t type;
  uint32_t seq;
  float tempThresholdC;
} threshold_packet_t;

typedef struct __attribute__((packed)) {
  uint8_t type;
  uint32_t seq;
} ack_packet_t;

volatile uint32_t seqCounter = 0;

//ACK/retry tracking
static bool hasPending = false;
static uint32_t pendingSeq = 0;
static unsigned long pendingSentAt = 0;
static uint8_t pendingRetries = 0;

static volatile bool ackReceivedForCurrent = false;
static volatile uint32_t lastAckSeq = 0;

static const uint8_t MAX_RETRIES = 6;
static const uint32_t ACK_TIMEOUT_MS = 250;

static uint8_t pendingBuf[64];
static size_t pendingLen = 0;

//helpers 
static void setCydMessage(const char* text) {
  if (!mess_label) return;
  lv_label_set_text(mess_label, text);
}

static void wsSendJson(const char* json) {
  if (!wsConnected) return;
  webSocket.sendTXT(json);
  Serial.print("[WS] sent: ");
  Serial.println(json);
}

//Wi‑Fi 
bool initWiFi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  Serial.print("Connecting WiFi");
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 30) {
    delay(500);
    Serial.print(".");
    attempts++;
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("WiFi OK, IP: %s, RSSI: %d dBm, Ch: %d\n",
                  WiFi.localIP().toString().c_str(),
                  WiFi.RSSI(),
                  WiFi.channel());
    return true;
  }

  Serial.println("WiFi FAILED");
  return false;
}

//WebSocket 
void webSocketEvent(WStype_t type, uint8_t* payload, size_t length);

void initWebSocket() {
  webSocket.begin(WS_HOST, WS_PORT, WS_PATH);
  webSocket.setReconnectInterval(15000);
  webSocket.onEvent(webSocketEvent);
}

//ESP‑NOW 
void onDataSent(const uint8_t* mac_addr, esp_now_send_status_t status) {
  Serial.print("ESP-NOW send status: ");
  Serial.println(status == ESP_NOW_SEND_SUCCESS ? "SUCCESS" : "FAIL");
}

void onDataRecv(const esp_now_recv_info_t* recv_info, const uint8_t* incomingData, int len) {
  if (len != (int)sizeof(ack_packet_t)) return;

  ack_packet_t ack;
  memcpy(&ack, incomingData, sizeof(ack));
  if (ack.type != PKT_ACK) return;

  lastAckSeq = ack.seq;
  if (hasPending && ack.seq == pendingSeq) {
    ackReceivedForCurrent = true;
  }
}

bool initEspNowLockedToWifiChannel() {
  WiFi.mode(WIFI_STA);

  if (esp_now_init() != ESP_OK) {
    Serial.println("ESP-NOW init failed");
    return false;
  }

  esp_now_register_send_cb((esp_now_send_cb_t)onDataSent);
  esp_now_register_recv_cb(onDataRecv);

  uint8_t ch = WiFi.channel();
  Serial.print("Locking ESP-NOW peer channel to: ");
  Serial.println(ch);

  esp_now_peer_info_t peerInfo = {};
  memcpy(peerInfo.peer_addr, CENTER_MAC, 6);
  peerInfo.channel = ch;
  peerInfo.encrypt = false;

  if (esp_now_add_peer(&peerInfo) != ESP_OK) {
    Serial.println("Failed to add center peer");
    return false;
  }
  return true;
}

void queueRawPacket(const void* pkt, size_t len, uint32_t seq) {
  if (len > sizeof(pendingBuf)) return;

  memcpy(pendingBuf, pkt, len);
  pendingLen = len;

  pendingSeq = seq;
  hasPending = true;

  pendingRetries = 0;
  ackReceivedForCurrent = false;
  pendingSentAt = 0;
}

void queueMotorCommand(int8_t dir, uint8_t speed) {
  motor_packet_t pkt;
  pkt.type = PKT_MOTOR;
  pkt.seq = ++seqCounter;
  pkt.dir = dir;
  pkt.speed = speed;
  queueRawPacket(&pkt, sizeof(pkt), pkt.seq);
}

void queueMode(uint8_t mode) {
  mode_packet_t pkt;
  pkt.type = PKT_MODE;
  pkt.seq = ++seqCounter;
  pkt.mode = mode;
  queueRawPacket(&pkt, sizeof(pkt), pkt.seq);
}

void queueThreshold(float thresholdC) {
  threshold_packet_t pkt;
  pkt.type = PKT_THRESHOLD;
  pkt.seq = ++seqCounter;
  pkt.tempThresholdC = thresholdC;
  queueRawPacket(&pkt, sizeof(pkt), pkt.seq);
}

void trySendPendingIfNeeded() {
  if (!hasPending) return;

  unsigned long now = millis();

  if (ackReceivedForCurrent) {
    char msg[64];
    snprintf(msg, sizeof(msg), "ESP-NOW ACK OK (seq %lu)", (unsigned long)pendingSeq);
    setCydMessage(msg);
    hasPending = false;
    return;
  }

  bool shouldSend = (pendingSentAt == 0) || (now - pendingSentAt >= ACK_TIMEOUT_MS);
  if (!shouldSend) return;

  if (pendingRetries >= MAX_RETRIES) {
    setCydMessage("ESP-NOW ACK TIMEOUT");
    hasPending = false;
    return;
  }

  pendingRetries++;
  pendingSentAt = now;

  esp_err_t res = esp_now_send(CENTER_MAC, pendingBuf, pendingLen);
  Serial.printf("[ESP-NOW] send seq %lu try %u res=%d len=%u\n",
                (unsigned long)pendingSeq, (unsigned)pendingRetries, (int)res, (unsigned)pendingLen);
}

//LVGL/touch 
void log_print(lv_log_level_t level, const char* buf) {
  LV_UNUSED(level);
  Serial.println(buf);
  Serial.flush();
}

void touchscreen_read(lv_indev_t* indev, lv_indev_data_t* data) {
  if (touchscreen.tirqTouched() && touchscreen.touched()) {
    TS_Point p = touchscreen.getPoint();
    x = map(p.x, 200, 3700, 1, SCREEN_WIDTH);
    y = map(p.y, 240, 3800, 1, SCREEN_HEIGHT);
    z = p.z;
    data->state = LV_INDEV_STATE_PRESSED;
    data->point.x = x;
    data->point.y = y;
  } else {
    data->state = LV_INDEV_STATE_RELEASED;
  }
}

void lv_create_main_gui() {
  lv_obj_t* title = lv_label_create(lv_scr_act());
  lv_label_set_text(title, "Smart Sense");
  lv_obj_set_width(title, 150);
  lv_obj_set_style_text_align(title, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_align(title, LV_ALIGN_CENTER, 0, -90);

  mess_label = lv_label_create(lv_scr_act());
  lv_label_set_text(mess_label, "Ready");
  lv_obj_align(mess_label, LV_ALIGN_CENTER, 0, -35);

  temp_label = lv_label_create(lv_scr_act());
  lv_label_set_text(temp_label, "Temperature: -- C");
  lv_obj_align(temp_label, LV_ALIGN_CENTER, 0, 20);

  hum_label = lv_label_create(lv_scr_act());
  lv_label_set_text(hum_label, "Humidity: -- %");
  lv_obj_align(hum_label, LV_ALIGN_CENTER, 0, 50);
}

//WS command handling 
static void handleWsCommandText(const char* msg, size_t len) {
  JsonDocument doc;
  DeserializationError err = deserializeJson(doc, msg, len);
  if (err) {
    Serial.print("[WS] JSON parse error: ");
    Serial.println(err.c_str());
    return;
  }

  const char* cmd = doc["cmd"] | "";

  if (strcmp(cmd, "cyd_text") == 0) {
    const char* text = doc["text"] | "";
    setCydMessage(text);
    return;
  }

  if (strcmp(cmd, "motor") == 0) {
    const char* dirStr = doc["dir"] | "STOP";
    int speed = doc["speed"] | 0;

    int8_t dir = 0;
    if (strcmp(dirStr, "FWD") == 0) dir = 1;
    else if (strcmp(dirStr, "REV") == 0) dir = -1;

    if (speed < 0) speed = 0;
    if (speed > 255) speed = 255;

    queueMotorCommand(dir, (uint8_t)speed);

    char status[64];
    snprintf(status, sizeof(status), "MANUAL: %s @ %d", dirStr, speed);
    setCydMessage(status);
    return;
  }

  if (strcmp(cmd, "motor_mode") == 0) {
    const char* modeStr = doc["mode"] | "AUTO";
    uint8_t m = (strcmp(modeStr, "MANUAL") == 0) ? MODE_MANUAL : MODE_AUTO;

    queueMode(m);

    char status[64];
    snprintf(status, sizeof(status), "Mode request: %s", (m == MODE_MANUAL ? "MANUAL" : "AUTO"));
    setCydMessage(status);
    return;
  }

  if (strcmp(cmd, "auto_threshold") == 0) {
    float thr = doc["threshold"] | 20.0;
    queueThreshold(thr);

    char status[64];
    snprintf(status, sizeof(status), "Threshold set: %.1f C", thr);
    setCydMessage(status);
    return;
  }

  Serial.print("[CMD] Unknown cmd: ");
  Serial.println(cmd);
}

void webSocketEvent(WStype_t type, uint8_t* payload, size_t length) {
  switch (type) {
    case WStype_CONNECTED:
      Serial.printf("[WS] Connected: %s\n", payload ? (char*)payload : "(null)");
      wsConnected = true;
      break;

    case WStype_DISCONNECTED:
      Serial.println("[WS] Disconnected");
      wsConnected = false;
      break;

    case WStype_TEXT:
      Serial.print("[WS] RX: ");
      if (payload && length) Serial.write(payload, length);
      Serial.println();
      if (payload && length) handleWsCommandText((const char*)payload, length);
      break;

    case WStype_ERROR:
      Serial.println("[WS] Error");
      wsConnected = false;
      break;

    default:
      break;
  }
}

//Timing 
static unsigned long t_dbg  = 0;
static unsigned long t_send = 0;
static unsigned long t_lv   = 0;

void sendSensorToCenter(float temperature, float humidity) {
  sensor_packet_t pkt;
  pkt.type = PKT_SENSOR;
  pkt.temperatureC = temperature;
  pkt.humidity = humidity;
  pkt.seq = ++seqCounter;

  esp_err_t res = esp_now_send(CENTER_MAC, (uint8_t*)&pkt, sizeof(pkt));
  Serial.printf("[ESP-NOW] SENSOR seq %lu temp=%.2f hum=%.2f res=%d\n",
                (unsigned long)pkt.seq, temperature, humidity, (int)res);
}

void setup() {
  Serial.begin(115200);
  Serial.printf("Connecting WS to ws://%s:%u%s\n", WS_HOST, WS_PORT, WS_PATH);

  dht.begin();
  analogReadResolution(12);

  lv_init();
  lv_log_register_print_cb(log_print);

  touchscreenSPI.begin(XPT2046_CLK, XPT2046_MISO, XPT2046_MOSI, XPT2046_CS);
  touchscreen.begin(touchscreenSPI);
  touchscreen.setRotation(2);

  lv_display_t* disp = lv_tft_espi_create(SCREEN_WIDTH, SCREEN_HEIGHT, draw_buf, sizeof(draw_buf));
  lv_display_set_rotation(disp, LV_DISPLAY_ROTATION_270);

  lv_indev_t* indev = lv_indev_create();
  lv_indev_set_type(indev, LV_INDEV_TYPE_POINTER);
  lv_indev_set_read_cb(indev, touchscreen_read);

  lv_create_main_gui();

  if (initWiFi()) {
    initWebSocket();
    setCydMessage("WiFi OK, WS init...");
  } else {
    setCydMessage("WiFi failed");
  }

  if (!initEspNowLockedToWifiChannel()) {
    setCydMessage("ESP-NOW init failed");
  } else {
    Serial.println("ESP-NOW ready");
  }

  unsigned long now = millis();
  t_dbg = t_send = t_lv = now;
}

void loop() {
  unsigned long now = millis();

  webSocket.loop();

  if (now - t_lv >= 5) {
    uint32_t dt = now - t_lv;
    t_lv = now;
    lv_tick_inc(dt);
    lv_task_handler();
  }

  if (now - t_dbg >= 1000) {
    t_dbg = now;
    Serial.printf("[DBG] ws=%d wifi=%d pending=%d acked=%d lastAck=%lu\n",
                  (int)wsConnected, (int)WiFi.status(),
                  (int)hasPending, (int)ackReceivedForCurrent,
                  (unsigned long)lastAckSeq);
  }

  if (now - t_send >= 5000) {
    t_send = now;

    float temperature = dht.readTemperature();
    float humidity = dht.readHumidity();

    Serial.print("Temperature: "); Serial.println(temperature);
    Serial.print("Humidity: "); Serial.println(humidity);

    if (!isnan(temperature) && !isnan(humidity)) {
      lastTemp = temperature;
      lastHum = humidity;

      char temp_text[40];
      snprintf(temp_text, sizeof(temp_text), "Temperature: %.2f C", temperature);
      lv_label_set_text(temp_label, temp_text);

      char hum_text[40];
      snprintf(hum_text, sizeof(hum_text), "Humidity: %.2f %%", humidity);
      lv_label_set_text(hum_label, hum_text);

      if (wsConnected) {
        static uint32_t sensorSeq = 0;
        sensorSeq++;

        char json[160];
        snprintf(json, sizeof(json),
                 "{\"event\":\"sensor\",\"temp\":%.2f,\"hum\":%.2f,\"seq\":%lu}",
                 temperature, humidity, (unsigned long)sensorSeq);
        wsSendJson(json);
      }

      sendSensorToCenter(temperature, humidity);
    } else {
      Serial.println("Failed to read from DHT sensor!");
    }
  }

  trySendPendingIfNeeded();
}
